package dev.fayber.seedmap.map;

import java.util.List;
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
