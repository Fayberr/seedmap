package dev.fayber.seedmap.gui;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Optional Modern GUI adapter. No class from Modern GUI is linked at load time.
 * The fallback uses Minecraft's drawing APIs so SeedMap is independently usable.
 */
public final class SeedMapUi {
    private static final Method ROUND_RECT;
    private static final Method UI_COMPONENT;

    static {
        Method shape = null;
        Method font = null;
        if (FabricLoader.getInstance().isModLoaded("moderngui")) {
            try {
                Class<?> ui = Class.forName("net.fayber.moderngui.render.Ui");
                shape = ui.getMethod("roundRect", GuiGraphicsExtractor.class, float.class, float.class,
                        float.class, float.class, float.class, int.class);
                font = ui.getMethod("ui", Component.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Leave the standalone renderer enabled.
            }
        }
        ROUND_RECT = shape;
        UI_COMPONENT = font;
    }

    private SeedMapUi() {}

    public static Component text(Component value) {
        if (UI_COMPONENT != null) {
            try {
                return (Component) UI_COMPONENT.invoke(null, value);
            } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        return value;
    }

    public static Component text(String value) {
        return text(Component.literal(value));
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             float radius, int color) {
        if (width <= 0 || height <= 0) return;
        if (ROUND_RECT != null) {
            try {
                ROUND_RECT.invoke(null, graphics, (float) x, (float) y,
                        (float) width, (float) height, radius, color);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        int r = Math.min(Math.min(width / 3, height / 3), Math.max(0, Math.round(radius)));
        if (r < 2) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - (r - i - 0.5) * (r - i - 0.5))));
            graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }
}
