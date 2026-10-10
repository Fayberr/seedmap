package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Rasterises an icon's alpha contour once into a thin, uniformly lit white ring.
 * Work scales with icon texture resolution, not the number of rendered frames.
 */
public final class SilhouetteRaster {
    public static final int MARGIN = 2;
    public static final int SIZE = 16 + 2 * MARGIN;

    private SilhouetteRaster() {}

    /** ARGB image, suitable for uploading as a cached dynamic texture. */
    public static int[] rasterize(BufferedImage image, int scale) {
        int multiplier = Math.clamp(scale, 1, 8);
        int dimension = SIZE * multiplier;
        int[] pixels = new int[dimension * dimension];
        List<SilhouetteContour.Segment> edges = SilhouetteContour.trace(image);
        if (edges.isEmpty()) return pixels;

        for (SilhouetteContour.Segment edge : edges) {
            float ax = (edge.x1() + MARGIN) * multiplier;
            float ay = (edge.y1() + MARGIN) * multiplier;
            float bx = (edge.x2() + MARGIN) * multiplier;
            float by = (edge.y2() + MARGIN) * multiplier;
            float dx = bx - ax, dy = by - ay;
            float lengthSq = dx * dx + dy * dy;
            int minX = Math.max(0, (int) Math.floor(Math.min(ax, bx) - 2));
            int minY = Math.max(0, (int) Math.floor(Math.min(ay, by) - 2));
            int maxX = Math.min(dimension - 1, (int) Math.ceil(Math.max(ax, bx) + 2));
            int maxY = Math.min(dimension - 1, (int) Math.ceil(Math.max(ay, by) + 2));
            for (int py = minY; py <= maxY; py++) {
                for (int px = minX; px <= maxX; px++) {
                    float vx = px + 0.5f - ax, vy = py + 0.5f - ay;
                    float t = lengthSq < 1e-6f ? 0 : Math.clamp((vx * dx + vy * dy) / lengthSq, 0, 1);
                    float distance = (float) Math.hypot(vx - t * dx, vy - t * dy);
                    // A 1.35-physical-pixel anti-aliased stroke, with brightness
                    // independent of edge orientation and GUI scale.
                    int alpha = Math.clamp(Math.round((1.45f - distance) * 225), 0, 225);
                    int index = py * dimension + px;
                    if (alpha > (pixels[index] >>> 24)) pixels[index] = (alpha << 24) | 0xFFFFFF;
                }
            }
        }
        return pixels;
    }
}
