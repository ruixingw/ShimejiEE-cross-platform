
#include "NativeEnvironment.h"
#include "Renderer.h"
#include <AppKit/AppKit.h>
#include <ApplicationServices/ApplicationServices.h>
#include <string.h>
#include <unistd.h>

// forward declarations (the ax orientation detection below needs them)
static AXUIElementRef copyAXFocusedWindowOf(AXUIElementRef target);
static CGPoint getAXPositionOf(AXUIElementRef target);
static CGSize getAXSizeOf(AXUIElementRef target);

void init_native_environment() {

}

void update_native_environment() {

}

//---

// accessibility window positions historically use a bottom left origin while
// java/awt and coregraphics use a top left origin, but newer macOS versions
// report top left directly; the orientation is detected once at runtime
static double mainScreenHeight() {
    return CGDisplayBounds(CGMainDisplayID()).size.height;
}

static int axOrientationState = 0; // 0 unknown, 1 top left (same as cg), 2 bottom left (classic)

static int axOrientation() {
    if (axOrientationState == 0) {
        axOrientationState = 2; // classic default when detection is impossible

        NSRunningApplication* app = NSWorkspace.sharedWorkspace.frontmostApplication;
        if (app != nil) {
            AXUIElementRef appRef = AXUIElementCreateApplication(app.processIdentifier);
            AXUIElementRef win = appRef != NULL ? copyAXFocusedWindowOf(appRef) : NULL;
            if (win != NULL) {
                CGPoint pos = getAXPositionOf(win);
                CGSize size = getAXSizeOf(win);
                CFRelease(win);
                CFRelease(appRef);

                // find the cg frame of the same window (same pid, size and x)
                CFArrayRef list = CGWindowListCopyWindowInfo(kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements, kCGNullWindowID);
                if (list != NULL) {
                    for (NSDictionary* info in (__bridge NSArray*)list) {
                        if ([info[(id)kCGWindowOwnerPID] intValue] != (int)app.processIdentifier) {
                            continue;
                        }
                        CGRect b = CGRectNull;
                        if (!CGRectMakeWithDictionaryRepresentation((CFDictionaryRef)info[(id)kCGWindowBounds], &b)) {
                            continue;
                        }
                        if (fabs(b.size.width - size.width) > 2 || fabs(b.size.height - size.height) > 2
                            || fabs(b.origin.x - pos.x) > 2) {
                            continue;
                        }
                        // same window; matching y means ax already uses the cg origin
                        axOrientationState = fabs(b.origin.y - pos.y) <= 4 ? 1 : 2;
                        break;
                    }
                    CFRelease(list);
                }
            } else if (appRef != NULL) {
                CFRelease(appRef);
            }
        }
    }
    return axOrientationState;
}

static int axYToJavaY(double axY, double height) {
    if (axOrientation() == 1) {
        return (int)round(axY);
    }
    return (int)round(mainScreenHeight() - axY - height);
}

static double javaYToAxY(double javaY, double height) {
    if (axOrientation() == 1) {
        return javaY;
    }
    return mainScreenHeight() - javaY - height;
}

static AXUIElementRef copyAXFocusedWindowOf(AXUIElementRef target) {
    AXUIElementRef ret;
    AXError result =  AXUIElementCopyAttributeValue(target, kAXFocusedWindowAttribute, (CFTypeRef*)&ret);
    return ret;
}

static CGSize getAXSizeOf(AXUIElementRef target) {
    AXValueRef retRef;
    AXError result = AXUIElementCopyAttributeValue(target, kAXSizeAttribute, (CFTypeRef*)&retRef);

    CGSize ret = CGSizeZero;
    if (result == kAXErrorSuccess) {
        AXValueGetValue(retRef, kAXValueCGSizeType, &ret);
        CFRelease(retRef);
    }

    return ret;
}

static CGPoint getAXPositionOf(AXUIElementRef target) {
    AXValueRef retRef;
    AXError result = AXUIElementCopyAttributeValue(target, kAXPositionAttribute, (CFTypeRef*)&retRef);

    CGPoint ret = CGPointZero;
    if (result == kAXErrorSuccess) {
        AXValueGetValue(retRef, kAXValueCGPointType, &ret);
        CFRelease(retRef);
    }

    return ret;
}

