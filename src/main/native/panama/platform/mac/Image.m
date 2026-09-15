
#include "Renderer.h"
#include <string.h>

NSImage* flipImageHorizontally(NSImage * inputImage) {
    NSImage *tmpImage;
    NSAffineTransform *transform = [NSAffineTransform transform];

    NSSize dimensions = [inputImage size];
    NSAffineTransformStruct flip = {-1.0, 0.0, 0.0, 1.0,
                                    dimensions.width, 0.0 };

    tmpImage = [[NSImage alloc] initWithSize:dimensions];
    [tmpImage lockFocus];
    [transform setTransformStruct:flip];
    [transform concat];

    [inputImage drawAtPoint:NSMakePoint(0,0)
                   fromRect:NSMakeRect(0,0, dimensions.width, dimensions.height)
                  operation:NSCompositingOperationCopy fraction:1.0];

    [tmpImage unlockFocus];

    return tmpImage;
}

// applies scaling/flipping/opacity ahead of time so the renderer can draw the
// image 1:1 with pixel exact nearest neighbour sampling for pixel art
static NSImage* transformImage(NSImage* src, double scaling, bool flipped, bool antialias, double opacity) {
    NSSize srcSize = [src size];
    NSSize dstSize = NSMakeSize(round(srcSize.width * scaling), round(srcSize.height * scaling));

    NSImage* dst = [[NSImage alloc] initWithSize:dstSize];
    [dst lockFocus];
    [[NSGraphicsContext currentContext] setImageInterpolation:antialias ? NSImageInterpolationHigh : NSImageInterpolationNone];

    if (flipped) {
        NSAffineTransform* flip = [NSAffineTransform transform];
        [flip setTransformStruct:(NSAffineTransformStruct){-1.0, 0.0, 0.0, 1.0, dstSize.width, 0.0}];
        [flip concat];
    }

    // fraction acts as an alpha multiplier
    [src drawInRect:NSMakeRect(0, 0, dstSize.width, dstSize.height)
             fromRect:NSMakeRect(0, 0, srcSize.width, srcSize.height)
            operation:NSCompositingOperationCopy fraction:opacity];

    [dst unlockFocus];
    return dst;
}

struct Image image_load(char *path, struct ImageLoadingOptions options) {
    struct Image r;
    NSString *p = [NSString stringWithUTF8String:path];
    NSImage* img = [[NSImage alloc] initWithContentsOfFile:p];

    if (img != nil) {
        NSImage* result = img;

        if (options.flipped || options.scaling != 1.0 || options.opacity < 1.0) {
            double opacity = options.opacity > 1.0 ? 1.0 : (options.opacity < 0.0 ? 0.0 : options.opacity);
            result = transformImage(img, options.scaling, options.flipped, options.anti_alias, opacity);
            [img release];
        }

        r.data = result;
        r.w = (int)round([result size].width);
        r.h = (int)round([result size].height);
    } else {
        r.w = 0;
        r.h = 0;
    }

    return r;
}

void image_dispose(struct Image image) {
    [(NSImage*)image.data release];
}

struct Image image_create_from_argb(int width, int height, const unsigned char* pixels) {
    struct Image r;
    r.w = 0;
    r.h = 0;

    if (width <= 0 || height <= 0 || pixels == NULL) {
        r.data = NULL;
        return r;
    }

    NSBitmapImageRep* rep = [[NSBitmapImageRep alloc] initWithBitmapDataPlanes:NULL
                                pixelsWide:width
                                pixelsHigh:height
                                bitsPerSample:8
                                samplesPerPixel:4
                                hasAlpha:YES
                                isPlanar:NO
                                colorSpaceName:NSCalibratedRGBColorSpace
                                bitmapFormat:NSAlphaFirstBitmapFormat
                                bytesPerRow:width * 4
                                bitsPerPixel:32];

    if (rep == nil) {
        r.data = NULL;
        return r;
    }

    memcpy([rep bitmapData], pixels, (size_t)width * (size_t)height * 4);

    NSImage* img = [[NSImage alloc] initWithSize:NSMakeSize(width, height)];
    [img addRepresentation:rep];
    [rep release];

    r.data = img;
    r.w = width;
    r.h = height;
    return r;
}
