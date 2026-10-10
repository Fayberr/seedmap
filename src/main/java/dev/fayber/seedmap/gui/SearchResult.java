package dev.fayber.seedmap.gui;

import dev.fayber.seedmap.worldgen.BiomeColors;
import dev.fayber.seedmap.worldgen.BiomeHit;
import dev.fayber.seedmap.worldgen.SeedWorld;
import dev.fayber.seedmap.worldgen.StructureHit;
import net.minecraft.resources.Identifier;

public record SearchResult(String name, int x, int z, double distance, Identifier structureId, int color) {
    public static SearchResult of(StructureHit hit) {
        Identifier id = SeedWorld.idOf(hit.structure());
        return new SearchResult(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), hit.distance(), id, StructureIcons.colorFor(id));
    }

    public static SearchResult of(dev.fayber.seedmap.worldgen.FeatureFinder.FeatureHit hit, int centerX, int centerZ) {
        double dx = hit.blockX() - centerX;
        double dz = hit.blockZ() - centerZ;
        return new SearchResult(StructureIcons.displayName(hit.featureId()) + "  y " + hit.blockY(), hit.blockX(), hit.blockZ(),
                Math.sqrt(dx * dx + dz * dz), hit.featureId(), StructureIcons.colorFor(hit.featureId()));
    }

    public static SearchResult of(BiomeHit hit) {
        Identifier id = SeedWorld.idOf(hit.biome());
        return new SearchResult(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), hit.distance(), null, BiomeColors.colorOf(id));
    }
}
