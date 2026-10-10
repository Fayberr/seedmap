package dev.fayber.seedmap.gui;

import dev.fayber.seedmap.SeedMapClient;
import dev.fayber.seedmap.config.SeedMapConfig;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Standalone settings with an optional Modern Config frontend.
 * Neither Modern Config nor Modern GUI are required to install or run SeedMap.
 */
public final class SeedMapSettings extends Screen {
    private final Screen parent;
    private int page;

    private SeedMapSettings(Screen parent) {
        super(Component.literal("SeedMap Settings"));
        this.parent = parent;
    }

    public static Screen create(Screen parent) {
        if (FabricLoader.getInstance().isModLoaded("modernconfig")
                && FabricLoader.getInstance().isModLoaded("moderngui")) {
            try {
                return modernConfig(parent);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                SeedMapClient.LOGGER.warn("Could not open optional Modern Config, using standalone settings", error);
            }
        }
        return new SeedMapSettings(parent);
    }

    /** Reflection keeps the optional API completely absent from this mod's classpath and dependencies. */
    private static Screen modernConfig(Screen parent) throws ReflectiveOperationException {
        Class<?> screen = Class.forName("net.fayber.modernconfig.api.ModernConfigScreen");
        Object builder = screen.getMethod("builder", Component.class, Screen.class, Runnable.class)
                .invoke(null, Component.literal("SeedMap Settings"), parent,
                        (Runnable) SeedMapClient::saveConfig);
        Class<?> api = builder.getClass();
        Method tab = api.getMethod("tab", String.class);
        Method category = api.getMethod("category", String.class);
        Method bool = api.getMethod("bool", String.class, Supplier.class, Consumer.class);
        Method slider = api.getMethod("intSlider", String.class, IntSupplier.class, IntConsumer.class,
                int.class, int.class, int.class);
        SeedMapConfig c = SeedMapClient.config();

        tab.invoke(builder, "Map");
        category.invoke(builder, "Markers");
        slider.invoke(builder, "Marker detail (0 = sparse, 3 = all)",
                (IntSupplier) () -> c.markerDensity,
                (IntConsumer) v -> c.markerDensity = Math.clamp(v, 0, 3),
                0, 3, 1);
        bool.invoke(builder, "Show spawn marker",
                (Supplier<Boolean>) () -> c.showSpawnMarker,
                (Consumer<Boolean>) v -> c.showSpawnMarker = v);
        bool.invoke(builder, "Show waypoint marker",
                (Supplier<Boolean>) () -> c.showWaypointMarker,
                (Consumer<Boolean>) v -> c.showWaypointMarker = v);
        bool.invoke(builder, "Show facing rays",
                (Supplier<Boolean>) () -> c.showFacing,
                (Consumer<Boolean>) v -> c.showFacing = v);
        bool.invoke(builder, "Show chunk and region grid",
                (Supplier<Boolean>) () -> c.showGrid,
                (Consumer<Boolean>) v -> c.showGrid = v);
        bool.invoke(builder, "Highlight slime chunks",
                (Supplier<Boolean>) () -> c.showSlimeChunks,
                (Consumer<Boolean>) v -> c.showSlimeChunks = v);

        tab.invoke(builder, "Navigation");
        category.invoke(builder, "Waypoints");
        bool.invoke(builder, "Show waypoint beam",
                (Supplier<Boolean>) () -> c.showBeam,
                (Consumer<Boolean>) v -> c.showBeam = v);
        category.invoke(builder, "Performance");
        bool.invoke(builder, "Enable persistent tile cache",
                (Supplier<Boolean>) () -> c.diskCache,
                (Consumer<Boolean>) v -> c.diskCache = v);
        return (Screen) api.getMethod("build").invoke(builder);
    }

    private void toggle(String title, boolean enabled, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal((enabled ? "[ON]  " : "[OFF] ") + title), button -> {
            action.run();
            SeedMapClient.saveConfig();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build());
    }

    private void rebuildWidgets() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        SeedMapConfig config = SeedMapClient.config();
        int w = Math.min(340, width - 28);
        int x = (width - w) / 2;
        int y = 52;
        int tabW = Math.max(60, (w - 6) / 2);
        addRenderableWidget(Button.builder(Component.literal("Map"), b -> { page = 0; rebuildWidgets(); })
                .bounds(x, 29, tabW, 19).build());
        addRenderableWidget(Button.builder(Component.literal("Navigation & Cache"), b -> { page = 1; rebuildWidgets(); })
                .bounds(x + tabW + 6, 29, tabW, 19).build());
        if (page == 0) {
            addRenderableWidget(Button.builder(Component.literal("Marker detail: " + switch (config.markerDensity) {
                case 0 -> "Sparse"; case 1 -> "Balanced"; case 2 -> "Detailed"; default -> "All";
            }), b -> {
                config.markerDensity = (config.markerDensity + 1) % 4;
                SeedMapClient.saveConfig();
                rebuildWidgets();
            }).bounds(x, y, w, 20).build());
            y += 24;
            int row = y; toggle("Spawn marker", config.showSpawnMarker, x, row, w, () -> config.showSpawnMarker = !config.showSpawnMarker);
            y += 24;
            row = y; toggle("Waypoint marker", config.showWaypointMarker, x, row, w, () -> config.showWaypointMarker = !config.showWaypointMarker);
            y += 24;
            row = y; toggle("Facing rays", config.showFacing, x, row, w, () -> config.showFacing = !config.showFacing);
            y += 24;
            row = y; toggle("Chunk / region grid", config.showGrid, x, row, w, () -> config.showGrid = !config.showGrid);
            y += 24;
            row = y; toggle("Slime chunks", config.showSlimeChunks, x, row, w, () -> config.showSlimeChunks = !config.showSlimeChunks);
        } else {
            int row = y; toggle("Waypoint beam", config.showBeam, x, row, w, () -> config.showBeam = !config.showBeam);
            y += 24;
            row = y; toggle("Disk tile cache", config.diskCache, x, row, w, () -> config.diskCache = !config.diskCache);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x, height - 30, w, 21).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0121212);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, SeedMapUi.text("SEEDMAP  /  SETTINGS"), width / 2, 10, 0xFFF0F0F0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        SeedMapClient.saveConfig();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
