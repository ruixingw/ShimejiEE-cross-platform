package com.group_finity.mascotnative.panama;

import com.group_finity.mascot.image.NativeImage;
import com.group_finity.mascotnative.panama.bindings.render.Image;
import com.group_finity.mascotnative.panama.bindings.render.ImageLoadingOptions;
import com.group_finity.mascotnative.panama.bindings.render.NativeRenderer_h;
import com.group_finity.mascotnative.shared.ImageUtil;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

public class PanamaImage implements NativeImage {
    final Arena arena;
    final MemorySegment image;
    public final int w;
    public final int h;

    private PanamaImage(Arena arena, MemorySegment image, int w, int h) {
        this.arena = arena;
        this.image = image;
        this.w = w;
        this.h = h;
    }

    public static PanamaImage loadFrom(Path path, double scaling, boolean flipped, boolean antialiasing, double opacity, boolean hqx) {
        // hqx is processed in java and handed to the renderer as raw pixels
        if (hqx && ImageUtil.hqxAppliesTo(scaling)) {
            try {
                return createFromPixels(ImageUtil.hqxBufferedImage(path, scaling, flipped, opacity));
            } catch (IOException ignored) {
                // fall back to the native loader
            }
        }

        try (var a = Arena.ofConfined()) {
            var cPath = a.allocateFrom(path.toAbsolutePath().toString());

            var opts = ImageLoadingOptions.allocate(a);
            ImageLoadingOptions.scaling(opts, scaling);
            ImageLoadingOptions.flipped(opts, flipped);
            ImageLoadingOptions.anti_alias(opts, antialiasing);
            ImageLoadingOptions.opacity(opts, opacity);

            var arena = Arena.ofShared();
            var img = NativeRenderer_h.image_load(arena, cPath, opts);
            return new PanamaImage(arena, img, Image.w(img), Image.h(img));
        }
    }

    private static PanamaImage createFromPixels(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        // premultiplied ARGB ints -> A,R,G,B bytes for NSBitmapImageRep
        int[] pixels;
        if (src.getRaster().getDataBuffer() instanceof DataBufferInt db) {
            pixels = db.getData();
        } else {
            pixels = src.getRGB(0, 0, w, h, null, 0, w);
        }

        byte[] bytes = new byte[w * h * 4];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            bytes[i * 4] = (byte) (p >>> 24);
            bytes[i * 4 + 1] = (byte) (p >>> 16);
            bytes[i * 4 + 2] = (byte) (p >>> 8);
            bytes[i * 4 + 3] = (byte) p;
        }

        var arena = Arena.ofShared();
        var pixelsSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
        var img = NativeRenderer_h.image_create_from_argb(arena, w, h, pixelsSeg);
        return new PanamaImage(arena, img, Image.w(img), Image.h(img));
    }

    @Override
    public int getWidth() {
        return w;
    }

    @Override
    public int getHeight() {
        return h;
    }

    @Override
    public void dispose() {
        NativeRenderer_h.image_dispose(image);
        arena.close();
    }
}
