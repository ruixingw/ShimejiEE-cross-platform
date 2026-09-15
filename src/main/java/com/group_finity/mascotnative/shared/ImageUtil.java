package com.group_finity.mascotnative.shared;

import hqx.Hqx_2x;
import hqx.Hqx_3x;
import hqx.Hqx_4x;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class ImageUtil {

    public static BufferedImage newBufferedImage(Path path, double scaling, boolean flipped, boolean antialiasing, double opacity, boolean hqx) throws IOException {
        if (hqx && hqxAppliesTo(scaling)) {
            return hqxBufferedImage(path, scaling, flipped, opacity);
        }
        var img = ImageIO.read(path.toFile());
        return transform(img, scaling, flipped, antialiasing, opacity);
    }

    /**
     * Whether the hqx pixel art scaling algorithm applies to the given scale.
     * <p>
     * hqx only works on whole factors 2, 3, 4 (with 6 and 8 done as 3x/4x plus a 2x pass).
     */
    public static boolean hqxAppliesTo(double scaling) {
        return scaling == 2 || scaling == 3 || scaling == 4 || scaling == 6 || scaling == 8;
    }

    /**
     * Loads and scales an image using the hqx algorithm (whole factors only).
     * Other factors fall back to nearest neighbour.
     */
    public static BufferedImage hqxBufferedImage(Path path, double scaling, boolean flipped, double opacity) throws IOException {
        var img = ImageIO.read(path.toFile());
        return hqxTransform(img, scaling, flipped, opacity);
    }

    private static BufferedImage hqxTransform(BufferedImage src, double scaling, boolean flipped, double opacity) {
        if (!hqxAppliesTo(scaling)) {
            return transform(src, scaling, flipped, false, opacity);
        }

        int width = src.getWidth();
        int height = src.getHeight();
        int[] rgbValues = src.getRGB(0, 0, width, height, null, 0, width);
        double effectiveScaling;

        if (scaling == 4 || scaling == 8) {
            int[] buffer = new int[width * 4 * height * 4];
            Hqx_4x.hq4x_32_rb(rgbValues, buffer, width, height);
            rgbValues = buffer;
            width *= 4;
            height *= 4;
            effectiveScaling = scaling > 4 ? 2 : 1;
        } else if (scaling == 3 || scaling == 6) {
            int[] buffer = new int[width * 3 * height * 3];
            Hqx_3x.hq3x_32_rb(rgbValues, buffer, width, height);
            rgbValues = buffer;
            width *= 3;
            height *= 3;
            effectiveScaling = scaling > 3 ? 2 : 1;
        } else { // 2
            int[] buffer = new int[width * 2 * height * 2];
            Hqx_2x.hq2x_32_rb(rgbValues, buffer, width, height);
            rgbValues = buffer;
            width *= 2;
            height *= 2;
            effectiveScaling = 1;
        }

        var scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        scaled.setRGB(0, 0, width, height, rgbValues, 0, width);

        return finish(scaled, (int) Math.round(width * effectiveScaling), (int) Math.round(height * effectiveScaling), flipped, opacity);
    }

    private static BufferedImage transform(BufferedImage src, double scaleFactor, boolean flip, boolean antialiasing, double opacity) {
        return finish(src, (int) Math.round(src.getWidth() * scaleFactor), (int) Math.round(src.getHeight() * scaleFactor), flip, opacity, antialiasing);
    }

    private static BufferedImage finish(BufferedImage src, int fWidth, int fHeight, boolean flip, double opacity) {
        return finish(src, fWidth, fHeight, flip, opacity, false);
    }

    private static BufferedImage finish(BufferedImage src, int fWidth, int fHeight, boolean flip, double opacity, boolean antialiasing) {
        final float clampedOpacity = (float) Math.clamp(opacity, 0.0, 1.0);

        final BufferedImage copy = new BufferedImage(fWidth, fHeight, BufferedImage.TYPE_INT_ARGB_PRE);

        Graphics2D g2d = copy.createGraphics();
        var renderHint = antialiasing
                ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, renderHint);
        // compositing over the empty image multiplies the source alpha,
        // baking the opacity into the pixels
        g2d.setComposite(AlphaComposite.SrcOver.derive(clampedOpacity));
        g2d.drawImage(src, (flip ? fWidth : 0), 0, (flip ? -fWidth : fWidth), fHeight, null);
        g2d.dispose();

        return copy;
    }

}
