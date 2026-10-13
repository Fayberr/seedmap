package dev.fayber.seedmap.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class MarkerClusterer {
    public static final class Cluster<T> {
        private double sumX;
        private double sumY;
        private double groupingX;
        private double groupingY;
        private final List<T> members = new ArrayList<>();

        public double x() {
            return sumX / members.size();
        }

        public double y() {
            return sumY / members.size();
        }

        public List<T> members() {
            return members;
        }

        public int size() {
            return members.size();
        }

        private double groupingX() {
            return groupingX / members.size();
        }

        private double groupingY() {
            return groupingY / members.size();
        }

        private void add(T item, double x, double y, double gx, double gy) {
            members.add(item);
            sumX += x;
            sumY += y;
            groupingX += gx;
            groupingY += gy;
        }
    }

    private record Cell(int cx, int cy) {}

    private MarkerClusterer() {}

    public static <T> List<Cluster<T>> cluster(List<T> items,
            ToDoubleFunction<T> xOf, ToDoubleFunction<T> yOf, double radius) {
        return cluster(items, xOf, yOf, xOf, yOf, radius);
    }

    /**
     * Compare fixed world coordinates, not moving screen pixels. Only the
     * presentation centroid changes when the viewport pans. Groups therefore
     * cannot change just because a screen-space hash grid shifted.
     */
    public static <T> List<Cluster<T>> cluster(List<T> items,
            ToDoubleFunction<T> groupX, ToDoubleFunction<T> groupY,
            ToDoubleFunction<T> screenX, ToDoubleFunction<T> screenY, double radius) {
        List<Cluster<T>> clusters = new ArrayList<>();
        if (radius <= 0) {
            for (T item : items) {
                Cluster<T> cluster = new Cluster<>();
                cluster.add(item, screenX.applyAsDouble(item), screenY.applyAsDouble(item),
                        groupX.applyAsDouble(item), groupY.applyAsDouble(item));
                clusters.add(cluster);
            }
            return clusters;
        }
        Map<Cell, List<Cluster<T>>> grid = new HashMap<>();
        double radiusSq = radius * radius;
        for (T item : items) {
            double x = groupX.applyAsDouble(item);
            double y = groupY.applyAsDouble(item);
            int cx = (int) Math.floor(x / radius);
            int cy = (int) Math.floor(y / radius);
            Cluster<T> best = null;
            double bestDist = Double.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Cluster<T>> bucket = grid.get(new Cell(cx + dx, cy + dy));
                    if (bucket == null) continue;
                    for (Cluster<T> current : bucket) {
                        double ddx = current.groupingX() - x;
                        double ddy = current.groupingY() - y;
                        double distance = ddx * ddx + ddy * ddy;
                        if (distance <= radiusSq && distance < bestDist) {
                            bestDist = distance;
                            best = current;
                        }
                    }
                }
            }
            if (best == null) {
                best = new Cluster<>();
                clusters.add(best);
                grid.computeIfAbsent(new Cell(cx, cy), unused -> new ArrayList<>()).add(best);
            }
            best.add(item, screenX.applyAsDouble(item), screenY.applyAsDouble(item), x, y);
        }
        return clusters;
    }

    /**
     * World-origin-aligned cells keep each aggregate's membership fixed during
     * panning. Display its real average position, never a snapped screen-grid
     * center. This also keeps single-marker positions exact.
     */
    public static <T> List<Cluster<T>> groupByWorldCells(List<T> items,
            ToDoubleFunction<T> worldX, ToDoubleFunction<T> worldY,
            ToDoubleFunction<T> screenX, ToDoubleFunction<T> screenY,
            double cellWorldSize) {
        if (cellWorldSize <= 0 || !Double.isFinite(cellWorldSize))
            throw new IllegalArgumentException("Invalid world cell size");
        Map<Cell, Cluster<T>> cells = new LinkedHashMap<>();
        for (T item : items) {
            double gx = worldX.applyAsDouble(item);
            double gy = worldY.applyAsDouble(item);
            Cell cell = new Cell((int) Math.floor(gx / cellWorldSize),
                    (int) Math.floor(gy / cellWorldSize));
            cells.computeIfAbsent(cell, unused -> new Cluster<>())
                    .add(item, screenX.applyAsDouble(item), screenY.applyAsDouble(item), gx, gy);
        }
        return new ArrayList<>(cells.values());
    }
}
