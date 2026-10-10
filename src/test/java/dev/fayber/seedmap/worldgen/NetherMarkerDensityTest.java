package dev.fayber.seedmap.worldgen;

import dev.fayber.seedmap.map.MapMarker;
import dev.fayber.seedmap.map.MarkerDisplayLayout;
import dev.fayber.seedmap.map.MarkerVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.core.Holder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Samples real vanilla structure placement from reproducibly random Nether seeds.
 * Results are printed to CI to make future changes to map clutter measurable.
 */
class NetherMarkerDensityTest {
    private static final int VIEW_WIDTH = 720;
    private static final int VIEW_HEIGHT = 420;

    @Test
    void netherStructureVisibilityAcrossZoomsAndRandomSeeds() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
        Random seeds = new Random(0x5EED5EEDL);
        int samples = 0;

        for (int sample = 0; sample < 2; sample++) {
            long seed = seeds.nextLong();
            int centerX = seeds.nextInt(-12000, 12001);
            int centerZ = seeds.nextInt(-12000, 12001);
            SeedWorld world = SeedWorld.create(seed, Dimension.NETHER);
            Set<Identifier> enabled = world.allStructures().stream()
                    .map(SeedWorld::idOf).collect(Collectors.toSet());

            for (int zoom : new int[]{4, 8, 16}) {
                List<MapMarker> all = new ArrayList<>();
                int minX = centerX - VIEW_WIDTH * zoom / 2;
                int maxX = centerX + VIEW_WIDTH * zoom / 2;
                int minZ = centerZ - VIEW_HEIGHT * zoom / 2;
                int maxZ = centerZ + VIEW_HEIGHT * zoom / 2;

                for (Holder<StructureSet> set : world.allStructureSets()) {
                    if (!(set.value().placement() instanceof RandomSpreadStructurePlacement placement)) continue;
                    int spacingBlocks = placement.spacing() * 16;
                    for (int rx = Math.floorDiv(minX, spacingBlocks); rx <= Math.floorDiv(maxX, spacingBlocks); rx++) {
                        for (int rz = Math.floorDiv(minZ, spacingBlocks); rz <= Math.floorDiv(maxZ, spacingBlocks); rz++) {
                            world.structureInRegionConfirmed(set, rx, rz).ifPresent(hit -> {
                                if (hit.blockX() >= minX && hit.blockX() <= maxX
                                        && hit.blockZ() >= minZ && hit.blockZ() <= maxZ) {
                                    all.add(MapMarker.of(hit));
                                }
                            });
                        }
                    }
                }
                Set<Identifier> filtered = MarkerVisibility.enabledAtScale(
                        enabled, zoom, MarkerVisibility.BALANCED);
                List<MapMarker> visible = all.stream().filter(m -> filtered.contains(m.id())).toList();
                var icons = MarkerDisplayLayout.layout(visible,
                        marker -> VIEW_WIDTH / 2.0 + (marker.blockX() - centerX) / (double) zoom,
                        marker -> VIEW_HEIGHT / 2.0 + (marker.blockZ() - centerZ) / (double) zoom,
                        0, 0, VIEW_WIDTH, VIEW_HEIGHT, MarkerVisibility.BALANCED, zoom);
                int displayed = icons.size();
                int budget = MarkerDisplayLayout.budget(MarkerVisibility.BALANCED, zoom);
                System.out.printf("NETHER_DENSITY seed=%d center=%d,%d zoom=%d confirmed=%d postFilter=%d onScreen=%d budget=%d%n",
                        seed, centerX, centerZ, zoom, all.size(), visible.size(), displayed, budget);

                assertTrue(displayed <= budget, "too many map icons at zoom " + zoom);
                assertEquals(visible.size(), icons.stream().mapToInt(c -> c.size()).sum(),
                        "marker grouping cannot lose source POIs");
                samples++;
            }
        }
        assertEquals(6, samples);
    }
}
