package dev.fayber.seedmap.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class MarkerVisibilityTest {
    private static Identifier id(String path) { return Identifier.withDefaultNamespace(path); }

    @Test void importantLandmarksSurviveWideZoom() {
        assertTrue(MarkerVisibility.visible(id("stronghold"), 128, MarkerVisibility.BALANCED));
        assertTrue(MarkerVisibility.visible(id("ancient_city"), 128, MarkerVisibility.BALANCED));
        assertFalse(MarkerVisibility.visible(id("trial_chambers"), 64, MarkerVisibility.BALANCED));
        assertFalse(MarkerVisibility.visible(id("trial_chambers"), 16, MarkerVisibility.BALANCED));
        assertTrue(MarkerVisibility.visible(id("trial_chambers"), 8, MarkerVisibility.BALANCED));
        assertFalse(MarkerVisibility.visible(id("village_plains"), 64, MarkerVisibility.BALANCED));
    }

    @Test void densityModesAreOrderedAndFullyConfigurable() {
        Identifier chamber = id("trial_chambers");
        assertFalse(MarkerVisibility.visible(chamber, 24, MarkerVisibility.SPARSE));
        assertFalse(MarkerVisibility.visible(chamber, 24, MarkerVisibility.BALANCED));
        assertTrue(MarkerVisibility.visible(chamber, 24, MarkerVisibility.DETAILED));
        assertTrue(MarkerVisibility.visible(chamber, 4096, MarkerVisibility.ALL));
    }

    @Test void zoomFilteringDoesNotMutateConfiguredTypes() {
        Set<Identifier> enabled = Set.of(id("stronghold"), id("trial_chambers"), id("village_plains"));
        var filtered = MarkerVisibility.enabledAtScale(enabled, 64, MarkerVisibility.BALANCED);
        assertEquals(Set.of(id("stronghold")), filtered);
        assertEquals(3, enabled.size());
    }

    @Test void iconsRemainVisibleAtWideZoom() {
        assertTrue(MarkerVisibility.iconAt(64, MarkerVisibility.BALANCED));
        assertTrue(MarkerVisibility.iconAt(256, MarkerVisibility.BALANCED));
        assertFalse(MarkerVisibility.iconAt(1024, MarkerVisibility.BALANCED));
    }
}
