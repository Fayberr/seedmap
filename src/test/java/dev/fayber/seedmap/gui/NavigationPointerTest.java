package dev.fayber.seedmap.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NavigationPointerTest {
    @Test void navigationGlyphHasNoFilledBackgroundOrCenter() {
        int[] pixels = NavigationPointer.rasterize();
        assertEquals(64 * 64, pixels.length);
        assertEquals(0, pixels[0], "top-left background must be transparent");
        assertEquals(0, pixels[63], "top-right background must be transparent");
        assertEquals(0, pixels[32 * 64 + 32], "hollow pointer has a transparent center");
        assertTrue((pixels[8 * 64 + 32] >>> 24) > 0, "visible tip");
    }

    @Test void glyphHasLeftRightSymmetryAndAntialiasing() {
        int[] pixels = NavigationPointer.rasterize();
        boolean hasSoftPixels = false;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 32; x++) {
                int left = pixels[y * 64 + x] >>> 24;
                int right = pixels[y * 64 + (63 - x)] >>> 24;
                assertTrue(Math.abs(left - right) <= 2, "glyph must be symmetric");
                if (left > 0 && left < 255) hasSoftPixels = true;
            }
        }
        assertTrue(hasSoftPixels, "outline should be antialiased");
    }
}
