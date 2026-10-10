package dev.fayber.seedmap.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

public record RegionHit(Holder<Structure> structure, ChunkPos chunk, int blockX, int blockZ) {}
