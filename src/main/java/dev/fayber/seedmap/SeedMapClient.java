package dev.fayber.seedmap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fayber.seedmap.config.SeedMapConfig;
import dev.fayber.seedmap.gui.SeedMapScreen;
import dev.fayber.seedmap.map.MapWorker;
import dev.fayber.seedmap.waypoint.BeamRenderer;
import dev.fayber.seedmap.waypoint.HudArrowRenderer;
import dev.fayber.seedmap.waypoint.ShareHandler;
import dev.fayber.seedmap.waypoint.WaypointState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeedMapClient implements ClientModInitializer {
    public static final String MOD_ID = "seedmap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.seedmap.open_map",
            InputConstants.KEY_M,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "seedmap")));

    private static SeedMapConfig config;

    public static SeedMapConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.save(SeedMapConfig.DEFAULT_PATH);
    }

    @Override
    public void onInitializeClient() {
        config = SeedMapConfig.load(SeedMapConfig.DEFAULT_PATH);
        KeyMappingHelper.registerKeyMapping(OPEN_MAP);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "waypoint_arrow"), HudArrowRenderer::render);

        BeamRenderer.init();
        LevelRenderEvents.COLLECT_SUBMITS.register(BeamRenderer::render);
        ShareHandler.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WaypointState.tick(client);
            while (OPEN_MAP.consumeClick()) {
                if (client.player == null) continue;
                if (client.gui.screen() instanceof SeedMapScreen) {
                    client.setScreenAndShow(null);
                } else {
                    client.setScreenAndShow(new SeedMapScreen(client));
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            WaypointState.clear();
            SeedMapScreen.resetForNewWorld();
        }));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MapWorker.shutdown());
        LOGGER.info("SeedMap loaded");
    }
}
