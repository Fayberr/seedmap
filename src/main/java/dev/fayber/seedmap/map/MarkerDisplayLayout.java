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
        final int cellSize = Math.max(19,
                (int) Math.ceil(Math.sqrt((double) width * height / maxIcons) * 0.92));
        Set<Long> occupied = new HashSet<>();
        List<MapMarker> accepted = new ArrayList<>(Math.min(maxIcons, markers.size()));
        for (MapMarker marker : candidates) {
            double x = screenX.applyAsDouble(marker);
            double y = screenY.applyAsDouble(marker);
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || x < left || x >= left + width || y < top || y >= top + height) continue;
            int col = (int) Math.floor((x - left) / cellSize);
            int row = (int) Math.floor((y - top) / cellSize);
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
        if (markers.isEmpty()) return List.of();
        int max = budget(density, scale);
        double radius = MarkerVisibility.clusterRadius(scale);
        if (max == Integer.MAX_VALUE || markers.size() <= max) {
            return MarkerClusterer.cluster(markers, screenX, screenY, radius);
        }

        // Snap only the displayed aggregate center; the members retain world coordinates.
        double cell = Math.max(radius * 1.8, Math.sqrt((double) width * height / max) * 1.65);
        List<MarkerClusterer.Cluster<T>> layout;
        do {
            double size = cell;
            layout = MarkerClusterer.cluster(markers,
                    marker -> left + (Math.floor((screenX.applyAsDouble(marker) - left) / size) + 0.5) * size,
                    marker -> top + (Math.floor((screenY.applyAsDouble(marker) - top) / size) + 0.5) * size,
                    0.1);
            cell *= 1.25;
        } while (layout.size() > max && cell < Math.max(width, height) * 4.0);
        return layout;
    }
}