static void setAXPositionOf(AXUIElementRef target, CGPoint val) {
    AXValueRef value =  AXValueCreate(kAXValueCGPointType, &val);
    AXUIElementSetAttributeValue(target, kAXPositionAttribute, value);
    CFRelease(value);
}

//---

NSRunningApplication* currentFrontmostApp = nil;

// title of the window returned by the last get_active_ie_bounds call,
// so java can filter/read it without another accessibility roundtrip
static char lastIeTitle[512];
static bool lastIeTitleValid = false;

static void cacheIeTitleOf(AXUIElementRef window) {
    lastIeTitleValid = false;
    lastIeTitle[0] = '\0';

    CFTypeRef titleRef = NULL;
    if (AXUIElementCopyAttributeValue(window, kAXTitleAttribute, &titleRef) == kAXErrorSuccess && titleRef != NULL) {
        if ([(id)titleRef isKindOfClass:[NSString class]]) {
            const char* utf8 = [(NSString*)titleRef UTF8String];
            if (utf8 != NULL) {
                strlcpy(lastIeTitle, utf8, sizeof(lastIeTitle));
                lastIeTitleValid = true;
            }
        }
        CFRelease(titleRef);
    }
}

// windows that were moved by mascots and can be restored with restore_ie
// (non arc: the array owns one reference of each element it holds)
static NSMutableArray* touchedWindows = nil;

static NSMutableArray* getTouchedWindows() {
    if (touchedWindows == nil) {
        touchedWindows = [[NSMutableArray alloc] init];
    }
    return touchedWindows;
}

static void rememberTouchedWindow(AXUIElementRef window) {
    @synchronized (getTouchedWindows()) {
        for (id existing in getTouchedWindows()) {
            if (CFEqual((AXUIElementRef)existing, window)) {
                return;
            }
        }
        [getTouchedWindows() addObject:(id)window]; // retains
    }
}

//---

struct NativeRect get_active_ie_bounds() {
    NSRunningApplication* fmApp = NSWorkspace.sharedWorkspace.frontmostApplication;

    struct NativeRect bounds;
    bounds.x = 0;
    bounds.y = 0;
    bounds.w = 0;
    bounds.h = 0;

    lastIeTitleValid = false;
    lastIeTitle[0] = '\0';

    if (fmApp != nil && ![fmApp.localizedName isEqual: @"ShimejiEE"]) {
        currentFrontmostApp = fmApp;
    }

    if (currentFrontmostApp == nil || currentFrontmostApp.terminated || currentFrontmostApp.hidden) {
        return bounds;
    }

    AXUIElementRef appRef = AXUIElementCreateApplication(currentFrontmostApp.processIdentifier);

    CGSize winSize;
    CGPoint winPos;

    if (appRef != NULL) {
        AXUIElementRef windowRef = copyAXFocusedWindowOf(appRef);
        CFRelease(appRef);

        if (windowRef != NULL) {
            winSize = getAXSizeOf(windowRef);
            winPos = getAXPositionOf(windowRef);
            cacheIeTitleOf(windowRef);
            CFRelease(windowRef);
        } else {
            return bounds;
        }
    } else {
        return bounds;
    }

    if (CGSizeEqualToSize(winSize, CGSizeZero)) {
        return bounds;
    }

    bounds.x = (int)round(winPos.x);
    bounds.y = axYToJavaY(winPos.y, winSize.height);
    bounds.w = (int)round(winSize.width);
    bounds.h = (int)round(winSize.height);

    return bounds;
}

void move_ie_window(int x, int y) {
    if (currentFrontmostApp == nil || currentFrontmostApp.terminated || currentFrontmostApp.hidden) {
        return;
    }

    AXUIElementRef appRef = AXUIElementCreateApplication(currentFrontmostApp.processIdentifier);

    if (appRef != NULL) {
        AXUIElementRef windowRef = copyAXFocusedWindowOf(appRef);
        CFRelease(appRef);

        if (windowRef != NULL) {
            CGSize size = getAXSizeOf(windowRef);
            setAXPositionOf(windowRef, CGPointMake(x, javaYToAxY(y, size.height)));
            rememberTouchedWindow(windowRef);
            CFRelease(windowRef);
        }
    }
}

