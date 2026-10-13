package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompositeSilhouetteTest {
    @Test
    void outlineFollowsUnionWithoutAnInternalBorder() {
        var base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var overlay = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) base.setRGB(x, y, -1);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) overlay.setRGB(x, y, -1);
        var union = CompositeSilhouette.combine(base, overlay);
        int[] outline = SilhouetteRaster.rasterize(union, 2);
        int side = SilhouetteRaster.SIZE * 2;
        assertEquals(0, outline[(13 * 2 + 6) * side + (13 * 2 + 6)], "overlay interior has no outline");
        assertNotEquals(0, outline[(16 * 2 + 6) * side + (13 * 2 + 6)], "outline surrounds overlay extension");
        assertEquals(0, union.getRGB(63, 0), "transparent corner stays transparent");
        assertNotEquals(0, union.getRGB(63, 63), "overlay reaches bottom right");
    }
}
