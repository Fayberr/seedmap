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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Standalone settings with an optional Modern Config frontend.
 * Neither Modern Config nor Modern GUI are required to install or run SeedMap.
 */
public final class SeedMapSettings extends Screen {
    private final Screen parent;
    private int page;
    private final List<SegmentedControl> controls = new ArrayList<>();

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
        bool.invoke(builder, "Hide crowded icons (no clustering)",
                (Supplier<Boolean>) () -> c.hideCrowdedInsteadOfClustering,
                (Consumer<Boolean>) v -> c.hideCrowdedInsteadOfClustering = v);
        bool.invoke(builder, "Experimental: faint icon outlines",
                (Supplier<Boolean>) () -> c.experimentalIconOutline,
                (Consumer<Boolean>) v -> c.experimentalIconOutline = v);
        bool.invoke(builder, "Show spawn marker",
                (Supplier<Boolean>) () -> c.showSpawnMarker,
                (Consumer<Boolean>) v -> c.showSpawnMarker = v);
        bool.invoke(builder, "Show waypoint marker",
                (Supplier<Boolean>) () -> c.showWaypointMarker,
                (Consumer<Boolean>) v -> c.showWaypointMarker = v);
        bool.invoke(builder, "Show Freecam camera",
                (Supplier<Boolean>) () -> c.showFreecamMarker,
                (Consumer<Boolean>) v -> c.showFreecamMarker = v);
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

    private void toggle(String label, BooleanSupplier getter, Runnable action, int x, int y, int width) {
        controls.add(new SegmentedControl(x, y, width, 21, List.of(
                new SegmentedControl.Segment(
                        () -> Component.literal(label + "   " + (getter.getAsBoolean() ? "On" : "Off")),
                        getter, () -> {
                            action.run();
                            SeedMapClient.saveConfig();
                        }))));
    }

    @Override
    protected void init() {
        controls.clear();
        SeedMapConfig config = SeedMapClient.config();
        int widthPx = Math.min(380, width - 28);
        int x = (width - widthPx) / 2;
        int y = 52;
        int rowHeight = Math.min(27, Math.max(21, (height - 96) / 9));
        controls.add(new SegmentedControl(x, 28, widthPx, 21, List.of(
                SegmentedControl.Segment.of(Component.literal("Map"), () -> page == 0, () -> {
                    page = 0;
                    init();
                }),
                SegmentedControl.Segment.of(Component.literal("Navigation"), () -> page == 1, () -> {
                    page = 1;
                    init();
                }))));
        if (page == 0) {
            String[] modes = {"Sparse", "Balanced", "Detailed", "All"};
            List<SegmentedControl.Segment> choices = new ArrayList<>();
            for (int i = 0; i < modes.length; i++) {
                int selected = i;
                choices.add(SegmentedControl.Segment.of(Component.literal(modes[i]),
                        () -> config.markerDensity == selected,
                        () -> {
                            config.markerDensity = selected;
                            SeedMapClient.saveConfig();
                        }));
            }
            controls.add(new SegmentedControl(x, y, widthPx, 21, choices));
            y += rowHeight;
            toggle("Hide crowded icons (no clusters)", () -> config.hideCrowdedInsteadOfClustering,
                    () -> config.hideCrowdedInsteadOfClustering = !config.hideCrowdedInsteadOfClustering,
                    x, y, widthPx);
            y += rowHeight;
            toggle("Experimental: faint icon outlines", () -> config.experimentalIconOutline,
                    () -> config.experimentalIconOutline = !config.experimentalIconOutline,
                    x, y, widthPx);
            y += rowHeight;
            toggle("Spawn marker", () -> config.showSpawnMarker,
                    () -> config.showSpawnMarker = !config.showSpawnMarker, x, y, widthPx);
            y += rowHeight;
            toggle("Waypoint marker", () -> config.showWaypointMarker,
                    () -> config.showWaypointMarker = !config.showWaypointMarker, x, y, widthPx);
            y += rowHeight;
            toggle("Freecam camera", () -> config.showFreecamMarker,
                    () -> config.showFreecamMarker = !config.showFreecamMarker, x, y, widthPx);
            y += rowHeight;
            toggle("Facing rays", () -> config.showFacing,
                    () -> config.showFacing = !config.showFacing, x, y, widthPx);
            y += rowHeight;
            toggle("Chunk / region grid", () -> config.showGrid,
                    () -> config.showGrid = !config.showGrid, x, y, widthPx);
            y += rowHeight;
            toggle("Slime chunks", () -> config.showSlimeChunks,
                    () -> config.showSlimeChunks = !config.showSlimeChunks, x, y, widthPx);
        } else {
            toggle("Waypoint beam", () -> config.showBeam,
                    () -> config.showBeam = !config.showBeam, x, y, widthPx);
            y += rowHeight;
            toggle("Persistent tile cache", () -> config.diskCache,
                    () -> config.diskCache = !config.diskCache, x, y, widthPx);
        }
        controls.add(new SegmentedControl(x, height - 31, widthPx, 22,
                List.of(SegmentedControl.Segment.of(Component.literal("Done"),
                        () -> false, this::onClose))));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0121212);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int w = Math.min(402, width - 12);
        SeedMapUi.panel(graphics, (width - w) / 2, 6, w, height - 12, 9, 0xFF1A1A1A);
        graphics.centeredText(font, SeedMapUi.text("SEEDMAP  /  SETTINGS"), width / 2, 12, 0xFFF0F0F0);
        for (SegmentedControl control : controls) control.render(graphics, font, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        for (SegmentedControl control : controls) {
            if (control.mouseClicked(click.x(), click.y(), click.button())) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void onClose() {
        SeedMapClient.saveConfig();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
