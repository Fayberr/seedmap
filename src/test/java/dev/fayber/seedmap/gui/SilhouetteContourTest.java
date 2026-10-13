package dev.fayber.seedmap.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SilhouetteContourTest {
    private static BufferedImage disk() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dist = Math.hypot(x - 7.5, y - 7.5);
                if (dist < 5.4) image.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        return image;
    }

    @Test void circleIsOutlinedAsCircleNotAsSquare() {
        var contour = SilhouetteContour.trace(disk());
        assertFalse(contour.isEmpty());
        for (var s : contour) {
            for (float[] pt : new float[][]{{s.x1(), s.y1()}, {s.x2(), s.y2()}}) {
                float d = (float) Math.hypot(pt[0] - 8, pt[1] - 8);
                assertTrue(d >= 4.0 && d < 7.2, "outline follows the circle edge, not icon bounds: " + d);
            }
        }
    }

    @Test void transparentIconHasNoOutline() {
        assertTrue(SilhouetteContour.trace(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)).isEmpty());
    }

    @Test void outputIsDeterministic() {
        assertEquals(SilhouetteContour.trace(disk()), SilhouetteContour.trace(disk()));
    }
}
