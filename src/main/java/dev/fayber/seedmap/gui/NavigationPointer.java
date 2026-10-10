package dev.fayber.seedmap.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Compact compass needle shared by the player, detached Freecam camera and
 * waypoint HUD. Drawn procedurally so it stays sharp and theme-independent.
 * Unlike the old textured arrow, the new design has a narrow point and notched tail.
 */
public final class NavigationPointer {
    private NavigationPointer() {}

    public static void draw(GuiGraphicsExtractor gfx, int x, int y, float radians, int color) {
        int alpha = (color >>> 24) & 0xFF;
        int outline = (Math.min(alpha, 212) << 24) | 0x101010;
        var pose = gfx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(radians);

        for (int row = -10; row <= 6; row++) {
            int half = Math.max(0, (int) Math.floor((row + 10) * 0.43));
            gfx.fill(-half - 1, row, half + 2, row + 1, outline);
            gfx.fill(-half, row, half + 1, row + 1, color);
            // The recessed tail makes the glyph recognizably different from the old triangle.
            if (row >= 1) {
                int cut = Math.max(0, (row - 1) / 2);
                gfx.fill(-cut, row, cut + 1, row + 1, outline);
            }
        }
        pose.popMatrix();
    }
}
