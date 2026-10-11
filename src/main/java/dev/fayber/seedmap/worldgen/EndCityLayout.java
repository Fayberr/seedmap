package dev.fayber.seedmap.worldgen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;

/** Replays vanilla pieces, including collision rejection, rather than guessing ship probability. */
final class EndCityLayout {
    private static final class Templates {
        static final StructureTemplateManager INSTANCE = create();

        private static StructureTemplateManager create() {
            try {
                Path temporary = Files.createTempDirectory("seedmap-templates-");
                StructureTemplateManager manager;
                try (var access = LevelStorageSource.createDefault(temporary).createAccess("templates")) {
                    var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                            List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
                    manager = new StructureTemplateManager(resources, access, DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
                }
                // Only vanilla resource templates are read; no generated world is retained.
                try (var paths = Files.walk(temporary)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
                return manager;
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot load End City templates", e);
            }
        }
    }

    static synchronized boolean hasShip(BlockPos start, Rotation rotation, RandomSource random) {
        // Vanilla's recursive piece generators contain shared mutable state.
        StructureTemplateManager templates = Templates.INSTANCE;
        List<StructurePiece> pieces = new ArrayList<>();
        EndCityPieces.startHouseTower(templates, start, rotation, pieces, random);
        var ship = templates.getOrCreate(Identifier.withDefaultNamespace("end_city/ship"));
        return pieces.stream().anyMatch(piece -> piece instanceof TemplateStructurePiece template && template.template() == ship);
    }
}
