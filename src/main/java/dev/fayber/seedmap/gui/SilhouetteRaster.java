package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;

/**
 * Renders a one-physical-pixel outer silhouette based on the active icon alpha.
 *
 * A fixed 50% coverage contour is used for ALL icons. Previous code multiplied
 * transparency gradients by GUI scale, making antialiased sprites and pixel art
 * appear to have different blur/thickness.
 *
 * This work only runs when the icon or resource pack changes, not every frame.
 */
public final class SilhouetteRaster {
    public static final int MARGIN = 2;
    public static final int SIZE = 16 + MARGIN * 2;
    private static final float ALPHA_CUTOFF = 0.5f;

    private SilhouetteRaster() {}

    public static int[] rasterize(BufferedImage source, int guiScale) {
        int scale = Math.clamp(guiScale, 1, 8);
        int width = SIZE * scale;
        int[] outline = new int[width * width];
        if (source == null || source.getWidth() < 1 || source.getHeight() < 1) return outline;

        // Normalize masks to the *rendered* pixel grid rather than the source
        // texture's dimensions, so 16x16 and 64x64 resource-pack sprites
        // receive the same physical border width.
        boolean[] opaque = new boolean[width * width];
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) {
                float px = (x + 0.5f) / scale - MARGIN;
                float py = (y + 0.5f) / scale - MARGIN;
                opaque[y * width + x] = sampleAlpha(source, px, py) >= ALPHA_CUTOFF;
            }
        }

        // 8-connected outer ring, with uniform brightness in every direction.
        // Diagonal pixels are modestly antialiased; no blur extends past one
        // physical pixel, and square or stair-step pixel-art corners remain intact.
        for (int y = 1; y < width - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int index = y * width + x;
                if (opaque[index]) continue;
                boolean side = opaque[index - 1] || opaque[index + 1]
                        || opaque[index - width] || opaque[index + width];
                boolean diagonal = opaque[index - width - 1] || opaque[index - width + 1]
                        || opaque[index + width - 1] || opaque[index + width + 1];
                if (side || diagonal) {
                    int alpha = side ? 235 : 180;
                    outline[index] = (alpha << 24) | 0xFFFFFF;
                }
            }
        }
        return outline;
    }

    private static float sampleAlpha(BufferedImage image, float guiX, float guiY) {
        float u = guiX * image.getWidth() / 16f - 0.5f;
        float v = guiY * image.getHeight() / 16f - 0.5f;
        int x = (int) Math.floor(u), y = (int) Math.floor(v);
        float fx = u - x, fy = v - y;
        float top = alpha(image, x, y) * (1 - fx) + alpha(image, x + 1, y) * fx;
        float bottom = alpha(image, x, y + 1) * (1 - fx) + alpha(image, x + 1, y + 1) * fx;
        return top * (1 - fy) + bottom * fy;
    }

    private static float alpha(BufferedImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return 0;
        return ((image.getRGB(x, y) >>> 24) & 255) / 255f;
    }
}
