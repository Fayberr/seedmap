package dev.fayber.seedmap.map;

/**
 * Captures a map pointer press without activating points of interest until a clean release.
 * A move beyond the configured threshold always becomes a pan, even when started over a marker.
 */
public final class MapPointerGesture {
    public static final double DRAG_THRESHOLD_PX = 5.0;
    private boolean pressed;
    private boolean dragged;
    private double originX;
    private double originY;

    public void press(double x, double y) {
        pressed = true;
        dragged = false;
        originX = x;
        originY = y;
    }

    public boolean isPressed() { return pressed; }
    public boolean isDragging() { return pressed && dragged; }

    public boolean move(double x, double y) {
        if (!pressed) return false;
        if (Math.hypot(x - originX, y - originY) >= DRAG_THRESHOLD_PX) dragged = true;
        return dragged;
    }

    /** Returns true only for a click whose press and release were close together. */
    public boolean release(double x, double y) {
        if (!pressed) return false;
        move(x, y);
        boolean clicked = !dragged;
        cancel();
        return clicked;
    }

    public void cancel() {
        pressed = false;
        dragged = false;
    }
}
