package dev.fayber.seedmap.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class RenderedItemCaptureTest {
    @Test void decodesTrueRenderedModelAlphaNotFlatTextureCorners() {
        ByteBuffer atlas = ByteBuffer.allocate(4 * 4 * 4);
        // A tiny rendered boat-like pixel silhouette with transparent corners.
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            atlas.put((byte) 60).put((byte) 120).put((byte) 30);
            atlas.put((byte) (y == 2 || (x == 1 && y == 1) ? 255 : 0));
        }
        atlas.flip();
        var mask = RenderedItemCapture.decodeAlpha(atlas, 4, 4);
        assertNotNull(mask);
        assertEquals(0, mask.getRGB(0, 0) >>> 24);
        assertEquals(255, mask.getRGB(1, 1) >>> 24);
        assertEquals(255, mask.getRGB(3, 2) >>> 24);
        assertEquals(0, mask.getRGB(3, 3) >>> 24);
    }

    @Test void gpuAtlasReadbackNeedsVerticalFlipToMatchGuiSprite() {
        ByteBuffer pixels = ByteBuffer.allocate(3 * 3 * 4);
        // A tall asymmetric 'upright crystal': bottom GPU row corresponds to
        // the visible top tip; the two remaining rows form its wider base.
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            pixels.put((byte) 0).put((byte) 0).put((byte) 0);
            pixels.put((byte) (y == 0 || (y == 2 && x == 1) ? 255 : 0));
        }
        pixels.flip();
        var upright = RenderedItemCapture.decodeAlpha(pixels, 3, 3, true);
        assertNotNull(upright);
        assertEquals(255, upright.getRGB(1, 0) >>> 24, "tip points upward in the GUI");
        assertEquals(0, upright.getRGB(0, 0) >>> 24, "top corners must be transparent");
        assertEquals(255, upright.getRGB(0, 2) >>> 24, "crystal base belongs at bottom");
    }

    @Test void flipsOversizedPictureInPictureAlphaVertically() {
        ByteBuffer pixels = ByteBuffer.allocate(2 * 2 * 4);
        // Top GPU row contains one pixel; the displayed PIP is vertically flipped.
        for (int i = 0; i < 4; i++) {
            pixels.put((byte) 0).put((byte) 0).put((byte) 0);
            pixels.put((byte) (i == 0 ? 255 : 0));
        }
        pixels.flip();
        BufferedImageAssert.assertPixelAlpha(RenderedItemCapture.decodeAlpha(pixels, 2, 2, true), 0, 1, 255);
        BufferedImageAssert.assertPixelAlpha(RenderedItemCapture.decodeAlpha(pixels, 2, 2, true), 0, 0, 0);
        BufferedImageAssert.assertPixelAlpha(RenderedItemCapture.decodeAlpha(pixels, 2, 2, false), 0, 0, 255);
    }

    private static final class BufferedImageAssert {
        static void assertPixelAlpha(java.awt.image.BufferedImage image, int x, int y, int alpha) {
            assertNotNull(image);
            assertEquals(alpha, image.getRGB(x, y) >>> 24);
        }
    }

    @Test void renderedCubeSilhouetteProducesStairStepOutlineWithoutSquareBorder() {
        ByteBuffer pixels = ByteBuffer.allocate(16 * 16 * 4);
        // Oblique cube-like projection with transparent outer corners.
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            pixels.put((byte) 140).put((byte) 140).put((byte) 140);
            boolean occupied = y >= 3 && y < 13 && x >= 3 + Math.abs(8-y)/2
                    && x < 13 - Math.abs(8-y)/2;
            pixels.put((byte) (occupied ? 255 : 0));
        }
        pixels.flip();
        var rendered = RenderedItemCapture.decodeAlpha(pixels, 16, 16);
        assertNotNull(rendered);
        int[] outline = SilhouetteRaster.rasterize(rendered, 3);
        int side = SilhouetteRaster.SIZE * 3;
        assertEquals(0, outline[0]);
        assertEquals(0, outline[side * side - 1]);
        assertTrue(java.util.Arrays.stream(outline).anyMatch(color -> (color >>> 24) > 180),
                "Actual 3D-model alpha must produce a nonempty pixel outline");
    }

    @Test void doesNotAcceptTransparentOrIncompleteReadbacks() {
        assertNull(RenderedItemCapture.decodeAlpha(ByteBuffer.allocate(64), 4, 4));
        assertNull(RenderedItemCapture.decodeAlpha(ByteBuffer.allocate(30), 4, 4));
        assertNull(RenderedItemCapture.decodeAlpha(ByteBuffer.allocate(64), 0, 4));
    }
}
