package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;

public final class CompositeSilhouette {
    public static final int OVERLAY_SIZE = 10;
    public static final int OVERLAY_OFFSET = 6;
    private CompositeSilhouette() {}
    public static BufferedImage combine(BufferedImage base, BufferedImage overlay) {
        if (base == null || overlay == null) return null;
        var result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int alpha = base.getRGB(x * base.getWidth() / 64, y * base.getHeight() / 64) >>> 24;
            int ox = x - OVERLAY_OFFSET * 4, oy = y - OVERLAY_OFFSET * 4;
            if (ox >= 0 && oy >= 0 && ox < OVERLAY_SIZE * 4 && oy < OVERLAY_SIZE * 4) {
                int top = overlay.getRGB(ox * overlay.getWidth() / (OVERLAY_SIZE * 4),
                        oy * overlay.getHeight() / (OVERLAY_SIZE * 4)) >>> 24;
                alpha = top + (alpha * (255 - top) + 127) / 255;
            }
            result.setRGB(x, y, alpha == 0 ? 0 : (alpha << 24) | 0xFFFFFF);
        }
        return result;
    }
}
