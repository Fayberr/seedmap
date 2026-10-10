package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;

/**
 * Strict pixel-art silhouette, including stair-step diagonals and hard corners.
 * No bilinear filtering, fractional threshold, blur, or marching-squares paths.
 *
 * Generated only once per icon/model and GUI scale; each subsequent frame
 * draws one cached texture. Width is measured in PHYSICAL screen pixels.
 */
public final class SilhouetteRaster {
    public static final int MARGIN = 3;
    public static final int SIZE = 16 + 2 * MARGIN;
    public static final int STROKE_PIXELS = 2;
    private static final int ALPHA_THRESHOLD = 128;
    private static final int STROKE_COLOR = 0xF5FFFFFF;

    private SilhouetteRaster() {}

    public static int[] rasterize(BufferedImage image, int guiScale) {
        int scale = Math.clamp(guiScale, 1, 8);
        int side = SIZE * scale;
        int[] out = new int[side * side];
        if (image == null || image.getWidth() == 0 || image.getHeight() == 0) return out;

        boolean[] occupied = new boolean[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                // Nearest texel: a diagonal made of square pixels must stay
                // a staircase, not an interpolated line through those pixels.
                int tx = (int) Math.floor((x + 0.5 - MARGIN * scale)
                        * image.getWidth() / (16.0 * scale));
                int ty = (int) Math.floor((y + 0.5 - MARGIN * scale)
                        * image.getHeight() / (16.0 * scale));
                if (tx >= 0 && ty >= 0 && tx < image.getWidth() && ty < image.getHeight()) {
                    occupied[y * side + x] = (image.getRGB(tx, ty) >>> 24) >= ALPHA_THRESHOLD;
                }
            }
        }

        // Square dilation gives equal thickness at both horizontal and diagonal
        // pixel steps; the original opaque mask is excluded from the white ring.
        for (int y = STROKE_PIXELS; y < side - STROKE_PIXELS; y++) {
            for (int x = STROKE_PIXELS; x < side - STROKE_PIXELS; x++) {
                int center = y * side + x;
                if (occupied[center]) continue;
                boolean bordering = false;
                for (int dy = -STROKE_PIXELS; dy <= STROKE_PIXELS && !bordering; dy++) {
                    for (int dx = -STROKE_PIXELS; dx <= STROKE_PIXELS; dx++) {
                        if (occupied[(y + dy) * side + x + dx]) {
                            bordering = true;
                            break;
                        }
                    }
                }
                if (bordering) out[center] = STROKE_COLOR;
            }
        }
        return out;
    }
}
