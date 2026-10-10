package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;

/**
 * A stable one-physical-pixel outer alpha stroke based on the icon's actual
 * transparency. Unlike marching-squares contours, alpha dilation preserves
 * sharp corners, small gaps and staircase-like pixel art.
 *
 * Executed ONCE per distinct icon + GUI scale; the result is uploaded as a
 * texture and rendered with one draw call on subsequent frames.
 */
public final class SilhouetteRaster {
    public static final int MARGIN = 2;
    public static final int SIZE = 16 + MARGIN * 2;

    private SilhouetteRaster() {}

    public static int[] rasterize(BufferedImage source, int guiScale) {
        int scale = Math.clamp(guiScale, 1, 8);
        int size = SIZE * scale;
        int[] pixels = new int[size * size];
        if (source == null || source.getWidth() < 1 || source.getHeight() < 1) return pixels;

        for (int sy = 0; sy < size; sy++) {
            for (int sx = 0; sx < size; sx++) {
                // Convert physical output pixel centers back into 16x16 GUI icon coordinates.
                float px = (sx + 0.5f) / scale - MARGIN;
                float py = (sy + 0.5f) / scale - MARGIN;
                float inside = alpha(source, px, py);
                float dilated = inside;

                // Expand the *actual mask*, not guessed polylines. A 1.5-physical-
                // pixel round kernel gives even coverage for horizontal, vertical,
                // diagonal and corner edges.
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        dilated = Math.max(dilated,
                                alpha(source, px + dx / (float) scale,
                                        py + dy / (float) scale));
                    }
                }
                // Outside-only stroke: the opaque original icon is drawn on top.
                // Normalise alpha gradients by GUI scale: a 1px physical stroke
                // should remain equally bright at scale 1, 3 and 6.
                int opacity = Math.clamp(Math.round((dilated - inside) * scale * 225), 0, 235);
                pixels[sy * size + sx] = opacity == 0 ? 0 : (opacity << 24) | 0xFFFFFF;
            }
        }
        return pixels;
    }

    private static float alpha(BufferedImage image, float guiX, float guiY) {
        float x = guiX * image.getWidth() / 16f - 0.5f;
        float y = guiY * image.getHeight() / 16f - 0.5f;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float tx = x - ix, ty = y - iy;
        float top = pixel(image, ix, iy) * (1f - tx) + pixel(image, ix + 1, iy) * tx;
        float bottom = pixel(image, ix, iy + 1) * (1f - tx) + pixel(image, ix + 1, iy + 1) * tx;
        return top * (1f - ty) + bottom * ty;
    }

    private static float pixel(BufferedImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return 0f;
        return ((image.getRGB(x, y) >>> 24) & 255) / 255f;
    }
}
