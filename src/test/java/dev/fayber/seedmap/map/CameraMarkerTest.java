package dev.fayber.seedmap.map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CameraMarkerTest {
    @Test void noDuplicateAtPlayerPosition() {
        assertFalse(CameraMarker.isSeparate(120, -80, 120, -80));
        assertFalse(CameraMarker.isSeparate(120, -80, 120.25, -79.75));
    }

    @Test void freecamIsVisibleAtDetachedCoordinates() {
        assertTrue(CameraMarker.isSeparate(120, -80, 140, -100));
        assertTrue(CameraMarker.isSeparate(-500, 300, -497, 300));
    }

    @Test void invalidPositionsAreNeverRendered() {
        assertFalse(CameraMarker.isSeparate(0, 0, Double.NaN, 0));
        assertFalse(CameraMarker.isSeparate(0, 0, Double.POSITIVE_INFINITY, 0));
    }
}
