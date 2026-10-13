package dev.fayber.seedmap.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fayber.seedmap.SeedMapClient;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

/**
 * Caches an alpha-silhouette as one small GPU texture per structure and GUI scale.
 * The previous renderer submitted thousands of fill calls per icon every frame.
 *
 * The alpha sources are read from Minecraft's live resource manager, so custom
 * PNGs replaced by a resource pack are automatically picked up after reload.
 * Flat items can use their resolved resource-pack PNGs immediately.
 * For 3D models, the actual rendered GUI-item alpha is captured and cached.
 */
public final class IconSilhouette {
    private record Entry(Identifier texture, int side, int guiScale) {}
    private record Key(Identifier structure, int scale) {}

    private static final Map<Key, Optional<Entry>> CACHE = new HashMap<>();
    private static ResourceManager lastResources;
    private static int generation;

    private IconSilhouette() {}

    public static void draw(GuiGraphicsExtractor graphics, Identifier structure, int x, int y,
                            Identifier customPng, String mapSprite, Item item) {
        if (structure == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) return;
        ResourceManager manager = client.getResourceManager();
        if (manager != lastResources) {
            clearCache();
            lastResources = manager;
        }

        int scale = Math.clamp((int) Math.round(client.getWindow().getGuiScale()), 1, 8);
        // A 3D model's one-off GPU capture is registered around the
        // subsequent context.item() call by StructureIcons.
        Key key = new Key(structure, scale);
        Optional<Entry> cached = CACHE.computeIfAbsent(key,
                unused -> makeTexture(client, manager, structure, scale, customPng, mapSprite, item));
        if (cached.isEmpty()) return;
        Entry entry = cached.get();

        // One textured draw instead of hundreds/thousands of rectangles per icon.
        // The cached texture contains a constant-width physical-pixel outline.
        graphics.pose().pushMatrix();
        graphics.pose().scale(1f / scale, 1f / scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, entry.texture(),
                (x - SilhouetteRaster.MARGIN) * scale,
                (y - SilhouetteRaster.MARGIN) * scale,
                0, 0, entry.side(), entry.side(),
                entry.side(), entry.side());
        graphics.pose().popMatrix();
    }

    private static Optional<Entry> makeTexture(Minecraft client, ResourceManager manager,
                    Identifier structure, int guiScale, Identifier customPng,
                    String mapSprite, Item item) {
        BufferedImage source = source(manager, StructureIcons.baseIcon(structure), customPng, mapSprite, item);
        Item overlay = StructureIcons.overlayItem(structure);
        if (overlay != null) {
            BufferedImage top = source(manager, StructureIcons.overlayKey(overlay), null, null, overlay);
            source = CompositeSilhouette.combine(source, top);
        }
        if (source == null) return Optional.empty();

        int[] outline = SilhouetteRaster.rasterize(source, guiScale);
        boolean hasPixels = false;
        for (int pixel : outline) {
            if ((pixel >>> 24) != 0) { hasPixels = true; break; }
        }
        if (!hasPixels) return Optional.empty();

        int side = SilhouetteRaster.SIZE * guiScale;
        NativeImage nativeImage = new NativeImage(side, side, false);
        for (int py = 0; py < side; py++) {
            for (int px = 0; px < side; px++) {
                nativeImage.setPixel(px, py, outline[py * side + px]);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(SeedMapClient.MOD_ID,
                "dynamic/outline/" + generation + "/" + guiScale + "/"
                        + Integer.toHexString(structure.toString().hashCode()));
        try {
            client.getTextureManager().register(id, new DynamicTexture(id::toString, nativeImage));
            return Optional.of(new Entry(id, side, guiScale));
        } catch (RuntimeException exception) {
            nativeImage.close();
            SeedMapClient.LOGGER.warn("Failed to cache icon outline for {}", structure, exception);
            return Optional.empty();
        }
    }

    private static BufferedImage source(ResourceManager manager, Identifier key,
                                        Identifier customPng, String mapSprite, Item item) {
        BufferedImage image = customPng == null ? null : load(manager, customPng);
        if (image == null && mapSprite != null) image = load(manager,
                Identifier.withDefaultNamespace("textures/map/decorations/" + mapSprite + ".png"));
        if (image == null && item != null) {
            image = RenderedItemCapture.cached(key);
            if (image == null) {
                var flat = ItemModelTexture.resolve(manager, BuiltInRegistries.ITEM.getKey(item));
                if (flat.texture() != null) image = load(manager, flat.texture());
            }
        }
        return image;
    }

    private static BufferedImage load(ResourceManager manager, Identifier path) {
        try {
            var resource = manager.getResource(path);
            if (resource.isEmpty()) return null;
            try (InputStream stream = resource.get().open()) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                        || image.getWidth() > 512 || image.getHeight() > 512) return null;
                return image;
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    public static void invalidate(Identifier structure) {
        CACHE.entrySet().removeIf(entry -> {
            Identifier marker = entry.getKey().structure();
            Item overlay = StructureIcons.overlayItem(marker);
            if (!marker.equals(structure) && !StructureIcons.baseIcon(marker).equals(structure)
                    && (overlay == null || !StructureIcons.overlayKey(overlay).equals(structure))) return false;
            entry.getValue().ifPresent(texture ->
                    Minecraft.getInstance().getTextureManager().release(texture.texture()));
            return true;
        });
    }

    /** Resource packs, game restarts and GUI scale changes must never use stale silhouettes. */
    public static void clearCache() {
        Minecraft client = Minecraft.getInstance();
        for (Optional<Entry> cached : CACHE.values()) {
            if (cached.isPresent()) client.getTextureManager().release(cached.get().texture());
        }
        CACHE.clear();
        RenderedItemCapture.clear();
        generation++;
        lastResources = null;
    }
}
