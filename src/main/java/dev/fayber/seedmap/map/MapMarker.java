package dev.fayber.seedmap.map;

import dev.fayber.seedmap.worldgen.FeatureFinder;
import dev.fayber.seedmap.worldgen.RegionHit;
import dev.fayber.seedmap.worldgen.SeedWorld;
import net.minecraft.resources.Identifier;

public record MapMarker(Identifier id, int blockX, int blockZ, Integer blockY) {
    public static MapMarker of(RegionHit hit) {
        return new MapMarker(hit.bastionType() != null ? hit.bastionType().markerId() : hit.hasShip() ? dev.fayber.seedmap.gui.StructureIcons.END_CITY_WITH_SHIP : SeedWorld.idOf(hit.structure()), hit.blockX(), hit.blockZ(), null);
    }

    public static MapMarker of(FeatureFinder.FeatureHit hit) {
        return new MapMarker(hit.featureId(), hit.blockX(), hit.blockZ(), hit.blockY());
    }
}
