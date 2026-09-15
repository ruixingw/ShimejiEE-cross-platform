package com.group_finity.mascot.environment;

import java.awt.Point;

/**
 * Provides info and interacts with the current desktop environment
 */
public interface NativeEnvironment {

    /**
     * Completes any setup needed before accurate info can be provided.
     * <p>
     * Call this before using the environment.
     * <p>
     * This method can be used to set up the initial display bounds and/or to start
     * a thread to periodically update the display bounds. This method should be called only after
     * the object is constructed.
     */
    default void init() {}

    //---desktop

    /**
     * The area of a display that contains the specified point.
     * <p>
     * This method is called at very irregular intervals and should not
     * have side effects.
     *
     * @return The work area at the specified point,
     * If two areas are found, the first one found is returned.
     * The delta values of these areas are not reliable and should
     * always be treated as 0. They may also be immutable and changes
     * made to them might not apply.
     */
    Area getWorkAreaAt(Point point);

    /**
     * The combined bounds of all displays in screen space.
     */
    Area getScreen();

    /**
     * Complex area composed of all displays.
     */
    ComplexArea getComplexScreen();

    /**
     * Whether the environment has either a floor or ceiling at the specified location.
     * <p>
     * This does not include IEs, only screens.
     */
    boolean isScreenTopBottom(final Point location);

    /**
     * Whether the environment has either a side wall at the specified location.
     * <p>
     * This does not include IEs, only screens.
     */
    boolean isScreenLeftRight(final Point location);

    //---IE

    /**
     * Called every frame by the manager.
     * <p>
     * Display updates should not be performed in this function as they very rarely changed.
     * Use notifications or a separate thread instead.
     */
    void tick();

    // Yes, IE didn't actually stand for interactive environment, but it works nicely
    /**
     * The bounds of the current IE (interactive environment)
     * <p>
     * The IE is generally the "frontmost window"
     * but this can be platform dependent
     */
    Area getActiveIE();

    /**
     * Name of the current IE
     * <p>
     * This method is very rarely called and the ie title
     * might not need to be constantly updated
     *
     * @return The title of the current IE if possible, null otherwise.
     */
    default String getActiveIETitle() {
        return null;
    }

    /**
     * Tries to set the top left corner of the current IE to specified location.
     * <p>
     * If the point is an invalid location, the IE is simply not moved.
     * @param point The destination in screen coordinates.
     */
    default void moveActiveIE(final Point point) {}

    /**
     * Restores all moved IEs to a usable location/state.
     */
    default void restoreIE() {}

    //---accessibility

    /**
     * Whether this process is trusted by the platform's accessibility api.
     * <p>
     * Untrusted environments can't query/move other app's windows,
     * which disables ie (window) interactions.
     *
     * @return true when accessibility isn't needed or has been granted.
     */
    default boolean isAccessibilityTrusted() {
        return true;
    }

    /**
     * Shows the system prompt asking the user for accessibility trust.
     */
    default void requestAccessibilityTrust() {}

    //---cursor

    /**
     * The location of the cursor in screen coordinates.
     */
    Location getCursor();

    //---interactive window filter

    /**
     * Restricts which windows count as interactive environments (mascot
     * carried/thrown windows) based on their titles.
     * <p>
     * Empty lists accept every window. Backends that can't read window titles
     * can ignore this.
     *
     * @param whitelist accepted title substrings (slash separated captions).
     * @param blacklist rejected title substrings, takes precedence over the whitelist.
     */
    default void setInteractiveWindowFilter(java.util.List<String> whitelist, java.util.List<String> blacklist) {}

    //---virtual desktop settings

    /**
     * Pushes the virtual desktop (window mode) settings.
     * <p>
     * Values are the raw pref strings; only the virtual desktop backend uses them.
     *
     * @param windowSize "width x height" of the window (eg {@code 900x600}).
     * @param background hex background colour (eg {@code #00FF00}).
     * @param backgroundMode one of centre/fill/fit/stretch.
     * @param backgroundImage path of a background image, empty for none.
     */
    default void applyVirtualDesktopSettings(String windowSize, String background, String backgroundMode, String backgroundImage) {}

}