// puts the window back on the main screen when it can't be seen
static void restoreTouchedWindow(AXUIElementRef window, NSArray<NSScreen*>* screens, int cascadeIndex) {
    CGSize size = getAXSizeOf(window);
    if (CGSizeEqualToSize(size, CGSizeZero)) {
        return; // the window is probably gone
    }

    // work in top left coordinates; appkit frames use the classic bottom
    // left origin so the visible frames get converted
    double mainH = mainScreenHeight();
    CGPoint pos = getAXPositionOf(window);
    CGRect winRect = CGRectMake(pos.x, axYToJavaY(pos.y, size.height), size.width, size.height);

    // a window counts as visible when a reasonable chunk of it is on a screen
    for (NSScreen* screen in screens) {
        NSRect vis = [screen visibleFrame];
        CGRect visTopLeft = CGRectMake(vis.origin.x, mainH - vis.origin.y - vis.size.height, vis.size.width, vis.size.height);
        CGRect isect = CGRectIntersection(winRect, visTopLeft);
        if (!CGRectIsNull(isect) && isect.size.width > 60 && isect.size.height > 60) {
            return;
        }
    }

    NSRect target = screens.count > 0 ? screens[0].visibleFrame : NSMakeRect(0, 0, 1280, 720);
    double targetX = target.origin.x + 25 * cascadeIndex;
    double targetY = mainH - target.origin.y - 25 * cascadeIndex; // top of the window, slightly below the cascade
    setAXPositionOf(window, CGPointMake(targetX, javaYToAxY(targetY - size.height, size.height)));
}

void restore_ie() {
    [Util runOnMainSync: ^ {
        @autoreleasepool {
            NSArray<NSScreen*>* screens = [NSScreen screens];

            NSArray* toRestore;
            @synchronized (getTouchedWindows()) {
                toRestore = [[getTouchedWindows() copy] autorelease];
                [getTouchedWindows() removeAllObjects];
            }

            int cascade = 0;
            for (id window in toRestore) {
                restoreTouchedWindow((AXUIElementRef)window, screens, cascade % 8);
                cascade++;
            }
        }
    }];
}

//---

// converts a global bottom left rect into java/awt top left coordinates
static struct NativeRect flipRectToJava(NSRect rect, double mainScreenHeight) {
    struct NativeRect out;
    out.x = (int)round(rect.origin.x);
    out.y = (int)round(mainScreenHeight - (rect.origin.y + rect.size.height));
    out.w = (int)round(rect.size.width);
    out.h = (int)round(rect.size.height);
    return out;
}

int get_work_area_bounds(struct NativeRect* out_areas, int max_areas) {
    if (out_areas == NULL || max_areas <= 0) {
        return 0;
    }

    __block int count = 0;

    [Util runOnMainSync: ^ {
        NSArray<NSScreen*>* screens = [NSScreen screens];
        if (screens.count == 0) {
            return;
        }

        double mainScreenHeight = screens[0].frame.size.height;

        for (NSScreen* screen in screens) {
            if (count >= max_areas) {
                break;
            }

            NSRect visible = [screen visibleFrame];
            // degenerate frames (tiny screens etc) fall back to the full frame
            if (visible.size.width < 10 || visible.size.height < 10) {
                visible = [screen frame];
            }

            out_areas[count] = flipRectToJava(visible, mainScreenHeight);
            count++;
        }
    }];

    return count;
}

//---

bool get_active_ie_title(char* out_buf, int buf_len) {
    if (out_buf == NULL || buf_len <= 0) {
        return false;
    }
    out_buf[0] = '\0';

    // the cached title matches the window of the last get_active_ie_bounds call
    if (lastIeTitleValid) {
        strlcpy(out_buf, lastIeTitle, (size_t)buf_len);
        return out_buf[0] != '\0';
    }

    if (currentFrontmostApp == nil || currentFrontmostApp.terminated || currentFrontmostApp.hidden) {
        return false;
    }

    AXUIElementRef appRef = AXUIElementCreateApplication(currentFrontmostApp.processIdentifier);
    if (appRef == NULL) {
        return false;
    }

    bool found = false;
    AXUIElementRef windowRef = copyAXFocusedWindowOf(appRef);
    CFRelease(appRef);

    if (windowRef != NULL) {
        CFTypeRef titleRef = NULL;
        if (AXUIElementCopyAttributeValue(windowRef, kAXTitleAttribute, &titleRef) == kAXErrorSuccess && titleRef != NULL) {
            if ([(id)titleRef isKindOfClass:[NSString class]]) {
                const char* utf8 = [(NSString*)titleRef UTF8String];
                if (utf8 != NULL) {
                    strlcpy(out_buf, utf8, (size_t)buf_len);
                    found = out_buf[0] != '\0';
                }
            }
            CFRelease(titleRef);
        }
        CFRelease(windowRef);
    }

    return found;
}

