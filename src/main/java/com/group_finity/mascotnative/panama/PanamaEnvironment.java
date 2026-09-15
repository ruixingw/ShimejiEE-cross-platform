package com.group_finity.mascotnative.panama;

import com.group_finity.mascot.environment.Area;
import com.group_finity.mascot.environment.BaseNativeEnvironment;
import com.group_finity.mascot.environment.WindowTitleFilter;
import com.group_finity.mascotnative.panama.bindings.environment.NativeEnvironment_h;
import com.group_finity.mascotnative.panama.bindings.environment.NativeRect;

import java.awt.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class PanamaEnvironment extends BaseNativeEnvironment {
    private static final Rectangle INVISIBLE_RECT = new Rectangle(1, 1, -10_000, -10_000);

    private static final int MAX_SCREENS = 16;

    private volatile WindowTitleFilter windowFilter = WindowTitleFilter.ALLOW_ALL;

    // when a whitelist is configured, all on screen windows are enumerated
    // (matching the window behavior of the original windows version)
    private volatile boolean enumerateWindows = false;
    private long activeWindowId = 0;
    private volatile String enumeratedTitle = null;

    @Override
    public void setInteractiveWindowFilter(List<String> whitelist, List<String> blacklist) {
        windowFilter = WindowTitleFilter.of(whitelist, blacklist);
        enumerateWindows = whitelist != null && !whitelist.isEmpty();
    }

    @Override
    protected void updateIe(Area ieToUpdate) {
        if (enumerateWindows) {
            updateIeFromWindowList(ieToUpdate);
            return;
        }

        try (var a = Arena.ofConfined()) {
            var r = NativeEnvironment_h.get_active_ie_bounds(a);
            var nr = new Rectangle();

            nr.x = NativeRect.x(r);
            nr.y = NativeRect.y(r);
            nr.width = NativeRect.w(r);
            nr.height = NativeRect.h(r);

            if (nr.isEmpty() || !windowFilter.test(readCachedIeTitle())) {
                ieToUpdate.setVisible(false);
                ieToUpdate.set(INVISIBLE_RECT);
            } else {
                ieToUpdate.setVisible(true);
                ieToUpdate.set(nr);
            }
        } catch (Exception | Error e) {
            ieToUpdate.setVisible(false);
            ieToUpdate.set(INVISIBLE_RECT);
        }
    }

    private void updateIeFromWindowList(Area ieToUpdate) {
        activeWindowId = 0;
        enumeratedTitle = null;

        try (var a = Arena.ofConfined()) {
            var buf = com.group_finity.mascotnative.panama.bindings.environment.NativeWindowInfo.allocateArray(MAX_WINDOWS, a);
            int count = NativeEnvironment_h.get_window_list(buf, MAX_WINDOWS);

            for (int i = 0; i < count; i++) {
                var info = com.group_finity.mascotnative.panama.bindings.environment.NativeWindowInfo.asSlice(buf, i);
                String title = readWindowTitle(info);

                if (windowFilter.test(title)) {
                    var b = com.group_finity.mascotnative.panama.bindings.environment.NativeWindowInfo.bounds(info);
                    var nr = new Rectangle(NativeRect.x(b), NativeRect.y(b), NativeRect.w(b), NativeRect.h(b));

                    activeWindowId = com.group_finity.mascotnative.panama.bindings.environment.NativeWindowInfo.window_id(info);
                    enumeratedTitle = title;

                    ieToUpdate.setVisible(true);
                    ieToUpdate.set(nr);
                    return;
                }
            }
        } catch (Exception | Error ignored) {
        }

        ieToUpdate.setVisible(false);
        ieToUpdate.set(INVISIBLE_RECT);
    }

    private static String readWindowTitle(java.lang.foreign.MemorySegment info) {
        var titleSeg = com.group_finity.mascotnative.panama.bindings.environment.NativeWindowInfo.title(info);
        return titleSeg.getString(0);
    }

    private static final int MAX_WINDOWS = 16;

    /**
     * The title cached by the last get_active_ie_bounds call (no extra
     * accessibility roundtrip).
     */
    private String readCachedIeTitle() {
        try (var a = Arena.ofConfined()) {
            var buf = a.allocate(512);
            if (NativeEnvironment_h.get_active_ie_title(buf, 512)) {
                byte[] bytes = buf.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
                int len = 0;
                while (len < bytes.length && bytes[len] != 0) {
                    len++;
                }
                return new String(bytes, 0, len, StandardCharsets.UTF_8);
            }
        } catch (Exception | Error ignored) {
        }
        return null;
    }

    @Override
    public String getActiveIETitle() {
        if (enumerateWindows) {
            return enumeratedTitle;
        }
        return readCachedIeTitle();
    }

    /**
     * The work areas of all displays as reported by the system.
     * <p>
     * Unlike the awt default, these exclude the menu bar and dock.
     */
    @Override
    protected List<Rectangle> getNewDisplayBoundsList() {
        try (var a = Arena.ofConfined()) {
            var buf = NativeRect.allocateArray(MAX_SCREENS, a);
            int count = NativeEnvironment_h.get_work_area_bounds(buf, MAX_SCREENS);

            if (count > 0) {
                List<Rectangle> ret = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    var r = NativeRect.asSlice(buf, i);
                    ret.add(new Rectangle(NativeRect.x(r), NativeRect.y(r), NativeRect.w(r), NativeRect.h(r)));
                }
                return ret;
            }
        } catch (Exception | Error ignored) {
            // fall back to the awt bounds when the native call fails
        }

        return super.getNewDisplayBoundsList();
    }

    @Override
    public void moveActiveIE(Point point) {
        if (!getActiveIE().isVisible()) {
            return;
        }

        var ie = getActiveIE().toRectangle();
        var workArea = getWorkAreaAt(point).toRectangle();

        // keep a grabbable part of the window on the work area
        final int minVisibleWidth = Math.min(80, ie.width / 2 + 1);
        final int minVisibleHeight = Math.min(40, ie.height / 2 + 1);

        point.x = Math.clamp(point.x, workArea.x - ie.width + minVisibleWidth, workArea.x + workArea.width - minVisibleWidth);
        point.y = Math.clamp(point.y, workArea.y, workArea.y + workArea.height - minVisibleHeight);

        if (enumerateWindows && activeWindowId != 0) {
            NativeEnvironment_h.move_window_by_id(activeWindowId, point.x, point.y);
        } else {
            NativeEnvironment_h.move_ie_window(point.x, point.y);
        }
    }

    @Override
    public void restoreIE() {
        NativeEnvironment_h.restore_ie();
    }

    @Override
    public boolean isAccessibilityTrusted() {
        return NativeEnvironment_h.ax_check_trusted(false);
    }

    @Override
    public void requestAccessibilityTrust() {
        NativeEnvironment_h.ax_check_trusted(true);
    }
}
