#pragma once

#import <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

void init_native_environment();
void update_native_environment();

struct NativeRect {
    int x;
    int y;
    int w;
    int h;
};

struct NativePoint {
    int x;
    int y;
};

struct NativeRect get_active_ie_bounds(void);
void move_ie_window(int x, int y);
void restore_ie(void);

/**
 * Writes the work area (visible frame) of each display into out_areas,
 * converted to java/awt coordinates (origin at the top left of the main display).
 * @return the number of areas written, or 0 on failure.
 */
int get_work_area_bounds(struct NativeRect* out_areas, int max_areas);

/**
 * Copies the title of the active IE window into out_buf (truncated, null terminated).
 * @return true if a non empty title was found.
 */
bool get_active_ie_title(char* out_buf, int buf_len);

/**
 * A normal on screen window (not owned by this process) that mascots can
 * potentially interact with. Bounds use java/awt coordinates.
 */
struct NativeWindowInfo {
    unsigned long long window_id;
    int pid;
    struct NativeRect bounds;
    char title[256];
};

/**
 * Lists the current candidate windows in top to bottom z order.
 * @return the number of windows written.
 */
int get_window_list(struct NativeWindowInfo* out, int max_count);

/**
 * Tries to move the window with the given id (from get_window_list).
 * @return true when the window was found and moved.
 */
bool move_window_by_id(unsigned long long window_id, int x, int y);

/**
 * @param prompt when true, the system accessibility permission prompt is shown.
 * @return whether this process is trusted by the accessibility api.
 */
bool ax_check_trusted(bool prompt);

#ifdef __cplusplus
}
#endif
