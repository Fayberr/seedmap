package dev.fayber.seedmap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.fayber.seedmap.SeedMapClient;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

public final class SeedMapConfig {
    public static final Path DEFAULT_PATH = FabricLoader.getInstance().getConfigDir().resolve("seedmap.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, String> seeds = new LinkedHashMap<>();
    public List<String> enabledStructures = new ArrayList<>();
    public int lastRadius = 5000;
    public boolean showBeam = true;
    public boolean showSlimeChunks = false;
    public boolean diskCache = true;
    public boolean showGrid = false;
    public boolean showFacing = false;
    public boolean showSpawnMarker = true;
    public boolean showWaypointMarker = true;
    public int markerDensity = 1;
    public int biomeY = 64;

    public static SeedMapConfig defaults() {
        SeedMapConfig config = new SeedMapConfig();
        config.enabledStructures.addAll(List.of(
                "minecraft:village_plains",
                "minecraft:village_desert",
                "minecraft:village_savanna",
                "minecraft:village_snowy",
                "minecraft:village_taiga",
                "minecraft:stronghold",
                "minecraft:mansion",
                "minecraft:monument",
                "minecraft:ancient_city",
                "minecraft:trial_chambers"));
        return config;
    }

    public static SeedMapConfig load(Path path) {
        if (!Files.exists(path)) {
            return defaults();
        }
        try {
            String json = Files.readString(path);
            SeedMapConfig config = GSON.fromJson(json, SeedMapConfig.class);
            if (config == null) {
                return defaults();
            }
            if (config.seeds == null) config.seeds = new LinkedHashMap<>();
            if (config.enabledStructures == null) config.enabledStructures = defaults().enabledStructures;
            if (config.lastRadius <= 0) config.lastRadius = 5000;
            config.markerDensity = Math.clamp(config.markerDensity, 0, 3);
            return config;
        } catch (IOException | JsonParseException e) {
            SeedMapClient.LOGGER.warn("Could not read {}, using defaults", path, e);
            return defaults();
        }
    }

    public void save(Path path) {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            SeedMapClient.LOGGER.warn("Could not write {}", path, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException suppressed) {
                SeedMapClient.LOGGER.warn("Could not remove {}", tmp, suppressed);
            }
        }
    }

    public Optional<String> seedFor(String address) {
        return Optional.ofNullable(seeds.get(address));
    }

    public void putSeed(String address, String seedText) {
        seeds.put(address, seedText);
    }
}
