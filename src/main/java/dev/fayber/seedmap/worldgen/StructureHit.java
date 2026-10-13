package dev.fayber.seedmap.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

public record StructureHit(Holder<Structure> structure, ChunkPos chunk, int blockX, int blockZ, double distance, boolean hasShip, BastionType bastionType) {
    public StructureHit(Holder<Structure> structure, ChunkPos chunk, int blockX, int blockZ, double distance, boolean hasShip) {
        this(structure, chunk, blockX, blockZ, distance, hasShip, null);
    }
    public StructureHit(Holder<Structure> structure, ChunkPos chunk, int blockX, int blockZ, double distance) {
        this(structure, chunk, blockX, blockZ, distance, false, null);
    }
    public static StructureHit of(RegionHit hit, int centerBlockX, int centerBlockZ) {
        double dx = hit.blockX() - centerBlockX;
        double dz = hit.blockZ() - centerBlockZ;
        return new StructureHit(hit.structure(), hit.chunk(), hit.blockX(), hit.blockZ(), Math.sqrt(dx * dx + dz * dz), hit.hasShip(), hit.bastionType());
    }
}
