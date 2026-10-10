package dev.fayber.seedmap.map;

/** A second, purely visual player-like marker for a detached camera. */
public record CameraMarker(double x, double z, float yaw) {
    /** Suppress overlap when camera and real player occupy the same map location. */
    public static boolean isSeparate(double playerX, double playerZ, double cameraX, double cameraZ) {
        return Double.isFinite(cameraX) && Double.isFinite(cameraZ)
                && Double.isFinite(playerX) && Double.isFinite(playerZ)
                && Math.hypot(cameraX - playerX, cameraZ - playerZ) > 0.75;
    }
}
