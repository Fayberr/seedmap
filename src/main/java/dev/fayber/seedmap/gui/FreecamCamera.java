package dev.fayber.seedmap.gui;

import dev.fayber.seedmap.map.CameraMarker;
import java.lang.reflect.Method;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Reads detached camera positions without requiring any Freecam implementation.
 *
 * MinecraftFreecam and other entity-based freecams replace the camera entity.
 * WI Freecam instead manipulates the Minecraft render camera directly, so we
 * detect its enabled state through a guarded optional reflection bridge.
 */
public final class FreecamCamera {
    private static final Object WI_INSTANCE;
    private static final Method WI_IS_ENABLED;

    static {
        Object instance = null;
        Method enabled = null;
        if (FabricLoader.getInstance().isModLoaded("wi_freecam")) {
            try {
                Class<?> clazz = Class.forName("net.wimods.freecam.WiFreecam");
                instance = clazz.getField("INSTANCE").get(null);
                enabled = clazz.getMethod("isEnabled");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // WI Freecam is optional, and its API may change in future versions.
            }
        }
        WI_INSTANCE = instance;
        WI_IS_ENABLED = enabled;
    }

    private FreecamCamera() {}

    private static boolean wiFreecamActive() {
        if (WI_INSTANCE == null || WI_IS_ENABLED == null) return false;
        try {
            return Boolean.TRUE.equals(WI_IS_ENABLED.invoke(WI_INSTANCE));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static Optional<CameraMarker> locate(Minecraft client) {
        if (client.player == null || client.level == null) return Optional.empty();

        Entity cameraEntity = client.getCameraEntity();
        boolean distinctEntity = cameraEntity != null && cameraEntity != client.player
                && cameraEntity.level().dimension().equals(client.player.level().dimension());
        boolean wiActive = wiFreecamActive();
        if (!distinctEntity && !wiActive) return Optional.empty();

        Vec3 position;
        float yaw;
        if (distinctEntity) {
            position = cameraEntity.position();
            yaw = cameraEntity.getYRot();
        } else {
            // WI Freecam does not replace the camera entity.
            var renderCamera = client.gameRenderer.mainCamera();
            position = renderCamera.position();
            yaw = renderCamera.yRot();
        }

        if (!CameraMarker.isSeparate(client.player.getX(), client.player.getZ(), position.x, position.z)) {
            return Optional.empty();
        }
        return Optional.of(new CameraMarker(position.x, position.z, yaw));
    }
}
