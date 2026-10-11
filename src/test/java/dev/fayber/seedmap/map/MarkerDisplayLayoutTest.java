package dev.fayber.seedmap.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkerDisplayLayoutTest {
    record Dot(int x, int y) {}

    @Test void denseNetherLikeViewStaysUnderVisualBudget() {
        List<Dot> points = new ArrayList<>();
        // Approximate a very crowded Nether screenshot: hundreds of hits on screen.
        for (int x = 0; x < 1120; x += 18)
            for (int z = 0; z < 690; z += 18) points.add(new Dot(x, z));
        var laidOut = MarkerDisplayLayout.layout(points, Dot::x, Dot::y,
                0, 0, 1120, 690, MarkerVisibility.BALANCED, 16);
        assertTrue(laidOut.size() <= MarkerDisplayLayout.budget(MarkerVisibility.BALANCED, 16),
                "visible marker groups: " + laidOut.size());
        assertEquals(points.size(), laidOut.stream().mapToInt(MarkerClusterer.Cluster::size).sum(),
                "all original structure hits remain available when zooming into a group");
    }

    @Test void sparseNearbyPointsRemainAtRealPositions() {
        var points = List.of(new Dot(10, 10), new Dot(220, 100), new Dot(450, 300));
        var displayed = MarkerDisplayLayout.layout(points, Dot::x, Dot::y,
                0, 0, 900, 600, MarkerVisibility.BALANCED, 2);
        assertEquals(3, displayed.size());
        assertEquals(10, displayed.get(0).x(), 0.01);
    }

    @Test void directHideDoesNotCreateClustersOrInventPositions() {
        List<MapMarker> markers = new ArrayList<>();
        var fortress = net.minecraft.resources.Identifier.withDefaultNamespace("fortress");
        for (int i = 0; i < 400; i++) {
            markers.add(new MapMarker(fortress, 10 + (i % 40) * 8, 20 + (i / 40) * 8, null));
        }
        var shown = MarkerDisplayLayout.hideInsteadOfCluster(
                markers, MapMarker::blockX, MapMarker::blockZ,
                0, 0, 400, 250, MarkerVisibility.BALANCED, 4);
        assertFalse(shown.isEmpty());
        assertTrue(shown.size() <= MarkerDisplayLayout.budget(MarkerVisibility.BALANCED, 4));
        assertTrue(shown.size() < markers.size());
        assertTrue(shown.stream().allMatch(c -> c.size() == 1), "no aggregation in direct-hide mode");
        assertTrue(shown.stream().allMatch(c -> {
            MapMarker m = c.members().get(0);
            return c.x() == m.blockX() && c.y() == m.blockZ();
        }), "rendered positions must be actual structure coordinates");
    }

    @Test void directHideIsDeterministicRegardlessOfDiscoveryOrder() {
        var id = net.minecraft.resources.Identifier.withDefaultNamespace("bastion_remnant");
        List<MapMarker> points = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            points.add(new MapMarker(id, (i * 19) % 500, (i * 47) % 250, null));
        }
        var forward = MarkerDisplayLayout.hideInsteadOfCluster(points,
                MapMarker::blockX, MapMarker::blockZ, 0, 0, 500, 250,
                MarkerVisibility.BALANCED, 4);
        java.util.Collections.reverse(points);
        var reversed = MarkerDisplayLayout.hideInsteadOfCluster(points,
                MapMarker::blockX, MapMarker::blockZ, 0, 0, 500, 250,
                MarkerVisibility.BALANCED, 4);
        assertEquals(forward.stream().map(c -> c.members().get(0)).toList(),
                reversed.stream().map(c -> c.members().get(0)).toList());
    }

    @Test void denseGroupsRemainAtStableWorldPositionsDuringPan() {
        List<Dot> world = new ArrayList<>();
        for (int x = 40; x < 920; x += 26) {
            for (int y = 25; y < 620; y += 28) world.add(new Dot(x, y));
        }
        var before = MarkerDisplayLayout.layoutWorldAligned(world,
                Dot::x, Dot::y, Dot::x, Dot::y,
                0, 0, 960, 650, MarkerVisibility.BALANCED, 4);
        var after = MarkerDisplayLayout.layoutWorldAligned(world,
                Dot::x, Dot::y, p -> p.x() + 17, p -> p.y() - 13,
                0, 0, 960, 650, MarkerVisibility.BALANCED, 4);
        assertEquals(before.size(), after.size(), "panning must never change cluster membership");
        assertTrue(before.size() <= MarkerDisplayLayout.budget(MarkerVisibility.BALANCED, 4));
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).members(), after.get(i).members(),
                    "cluster membership changed just because the screen moved");
            assertEquals(before.get(i).x() + 17, after.get(i).x(), 0.00001);
            assertEquals(before.get(i).y() - 13, after.get(i).y(), 0.00001);
        }
    }

    @Test void nearbyWorldClustersRemainStableAcrossScreenCellBoundaries() {
        List<Dot> world = List.of(new Dot(9, 20), new Dot(15, 21),
                new Dot(120, 100), new Dot(126, 104));
        var before = MarkerDisplayLayout.layoutWorldAligned(world,
                Dot::x, Dot::y, Dot::x, Dot::y,
                0, 0, 500, 400, MarkerVisibility.ALL, 4);
        var after = MarkerDisplayLayout.layoutWorldAligned(world,
                Dot::x, Dot::y, p -> p.x() + 73, p -> p.y() + 21,
                0, 0, 500, 400, MarkerVisibility.ALL, 4);
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).members(), after.get(i).members());
            assertEquals(before.get(i).x() + 73, after.get(i).x(), 0.00001);
        }
    }

    @Test void hiddenMarkersHaveStableWorldCellSelectionDuringPan() {
        var id = net.minecraft.resources.Identifier.withDefaultNamespace("shipwreck");
        List<MapMarker> markers = new ArrayList<>();
        for (int x = 100; x <= 500; x += 18)
            for (int y = 100; y <= 350; y += 18)
                markers.add(new MapMarker(id, x, y, null));
        var before = MarkerDisplayLayout.hideWorldAligned(markers,
                MapMarker::blockX, MapMarker::blockZ, MapMarker::blockX, MapMarker::blockZ,
                0, 0, 700, 500, MarkerVisibility.BALANCED, 4);
        var after = MarkerDisplayLayout.hideWorldAligned(markers,
                MapMarker::blockX, MapMarker::blockZ,
                m -> m.blockX() + 11, m -> m.blockZ() + 7,
                0, 0, 700, 500, MarkerVisibility.BALANCED, 4);
        assertEquals(before.stream().map(c -> c.members().get(0)).toList(),
                after.stream().map(c -> c.members().get(0)).toList());
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).x() + 11, after.get(i).x(), 0.00001);
        }
    }

    @Test void viewportFrequencyChangesCannotSuppressUnchangedInteriorMarkers() {
        var amethyst = net.minecraft.resources.Identifier.withDefaultNamespace("amethyst_geode");
        var boat = net.minecraft.resources.Identifier.withDefaultNamespace("shipwreck");
        List<MapMarker> world = new ArrayList<>();
        // Densely populated map: much more than the balanced-mode budget.
        for (int x = -250; x <= 1300; x += 34) {
            for (int z = -200; z <= 900; z += 38) {
                world.add(new MapMarker(amethyst, x, z, null));
            }
        }
        world.add(new MapMarker(boat, 380, 255, null));
        int width = 820, height = 590, density = MarkerVisibility.BALANCED;
        double scale = 4;
        int gutter = MarkerDisplayLayout.queryPaddingPixels(width, height, density, scale, true);
        // The underlying query is recomputed as the viewport pans by 27px.
        List<MapMarker> first = world.stream().filter(m ->
                m.blockX() >= -gutter * scale && m.blockX() < (width + gutter) * scale
                && m.blockZ() >= -gutter * scale && m.blockZ() < (height + gutter) * scale).toList();
        List<MapMarker> second = world.stream().filter(m ->
                m.blockX() >= (-gutter - 27) * scale && m.blockX() < (width + gutter - 27) * scale
                && m.blockZ() >= (-gutter + 11) * scale && m.blockZ() < (height + gutter + 11) * scale).toList();
        var before = MarkerDisplayLayout.hideWorldAligned(first,
                MapMarker::blockX, MapMarker::blockZ,
                m -> m.blockX() / scale, m -> m.blockZ() / scale,
                0, 0, width, height, density, scale);
        var after = MarkerDisplayLayout.hideWorldAligned(second,
                MapMarker::blockX, MapMarker::blockZ,
                m -> m.blockX() / scale + 27, m -> m.blockZ() / scale - 11,
                0, 0, width, height, density, scale);

        var innerBefore = before.stream().map(c -> c.members().get(0))
                .filter(m -> m.blockX() / scale > 150 && m.blockX() / scale < 600
                          && m.blockZ() / scale > 145 && m.blockZ() / scale < 425)
                .toList();
        var innerAfter = after.stream().map(c -> c.members().get(0))
                .filter(m -> m.blockX() / scale > 150 && m.blockX() / scale < 600
                          && m.blockZ() / scale > 145 && m.blockZ() / scale < 425)
                .toList();
        assertFalse(innerBefore.isEmpty(), "test needs stable interior markers");
        assertEquals(innerBefore, innerAfter,
                "moving the query must not replace or hide markers still deep within the map");
        assertTrue(before.size() <= MarkerDisplayLayout.budget(density, scale));
        assertTrue(after.size() <= MarkerDisplayLayout.budget(density, scale));
    }

    @Test void crossingTheOldClusterBudgetCannotChangeExistingGroups() {
        List<Dot> points = new ArrayList<>();
        for (int x = 30; x < 700; x += 110) {
            for (int y = 50; y < 490; y += 90) points.add(new Dot(x, y));
        }
        var original = MarkerDisplayLayout.layoutWorldAligned(points,
                Dot::x, Dot::y, Dot::x, Dot::y,
                0, 0, 840, 540, MarkerVisibility.BALANCED, 4);
        // Two extra distant markers must not switch existing markers to a
        // different clustering algorithm or move the old centroids.
        var larger = new ArrayList<>(points);
        larger.add(new Dot(-180, -120));
        larger.add(new Dot(1000, 870));
        var expanded = MarkerDisplayLayout.layoutWorldAligned(larger,
                Dot::x, Dot::y, Dot::x, Dot::y,
                0, 0, 840, 540, MarkerVisibility.BALANCED, 4);
        for (var cluster : original) {
            assertTrue(expanded.stream().anyMatch(c ->
                    c.members().equals(cluster.members())
                    && Math.abs(c.x() - cluster.x()) < 0.001
                    && Math.abs(c.y() - cluster.y()) < 0.001),
                    "distant candidates must not affect existing world-cell groups");
        }
    }

    @Test void allModeNeverDropsMarkers() {
        var points = new ArrayList<Dot>();
        for (int x = 0; x < 100; x++) points.add(new Dot(x * 20, 10));
        var displayed = MarkerDisplayLayout.layout(points, Dot::x, Dot::y,
                0, 0, 2100, 400, MarkerVisibility.ALL, 16);
        assertEquals(100, displayed.stream().mapToInt(MarkerClusterer.Cluster::size).sum());
    }
}
