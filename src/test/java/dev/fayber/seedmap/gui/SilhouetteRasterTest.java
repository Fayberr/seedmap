package dev.fayber.seedmap.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SilhouetteRasterTest {
    static BufferedImage circle() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<16; y++) for (int x=0; x<16; x++)
            if (Math.hypot(x-7.5, y-7.5) < 5.6) image.setRGB(x,y,0xFFFFFFFF);
        return image;
    }

    @Test void noSquareFrameAroundCircularIcon() {
        int[] ring = SilhouetteRaster.rasterize(circle(), 3);
        int width = SilhouetteRaster.SIZE * 3;
        assertEquals(0, ring[0]);
        assertEquals(0, ring[width - 1]);
        assertEquals(0, ring[width * (width - 1)]);
        assertEquals(0, ring[width * width - 1]);
        assertTrue(java.util.Arrays.stream(ring).anyMatch(x -> (x >>> 24) > 180));
    }

    @Test void outlineIsSymmetricInEveryDirection() {
        int[] ring = SilhouetteRaster.rasterize(circle(), 4);
        int width = SilhouetteRaster.SIZE * 4;
        int mid = width/2;
        int top = sum(ring,width,mid,0,-1);
        int bottom = sum(ring,width,mid,width-1,1);
        int left = side(ring,width,0,mid,1);
        int right = side(ring,width,width-1,mid,-1);
        assertTrue(Math.abs(top-bottom) < 1200, "top/bottom imbalance");
        assertTrue(Math.abs(left-right) < 1200, "left/right imbalance");
    }

    private static int sum(int[] pixels,int width,int x,int y,int direction) {
        int total=0;
        for (int i=0;i<width;i++) {
            int at=direction<0?i:width-1-i;
            total+=(pixels[at*width+x]>>>24);
        }
        return total;
    }
    private static int side(int[] pixels,int width,int x,int y,int direction) {
        int total=0;
        for (int i=0;i<width;i++) {
            int at=direction>0?i:width-1-i;
            total+=(pixels[y*width+at]>>>24);
        }
        return total;
    }

    @Test void opacityIsUniformAcrossOpaqueEdgesAndGuiScales() {
        BufferedImage solid = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 4; y <= 11; y++)
            for (int x = 4; x <= 11; x++)
                solid.setRGB(x, y, 0xFFFFFFFF);
        for (int scale : new int[]{1, 2, 4, 6}) {
            int[] ring = SilhouetteRaster.rasterize(solid, scale);
            int width = SilhouetteRaster.SIZE * scale;
            // Four equivalent outside-facing sides have identical alpha.
            int top = ring[((4 + SilhouetteRaster.MARGIN) * scale - 1) * width
                    + (8 + SilhouetteRaster.MARGIN) * scale] >>> 24;
            int left = ring[((8 + SilhouetteRaster.MARGIN) * scale) * width
                    + (4 + SilhouetteRaster.MARGIN) * scale - 1] >>> 24;
            int right = ring[((8 + SilhouetteRaster.MARGIN) * scale) * width
                    + (12 + SilhouetteRaster.MARGIN) * scale] >>> 24;
            int bottom = ring[((12 + SilhouetteRaster.MARGIN) * scale) * width
                    + (8 + SilhouetteRaster.MARGIN) * scale] >>> 24;
            assertEquals(top, left, "top-left width mismatch at GUI scale " + scale);
            assertEquals(top, right, "top-right width mismatch at GUI scale " + scale);
            assertEquals(top, bottom, "top-bottom width mismatch at GUI scale " + scale);
            assertTrue(top >= 180, "outline should be bright but thin");
            assertEquals(0, ring[0], "canvas should remain transparent");
        }
    }

    @Test void emptyIconCostsNoDrawnPixels() {
        var empty=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        assertEquals(0,java.util.Arrays.stream(SilhouetteRaster.rasterize(empty,4))
                .filter(pixel -> (pixel>>>24)!=0).count());
    }
}
