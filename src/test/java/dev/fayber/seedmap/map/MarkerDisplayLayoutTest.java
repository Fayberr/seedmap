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

    @Test void allModeNeverDropsMarkers() {
        var points = new ArrayList<Dot>();
        for (int x = 0; x < 100; x++) points.add(new Dot(x * 20, 10));
        var displayed = MarkerDisplayLayout.layout(points, Dot::x, Dot::y,
                0, 0, 2100, 400, MarkerVisibility.ALL, 16);
        assertEquals(100, displayed.stream().mapToInt(MarkerClusterer.Cluster::size).sum());
    }
}
