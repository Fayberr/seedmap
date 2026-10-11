package dev.fayber.seedmap.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * World-anchored, deterministic map icon selection and aggregation.
 *
 * Screen-space ranking is NOT stable: panning changes the candidate count and
 * the local frequencies, so unchanged markers vanish or swap arbitrarily.
 * All display decisions below use fixed world cells and zoom/viewport size.
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
        if (blocksPerPixel <= 2 && base != Integer.MAX_VALUE) return base * 2;
        return base;
    }

    /**
     * A conservative fixed grid guarantees that at most maxIcons world cells
     * can have centers in the viewport, regardless of how the viewport pans.
     * Never change this size based on the current hit count.
     */
    private static double cellPixels(int width, int height, int maxIcons, double minimum) {
        if (maxIcons == Integer.MAX_VALUE) return minimum;
        double size = Math.max(minimum,
                Math.sqrt((double) Math.max(1, width) * Math.max(1, height) / maxIcons));
        while ((Math.ceil(width / size) + 1) * (Math.ceil(height / size) + 1) > maxIcons) {
            size *= 1.1;
        }
        return size;
    }

    private static double hideCellPixels(int width, int height, int density, double scale) {
        int max = budget(density, scale);
        if (max == Integer.MAX_VALUE) max = 200;
        return cellPixels(width, height, max, 19);
    }

    private static double groupCellPixels(int width, int height, int density, double scale) {
        int max = budget(density, scale);
        double radius = MarkerVisibility.clusterRadius(scale);
        if (max == Integer.MAX_VALUE) return radius;
        return cellPixels(width, height, max, radius * 1.8);
    }

    /**
     * Expand the worldgen query so every selected on-screen cell has all of
     * its competitors/members, including any just beyond the view edge.
     * Without this gutter a small pan can change a cell winner or centroid.
     */
    public static int queryPaddingPixels(int width, int height, int density,
                                         double scale, boolean hide) {
        double size = hide ? hideCellPixels(width, height, density, scale)
                : groupCellPixels(width, height, density, scale);
        return (int) Math.ceil(size) + 16;
    }

    private static final Comparator<MapMarker> MARKER_PRIORITY = Comparator
            .comparingInt((MapMarker marker) -> MarkerVisibility.isLandmark(marker.id()) ? 0 : 1)
            // Structures take precedence over ubiquitous feature markers
            // such as amethyst geodes. Feature hits carry a blockY, while
            // structure hits are 2D positions.
            .thenComparingInt(marker -> marker.blockY() == null ? 0 : 1)
            .thenComparing(marker -> marker.id().toString())
            .thenComparingInt(MapMarker::blockX)
            .thenComparingInt(MapMarker::blockZ)
            .thenComparing(marker -> marker.blockY() == null ? Integer.MIN_VALUE : marker.blockY());

    public static List<MarkerClusterer.Cluster<MapMarker>> hideInsteadOfCluster(
            List<MapMarker> markers, ToDoubleFunction<MapMarker> screenX,
            ToDoubleFunction<MapMarker> screenY, int left, int top, int width,
            int height, int density, double scale) {
        return hideWorldAligned(markers, MapMarker::blockX, MapMarker::blockZ,
                screenX, screenY, left, top, width, height, density, scale);
    }

    /**
     * Pick the same real marker from each WORLD cell, independent of which
     * other cells enter the viewport. No viewport frequency rankings, global
     * truncation or screen-relative cells. Call with a padded worldgen query.
     */
    public static List<MarkerClusterer.Cluster<MapMarker>> hideWorldAligned(
            List<MapMarker> markers, ToDoubleFunction<MapMarker> worldX,
            ToDoubleFunction<MapMarker> worldY, ToDoubleFunction<MapMarker> screenX,
            ToDoubleFunction<MapMarker> screenY, int left, int top, int width,
            int height, int density, double scale) {
        if (markers.isEmpty() || width <= 0 || height <= 0) return List.of();
        double sizePixels = hideCellPixels(width, height, density, scale);
        double worldSize = sizePixels * Math.max(0.01, scale);
        double gutter = sizePixels + 16;

        Map<Long, MapMarker> winners = new HashMap<>();
        for (MapMarker marker : markers) {
            double x = screenX.applyAsDouble(marker);
            double y = screenY.applyAsDouble(marker);
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || x < left - gutter || x >= left + width + gutter
                    || y < top - gutter || y >= top + height + gutter) continue;
            long col = (long) Math.floor(worldX.applyAsDouble(marker) / worldSize);
            long row = (long) Math.floor(worldY.applyAsDouble(marker) / worldSize);
            // World coordinates are integer-limited, and worldSize >= 0.19,
            // so both cell indices fit into a signed 32-bit pair.
            long key = (col << 32) | (row & 0xFFFFFFFFL);
            winners.merge(key, marker,
                    (previous, candidate) ->
                            MARKER_PRIORITY.compare(previous, candidate) <= 0 ? previous : candidate);
        }

        List<MapMarker> accepted = new ArrayList<>(winners.size());
        for (MapMarker selected : winners.values()) {
            double x = screenX.applyAsDouble(selected), y = screenY.applyAsDouble(selected);
            if (x >= left && x < left + width && y >= top && y < top + height) {
                accepted.add(selected);
            }
        }
        accepted.sort(MARKER_PRIORITY);
        return MarkerClusterer.cluster(accepted, screenX, screenY, 0);
    }

    /** Legacy screen-only callers keep a stable grid in screen coordinates. */
    public static <T> List<MarkerClusterer.Cluster<T>> layout(
            List<T> markers, ToDoubleFunction<T> screenX, ToDoubleFunction<T> screenY,
            int left, int top, int width, int height, int density, double scale) {
        if (markers.isEmpty()) return List.of();
        return MarkerClusterer.groupByWorldCells(markers, screenX, screenY, screenX, screenY,
                groupCellPixels(width, height, density, scale));
    }

    /**
     * Never switch clustering strategies at the hit-count threshold. The old
     * "radius when <= budget, grid when > budget" caused interior markers to
     * pop or move when a few distant POIs entered the viewport while panning.
     * Fixed world cells preserve membership for any unchanged visible region.
     */
    public static <T> List<MarkerClusterer.Cluster<T>> layoutWorldAligned(
            List<T> markers, ToDoubleFunction<T> worldX, ToDoubleFunction<T> worldY,
            ToDoubleFunction<T> screenX, ToDoubleFunction<T> screenY,
            int left, int top, int width, int height, int density, double scale) {
        if (markers.isEmpty()) return List.of();
        return MarkerClusterer.groupByWorldCells(markers, worldX, worldY, screenX, screenY,
                groupCellPixels(width, height, density, scale) * Math.max(0.01, scale));
    }
}
