package dev.fayber.seedmap.map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MapPointerGestureTest {
    @Test void simpleClickIsRecognizedOnRelease() {
        var gesture = new MapPointerGesture();
        gesture.press(10, 20);
        assertTrue(gesture.isPressed());
        assertFalse(gesture.isDragging());
        assertTrue(gesture.release(10, 20));
        assertFalse(gesture.isPressed());
    }
    @Test void dragOverAMarkerDoesNotCountAsAClick() {
        var gesture = new MapPointerGesture();
        gesture.press(60, 80);
        assertFalse(gesture.move(62, 82));
        assertTrue(gesture.move(67, 85));
        assertFalse(gesture.release(140, 200));
    }
    @Test void releaseBeyondThresholdWithoutMoveEventStillIsDrag() {
        var gesture = new MapPointerGesture();
        gesture.press(60, 80);
        assertFalse(gesture.release(90, 80));
    }
    @Test void minorHandMovementRemainsClick() {
        var gesture = new MapPointerGesture();
        gesture.press(60, 80);
        assertFalse(gesture.move(61, 81));
        assertTrue(gesture.release(62, 82));
    }
}
