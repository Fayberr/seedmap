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

    @Test void doesNotAcceptTransparentOrIncompleteReadbacks() {
        assertNull(RenderedItemCapture.decodeAlpha(ByteBuffer.allocate(64), 4, 4));
        assertNull(RenderedItemCapture.decodeAlpha(ByteBuffer.allocate(30), 4, 4));
        assertNull(RenderedItemCapture.decodeAlpha(ByteBuffer.allocate(64), 0, 4));
    }
}
