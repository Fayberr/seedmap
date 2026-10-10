package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Marching-squares alpha isolines in a 16x16 GUI icon coordinate system. */
public final class SilhouetteContour {
    private static final float STEP = 0.5f; // sub-GUI-pixel contour samples
    private static final float START = -1.0f;
    private static final int GRID = 37;
    private static final float CUTOFF = 0.44f;

    public record Segment(float x1, float y1, float x2, float y2) {}

    private SilhouetteContour() {}

    public static List<Segment> trace(BufferedImage source) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return List.of();
        }
        float[][] a = new float[GRID][GRID];
        float max = 0;
        float min = 1;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                a[y][x] = alpha(source, START + x * STEP, START + y * STEP);
                max = Math.max(max, a[y][x]);
                min = Math.min(min, a[y][x]);
            }
        }
        if (max < CUTOFF || min >= CUTOFF) return List.of();

        List<Segment> segments = new ArrayList<>();
        for (int y = 0; y < GRID - 1; y++) {
            for (int x = 0; x < GRID - 1; x++) {
                float x0 = START + x * STEP;
                float y0 = START + y * STEP;
                float tl = a[y][x], tr = a[y][x + 1];
                float br = a[y + 1][x + 1], bl = a[y + 1][x];
                List<float[]> crosses = new ArrayList<>(4);
                // Clockwise: top, right, bottom, left
                if ((tl >= CUTOFF) != (tr >= CUTOFF))
                    crosses.add(new float[]{x0 + STEP * interpolate(tl, tr), y0});
                if ((tr >= CUTOFF) != (br >= CUTOFF))
                    crosses.add(new float[]{x0 + STEP, y0 + STEP * interpolate(tr, br)});
                if ((br >= CUTOFF) != (bl >= CUTOFF))
                    crosses.add(new float[]{x0 + STEP * interpolate(bl, br), y0 + STEP});
                if ((bl >= CUTOFF) != (tl >= CUTOFF))
                    crosses.add(new float[]{x0, y0 + STEP * interpolate(tl, bl)});

                if (crosses.size() == 2) {
                    segments.add(between(crosses.get(0), crosses.get(1)));
                } else if (crosses.size() == 4) {
                    segments.add(between(crosses.get(0), crosses.get(1)));
                    segments.add(between(crosses.get(2), crosses.get(3)));
                }
            }
        }
        return List.copyOf(segments);
    }

    private static Segment between(float[] a, float[] b) {
        return new Segment(a[0], a[1], b[0], b[1]);
    }

    private static float interpolate(float a, float b) {
        if (a == b) return 0.5f;
        return Math.clamp((CUTOFF - a) / (b - a), 0, 1);
    }

    private static float alpha(BufferedImage image, float x, float y) {
        float sx = x * image.getWidth() / 16f - 0.5f;
        float sy = y * image.getHeight() / 16f - 0.5f;
        int px = (int) Math.floor(sx);
        int py = (int) Math.floor(sy);
        float fx = sx - px;
        float fy = sy - py;
        float tl = pixel(image, px, py);
        float tr = pixel(image, px + 1, py);
        float bl = pixel(image, px, py + 1);
        float br = pixel(image, px + 1, py + 1);
        return (tl * (1f - fx) + tr * fx) * (1f - fy)
                + (bl * (1f - fx) + br * fx) * fy;
    }

    private static float pixel(BufferedImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return 0;
        return ((image.getRGB(x, y) >>> 24) & 255) / 255f;
    }
}
