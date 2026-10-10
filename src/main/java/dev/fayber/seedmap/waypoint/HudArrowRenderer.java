package dev.fayber.seedmap.waypoint;

import dev.fayber.seedmap.gui.NavigationPointer;
import dev.fayber.seedmap.worldgen.Dimension;
import net.minecraft.network.chat.Component;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudArrowRenderer {
    private HudArrowRenderer() {}

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return;
        Waypoint waypoint = WaypointState.get().orElse(null);
        if (waypoint == null) return;
        if (!WaypointState.playerInDimension(client, waypoint)) {
            Component label = Component.translatable("seedmap.waypoint.other_dimension", waypoint.name(),
                    Dimension.fromLevelId(waypoint.dimension()).displayName());
            context.centeredText(client.font, label, context.guiWidth() / 2, 18, 0xFFCCCCCC);
            return;
        }

        double px = client.player.getX();
        double pz = client.player.getZ();
        float yaw = client.player.getViewYRot(tickCounter.getGameTimeDeltaPartialTick(true));
        float relative = Bearing.relativeDegrees(px, pz, yaw, waypoint.x() + 0.5, waypoint.z() + 0.5);
        int distance = (int) Math.round(Bearing.distance(px, pz, waypoint.x() + 0.5, waypoint.z() + 0.5));

        int centerX = context.guiWidth() / 2;
        int arrowY = 18;
        NavigationPointer.draw(context, centerX, arrowY, (float) Math.toRadians(relative), 0xFFFFFFFF);

        String label = waypoint.name() + "  " + distance + " m";
        context.centeredText(client.font, label, centerX, arrowY + 12, 0xFFFFFFFF);
    }
}
