package dev.fayber.seedmap.map;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Limits icons occupying the visible map by grouping screen-space cells.
 * The original structure positions are not changed; zooming into a cell
 * reveals the actual members.
 */
public final class MarkerDisplayLayout {
    private MarkerDisplayLayout() {}

    public static int budget(int density, double blocksPerPixel) {
        int base = switch (MarkerVisibility.clamp(density)) {
            case MarkerVisibility.SPARSE -> 26;
            case MarkerVisibility.DETAILED -> 100;
            case MarkerVisibility.ALL -> Integer.MAX_VALUE;
            default -> 46;
        };
        // At close zoom, individual structure icons remain more useful than aggregates.
        if (blocksPerPixel <= 2 && base != Integer.MAX_VALUE) return base * 2;
        return base;
    }

    /**
     * Chunkbase-style mode: never aggregate positions and never invent a marker at a
     * cluster centroid. Instead, hide excess individual icons deterministically.
     *
     * The existing per-structure zoom thresholds run before this pass; the
     * remaining icons are selected from real world locations. Rare types take
     * precedence, while a screen-space cell and icon budget prevent overcrowding.
     * Search results and a tracked waypoint are rendered separately by the UI.
     */
    public static List<MarkerClusterer.Cluster<MapMarker>> hideInsteadOfCluster(
            List<MapMarker> markers, ToDoubleFunction<MapMarker> screenX,
            ToDoubleFunction<MapMarker> screenY, int left, int top, int width,
            int height, int density, double scale) {
        return hideWorldAligned(markers, MapMarker::blockX, MapMarker::blockZ,
                screenX, screenY, left, top, width, height, density, scale);
    }

    public static List<MarkerClusterer.Cluster<MapMarker>> hideWorldAligned(
            List<MapMarker> markers, ToDoubleFunction<MapMarker> worldX,
            ToDoubleFunction<MapMarker> worldY, ToDoubleFunction<MapMarker> screenX,
            ToDoubleFunction<MapMarker> screenY, int left, int top, int width,
            int height, int density, double scale) {
        if (markers.isEmpty() || width <= 0 || height <= 0) return List.of();
        int budget = budget(density, scale);
        if (budget == Integer.MAX_VALUE) budget = 200;
        final int maxIcons = Math.max(1, budget);

        Map<net.minecraft.resources.Identifier, Integer> frequencies = new HashMap<>();
        for (MapMarker marker : markers) frequencies.merge(marker.id(), 1, Integer::sum);

        List<MapMarker> candidates = new ArrayList<>(markers);
        candidates.sort(Comparator
                .comparingInt((MapMarker marker) -> MarkerVisibility.isLandmark(marker.id()) ? 0 : 1)
                .thenComparingInt(marker -> frequencies.get(marker.id()))
                .thenComparing(marker -> marker.id().toString())
                .thenComparingInt(MapMarker::blockX)
                .thenComparingInt(MapMarker::blockZ));

        // One actual icon per visual cell. Moving/zooming the map changes which
        // individual structures are visible, but does not change their coordinates.
        final double cellWorldSize = Math.max(19,
                Math.ceil(Math.sqrt((double) width * height / maxIcons) * 0.92))
                * Math.max(0.01, scale);
        Set<Long> occupied = new HashSet<>();
        List<MapMarker> accepted = new ArrayList<>(Math.min(maxIcons, markers.size()));
        for (MapMarker marker : candidates) {
            double x = screenX.applyAsDouble(marker);
            double y = screenY.applyAsDouble(marker);
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || x < left || x >= left + width || y < top || y >= top + height) continue;
            int col = (int) Math.floor(worldX.applyAsDouble(marker) / cellWorldSize);
            int row = (int) Math.floor(worldY.applyAsDouble(marker) / cellWorldSize);
            long key = ((long) col << 32) | (row & 0xFFFFFFFFL);
            if (!occupied.add(key)) continue;
            accepted.add(marker);
            if (accepted.size() >= maxIcons) break;
        }
        // radius 0 guarantees singleton groups with no count badges.
        return MarkerClusterer.cluster(accepted, screenX, screenY, 0);
    }

    public static <T> List<MarkerClusterer.Cluster<T>> layout(
            List<T> markers, ToDoubleFunction<T> screenX, ToDoubleFunction<T> screenY,
            int left, int top, int width, int height, int density, double scale) {
        // Compatibility for callers with screen-space-only points.
        return layoutWorldAligned(markers, screenX, screenY, screenX, screenY,
                left, top, width, height, density, 1.0);
    }

    /**
     * Stable while dragging: grouping is performed entirely in absolute world
     * coordinates. The screen transform only affects the rendered centroid.
     * Zooming may change the chosen cell size, but panning never does.
     */
    public static <T> List<MarkerClusterer.Cluster<T>> layoutWorldAligned(
            List<T> markers, ToDoubleFunction<T> worldX, ToDoubleFunction<T> worldY,
            ToDoubleFunction<T> screenX, ToDoubleFunction<T> screenY,
            int left, int top, int width, int height, int density, double scale) {
        if (markers.isEmpty()) return List.of();
        int max = budget(density, scale);
        double radius = MarkerVisibility.clusterRadius(scale);
        double blocksPerPixel = Math.max(0.01, scale);
        if (max == Integer.MAX_VALUE || markers.size() <= max) {
            return MarkerClusterer.cluster(markers, worldX, worldY,
                    screenX, screenY, radius * blocksPerPixel);
        }

        // Ensure that ANY viewport offset intersects no more than the marker
        // budget worth of world-anchored grid cells (including partial edges).
        // No frame-to-frame adaptation to the number of visible markers.
        double cellPixels = Math.max(radius * 1.8,
                Math.sqrt((double) width * height / max) * 1.65);
        while ((Math.ceil(width / cellPixels) + 1) * (Math.ceil(height / cellPixels) + 1) > max) {
            cellPixels *= 1.1;
        }
        return MarkerClusterer.groupByWorldCells(markers, worldX, worldY,
                screenX, screenY, cellPixels * blocksPerPixel);
    }
}
