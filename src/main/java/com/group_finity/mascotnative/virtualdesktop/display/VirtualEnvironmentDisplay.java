package com.group_finity.mascotnative.virtualdesktop.display;

import com.group_finity.mascotnative.virtualdesktop.VirtualWindowPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class VirtualEnvironmentDisplay {

    private static final int INIT_WIDTH = 900;
    private static final int INIT_HEIGHT = 600;

    private final JFrame frame;
    private final ContentPane content;

    /** @param mode one of centre/fill/fit/stretch */
    public record BackgroundSettings(Color color, Path image, String mode) {}

    private static class ContentPane extends JPanel {
        private BufferedImage backgroundImage;
        private String backgroundMode = "centre";

        public void setBackgroundSettings(BufferedImage image, String mode) {
            this.backgroundImage = image;
            this.backgroundMode = mode == null ? "centre" : mode;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage == null) {
                return;
            }

            int w = getWidth();
            int h = getHeight();
            int iw = backgroundImage.getWidth();
            int ih = backgroundImage.getHeight();

            switch (backgroundMode) {
                case "stretch" -> g.drawImage(backgroundImage, 0, 0, w, h, null);
                case "fill" -> {
                    double scale = Math.max((double) w / iw, (double) h / ih);
                    int dw = (int) Math.round(iw * scale);
                    int dh = (int) Math.round(ih * scale);
                    g.drawImage(backgroundImage, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                }
                case "fit" -> {
                    double scale = Math.min((double) w / iw, (double) h / ih);
                    int dw = (int) Math.round(iw * scale);
                    int dh = (int) Math.round(ih * scale);
                    g.drawImage(backgroundImage, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                }
                default -> // centre
                        g.drawImage(backgroundImage, (w - iw) / 2, (h - ih) / 2, null);
            }
        }
    }

    public VirtualEnvironmentDisplay() {
        frame = new JFrame("ShimejiEE");
        content = new ContentPane();
        content.setPreferredSize(new Dimension(INIT_WIDTH, INIT_HEIGHT));
        content.setLayout(null);
        content.setBackground(Color.WHITE);
        frame.setContentPane(content);

        SwingUtilities.invokeLater(() -> {
            frame.pack();
            frame.setVisible(true);
        });
    }

    public List<Rectangle> getDisplayBoundsList() {
        return List.of(frame.getContentPane().getBounds());
    }

    public Point getCursorLocation(Point screenCoordinates) {
        var p = screenCoordinates.getLocation();
        SwingUtilities.convertPointFromScreen(p, frame.getContentPane());
        return p;
    }

    public void addShimejiWindow(VirtualWindowPanel panel) {
        SwingUtilities.invokeLater(() -> {
            frame.getContentPane().add(panel);
            frame.validate();
        });
    }

    /**
     * Applies the window mode (virtual desktop) settings.
     * <p>
     * Invalid values keep the current state.
     */
    public void applySettings(int width, int height, BackgroundSettings background) {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);

            if (width > 0 && height > 0) {
                content.setPreferredSize(new Dimension(width, height));
                frame.pack();
            }

            if (background != null) {
                if (background.color() != null) {
                    content.setBackground(background.color());
                }

                BufferedImage img = null;
                if (background.image() != null && Files.isRegularFile(background.image())) {
                    try {
                        img = ImageIO.read(background.image().toFile());
                    } catch (Exception ignored) {
                    }
                }
                content.setBackgroundSettings(img, background.mode());
            }
        });
    }

    /**
     * Hides the window mode display (used when switching environments at runtime).
     */
    public void hide() {
        SwingUtilities.invokeLater(() -> frame.setVisible(false));
    }
}
