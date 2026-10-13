package dev.fayber.seedmap.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NavigationPointerTest {
    private static final int SIDE = 64;

    @Test void mapPointerIsASolidTriangularCursorWithTransparentBackground() {
        int[] pixels = NavigationPointer.rasterizeMap();
        assertEquals(SIDE * SIDE, pixels.length);
        assertEquals(0, pixels[0]);
        assertEquals(0, pixels[63]);
        assertTrue((pixels[10 * SIDE + 32] >>> 24) > 0, "north-pointing tip");
        assertTrue((pixels[34 * SIDE + 32] >>> 24) > 180, "filled map cursor, not hollow");
        assertEquals(0, pixels[58 * SIDE + 32], "no surrounding rectangular background");
    }

    @Test void hudArrowHasStraightShaftAndTriangularHead() {
        int[] pixels = NavigationPointer.rasterizeHud();
        assertEquals(SIDE * SIDE, pixels.length);
        assertEquals(0, pixels[0]);
        assertTrue((pixels[32 * SIDE + 10] >>> 24) > 180, "horizontal shaft");
        assertTrue((pixels[32 * SIDE + 53] >>> 24) > 180, "east-pointing arrow head");
        assertEquals(0, pixels[14 * SIDE + 10], "background around shaft is transparent");
        assertEquals(0, pixels[58 * SIDE + 53], "no opaque overlay box");
    }

    @Test void mapsAndHudAreDifferentShapesAndHaveAntialiasedEdges() {
        int[] map = NavigationPointer.rasterizeMap();
        int[] hud = NavigationPointer.rasterizeHud();
        assertFalse(java.util.Arrays.equals(map, hud));
        for (int[] raster : new int[][]{map, hud}) {
            boolean soft = false, filled = false;
            for (int pixel : raster) {
                int alpha = pixel >>> 24;
                if (alpha > 0 && alpha < 255) soft = true;
                if (alpha >= 245) filled = true;
            }
            assertTrue(soft, "edges should be antialiased");
            assertTrue(filled, "arrow body should be filled");
        }
    }
}