//---

bool ax_check_trusted(bool prompt) {
    if (prompt) {
        NSDictionary* options = @{(NSString*)kAXTrustedCheckOptionPrompt: @YES};
        return AXIsProcessTrustedWithOptions((__bridge CFDictionaryRef)options);
    } else {
        return AXIsProcessTrusted();
    }
}

//====== window list =====//

// caches to keep the per tick cost of get_window_list reasonable
static CFMutableDictionaryRef titleCache = NULL;   // window id -> CFStringRef
static CFMutableDictionaryRef axWindowCache = NULL; // window id -> AXUIElementRef

static id getCacheLock() {
    static id lock = nil;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        lock = [[NSObject alloc] init];
    });
    return lock;
}

static void cacheTitle(unsigned long long windowId, NSString* title) {
    @synchronized (getCacheLock()) {
        if (titleCache == NULL) {
            titleCache = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, NULL, &kCFTypeDictionaryValueCallBacks);
        }
        if (CFDictionaryGetCount(titleCache) > 256) {
            CFDictionaryRemoveAllValues(titleCache);
        }
        CFDictionarySetValue(titleCache, (const void*)windowId, (__bridge CFStringRef)title);
    }
}

static NSString* cachedTitle(unsigned long long windowId) {
    @synchronized (getCacheLock()) {
        if (titleCache == NULL) {
            return nil;
        }
        CFStringRef t = CFDictionaryGetValue(titleCache, (const void*)windowId);
        return t == NULL ? nil : (__bridge NSString*)t;
    }
}

static void releaseAxCacheValue(CFAllocatorRef allocator, const void* value) {
    if (value != NULL) {
        CFRelease((CFTypeRef)value);
    }
}

static void cacheAxWindow(unsigned long long windowId, AXUIElementRef window) {
    @synchronized (getCacheLock()) {
        if (axWindowCache == NULL) {
            CFDictionaryValueCallBacks axCallbacks = {0, NULL, NULL, CFCopyDescription, CFEqual};
            axCallbacks.retain = (const void* (*)(CFAllocatorRef, const void*))CFRetain;
            axCallbacks.release = releaseAxCacheValue;
            axWindowCache = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, NULL, &axCallbacks);
        }
        if (CFDictionaryGetCount(axWindowCache) > 64) {
            CFDictionaryRemoveAllValues(axWindowCache);
        }
        CFDictionarySetValue(axWindowCache, (const void*)windowId, window);
    }
}

static AXUIElementRef cachedAxWindow(unsigned long long windowId) {
    @synchronized (getCacheLock()) {
        if (axWindowCache == NULL) {
            return NULL;
        }
        return (AXUIElementRef)CFDictionaryGetValue(axWindowCache, (const void*)windowId);
    }
}

static AXUIElementRef copyAxWindowMatching(AXUIElementRef appRef, CGRect frame) {
    AXUIElementRef matched = NULL;
    CFArrayRef windows = NULL;
    if (AXUIElementCopyAttributeValue(appRef, kAXWindowsAttribute, (CFTypeRef*)&windows) != kAXErrorSuccess || windows == NULL) {
        return NULL;
    }

    CFIndex count = CFArrayGetCount(windows);
    for (CFIndex i = 0; i < count; i++) {
        AXUIElementRef w = (AXUIElementRef)CFArrayGetValueAtIndex(windows, i);
        CGPoint pos = getAXPositionOf(w);
        CGSize size = getAXSizeOf(w);

        // convert the ax position to the top left origin used by frame
        double javaY = axYToJavaY(pos.y, size.height);

        if (fabs(pos.x - frame.origin.x) <= 2 && fabs(javaY - frame.origin.y) <= 2
            && fabs(size.width - frame.size.width) <= 2 && fabs(size.height - frame.size.height) <= 2) {
            matched = (AXUIElementRef)CFRetain(w);
            break;
        }
    }

    CFRelease(windows);
    return matched;
}

static AXUIElementRef resolveAxWindow(int pid, CGRect frame) {
    AXUIElementRef appRef = AXUIElementCreateApplication(pid);
    if (appRef == NULL) {
        return NULL;
    }

    AXUIElementRef window = copyAxWindowMatching(appRef, frame);
    CFRelease(appRef);
    return window;
}

