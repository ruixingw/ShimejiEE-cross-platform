package com.group_finity.mascotnative.virtualdesktop;

import com.group_finity.mascot.environment.*;
import com.group_finity.mascotnative.virtualdesktop.display.VirtualEnvironmentDisplay;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class VirtualEnvironment extends BaseNativeEnvironment {

    private final VirtualEnvironmentDisplay display;

    public VirtualEnvironment(VirtualEnvironmentDisplay display) {
        this.display = display;
    }

    @Override
    protected void updateIe(Area ieToUpdate) {
        ieToUpdate.setVisible(false);
    }

    @Override
    protected List<Rectangle> getNewDisplayBoundsList() {
        return display.getDisplayBoundsList();
    }

    @Override
    public void tick() {
        super.tick();
        updateDisplayBounds();
    }

    @Override
    public String getActiveIETitle() {
        return null;
    }

    @Override
    public void moveActiveIE(Point point) {
    }

    @Override
    public void restoreIE() {
    }

    @Override
    public void applyVirtualDesktopSettings(String windowSize, String background, String backgroundMode, String backgroundImage) {
        var size = parseSize(windowSize);
        var color = parseColor(background);
        var image = backgroundImage == null || backgroundImage.isBlank()
                ? null
                : Path.of(backgroundImage.trim());

        display.applySettings(
                size == null ? 0 : size.width,
                size == null ? 0 : size.height,
                new VirtualEnvironmentDisplay.BackgroundSettings(color, image, backgroundMode));
    }

    private static Dimension parseSize(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            String[] parts = raw.toLowerCase().split("x");
            if (parts.length == 2) {
                return new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Color parseColor(String hex) {
        if (hex == null || !hex.startsWith("#") || (hex.length() != 7 && hex.length() != 9)) {
            return null;
        }
        try {
            return new Color((int) Long.parseLong(hex.substring(1), 16), hex.length() == 9);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected Point getNewCursorLocation() {
        return display.getCursorLocation(super.getNewCursorLocation());
    }
}