static NSString* readWindowTitle(NSDictionary* info, int pid, unsigned long long windowId, CGRect frame) {
    // kCGWindowName needs the screen recording permission on modern macOS
    NSString* name = info[(id)kCGWindowName];
    if ([name isKindOfClass:[NSString class]] && name.length > 0) {
        return name;
    }

    // accessibility fallback (also caches the ref used by move_window_by_id)
    AXUIElementRef window = resolveAxWindow(pid, frame);
    if (window == NULL) {
        return @"";
    }

    NSString* title = @"";
    CFTypeRef titleRef = NULL;
    if (AXUIElementCopyAttributeValue(window, kAXTitleAttribute, &titleRef) == kAXErrorSuccess && titleRef != NULL) {
        if ([(id)titleRef isKindOfClass:[NSString class]]) {
            title = [(NSString*)titleRef copy];
        }
        CFRelease(titleRef);
    }

    cacheAxWindow(windowId, window);
    CFRelease(window);
    return [title autorelease];
}

int get_window_list(struct NativeWindowInfo* out, int max_count) {
    if (out == NULL || max_count <= 0) {
        return 0;
    }

    int selfPid = getpid();

    @autoreleasepool {
        CFArrayRef listRef = CGWindowListCopyWindowInfo(kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements, kCGNullWindowID);
        if (listRef == NULL) {
            return 0;
        }

        int count = 0;
        for (NSDictionary* info in (__bridge NSArray*)listRef) {
            if (count >= max_count) {
                break;
            }

            if ([info[(id)kCGWindowLayer] longValue] != 0) {
                continue; // only normal windows
            }

            int pid = [info[(id)kCGWindowOwnerPID] intValue];
            if (pid == selfPid) {
                continue;
            }

            CGRect frame = CGRectNull;
            if (!CGRectMakeWithDictionaryRepresentation((CFDictionaryRef)info[(id)kCGWindowBounds], &frame)) {
                continue;
            }
            if (frame.size.width < 40 || frame.size.height < 40) {
                continue;
            }

            unsigned long long windowId = [info[(id)kCGWindowNumber] unsignedLongLongValue];

            NSString* title = cachedTitle(windowId);
            if (title == nil) {
                title = readWindowTitle(info, pid, windowId, frame);
                cacheTitle(windowId, title);
            }

            struct NativeWindowInfo* slot = &out[count];
            slot->window_id = windowId;
            slot->pid = pid;
            slot->bounds.x = (int)frame.origin.x;
            slot->bounds.y = (int)frame.origin.y;
            slot->bounds.w = (int)frame.size.width;
            slot->bounds.h = (int)frame.size.height;
            strlcpy(slot->title, [title UTF8String] ?: "", sizeof(slot->title));

            count++;
        }

        CFRelease(listRef);
        return count;
    }
}

bool move_window_by_id(unsigned long long window_id, int x, int y) {
    AXUIElementRef window = cachedAxWindow(window_id);

    // cached elements can go stale (windows get recreated)
    if (window != NULL && CGSizeEqualToSize(getAXSizeOf(window), CGSizeZero)) {
        window = NULL;
    }
    if (window == NULL) {
        // resolve it from the window's current frame
        CFArrayRef listRef = CGWindowListCopyWindowInfo(kCGWindowListOptionIncludingWindow | kCGWindowListExcludeDesktopElements, (CGWindowID)window_id);
        if (listRef != NULL) {
            CFIndex cnt = CFArrayGetCount(listRef);
            for (CFIndex i = 0; i < cnt; i++) {
                NSDictionary* info = (NSDictionary*)CFArrayGetValueAtIndex(listRef, i);
                int pid = [info[(id)kCGWindowOwnerPID] intValue];
                CGRect frame = CGRectNull;
                if (CGRectMakeWithDictionaryRepresentation((CFDictionaryRef)info[(id)kCGWindowBounds], &frame)) {
                    window = resolveAxWindow(pid, frame);
                    if (window != NULL) {
                        cacheAxWindow(window_id, window);
                        break;
                    }
                }
            }
            CFRelease(listRef);
        }
    }

    if (window == NULL) {
        return false;
    }

    CGSize size = getAXSizeOf(window);
    setAXPositionOf(window, CGPointMake(x, javaYToAxY(y, size.height)));
    rememberTouchedWindow(window);
    return true;
}
