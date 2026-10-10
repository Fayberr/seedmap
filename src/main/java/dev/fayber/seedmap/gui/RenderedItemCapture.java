package dev.fayber.seedmap.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import dev.fayber.seedmap.SeedMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.resources.Identifier;

/**
 * Copies the actual finished GUI item model (including 3D models and active
 * resource packs) from Minecraft 26.3's own cached item atlas.
 *
 * The copy runs asynchronously, once per structure type. At most two small
 * item-sized readbacks may be in flight. There is NO extra 3D render, GPU
 * readback, or CPU image analysis in steady-state map frames.
 *
 * Requests are associated with the exact GuiItemRenderState when
 * GuiRenderState.addItem is called. Matching only x/y was unreliable when
 * duplicate icons, pose transforms or moving map markers shared positions.
 */
public final class RenderedItemCapture {
    private static final int MAX_IN_FLIGHT = 2;
    private record Request(Identifier structure, int epoch) {}

    private static final Map<GuiItemRenderState, Request> TARGETS = new IdentityHashMap<>();
    private static final Map<Identifier, GuiItemRenderState> LAST_TARGET = new HashMap<>();
    private static final Map<Identifier, BufferedImage> IMAGES = new HashMap<>();
    private static final Set<Identifier> IN_FLIGHT = new HashSet<>();
    private static final Set<Identifier> UNSUPPORTED = new HashSet<>();
    private static int epoch;
    private static Identifier extractingStructure;

    private RenderedItemCapture() {}

    public static BufferedImage cached(Identifier structure) {
        return IMAGES.get(structure);
    }

    /** Called immediately around SeedMap's own item extraction, not the entire GUI frame. */
    public static void beginItem(Identifier structure) {
        extractingStructure = structure != null && !IMAGES.containsKey(structure)
                && !IN_FLIGHT.contains(structure) && !UNSUPPORTED.contains(structure)
                ? structure : null;
    }

    public static void endItem() {
        extractingStructure = null;
    }

    /** Mixin into GuiRenderState.addItem: this is the exact state the renderer later uses. */
    public static void onItemQueued(GuiItemRenderState item) {
        Identifier structure = extractingStructure;
        if (structure == null) return;
        GuiItemRenderState previous = LAST_TARGET.put(structure, item);
        if (previous != null) TARGETS.remove(previous);
        TARGETS.put(item, new Request(structure, epoch));
    }

    /** GuiRenderState.reset runs after rendering the previous extracted frame. */
    public static void endFrame() {
        TARGETS.clear();
        LAST_TARGET.clear();
        extractingStructure = null;
    }

    /** Minecraft's normal cached GUI-item atlas. */
    public static void onResolved(GuiItemRenderState item, GuiItemAtlas.SlotView slot) {
        if (!TARGETS.containsKey(item)) return;
        int atlasWidth = slot.textureView().getWidth(0);
        int atlasHeight = slot.textureView().getHeight(0);
        int x = Math.round(Math.min(slot.u0(), slot.u1()) * atlasWidth);
        int y = Math.round(Math.min(slot.v0(), slot.v1()) * atlasHeight);
        int w = Math.round(Math.abs(slot.u1() - slot.u0()) * atlasWidth);
        int h = Math.round(Math.abs(slot.v1() - slot.v0()) * atlasHeight);
        capture(item, slot.textureView().texture(), x, y, w, h, false);
    }

    /**
     * Large custom 3D models are rendered into Minecraft's picture-in-picture
     * texture rather than the standard item atlas. Take only the 16x16-GUI-pixel
     * region that the map actually displays; avoid reading back the entire PIP.
     */
    public static void onOversizedResolved(GuiItemRenderState item,
                                           com.mojang.renderpearl.api.textures.GpuTexture texture,
                                           int x0, int y0, int x1, int y1) {
        if (texture == null || !TARGETS.containsKey(item)) return;
        int physicalScale = Math.max(1, Math.round(
                texture.getWidth(0) / (float) Math.max(1, x1 - x0)));
        int size = Math.clamp(16 * physicalScale, 16, 256);
        int sx = Math.round((item.x() - x0) * physicalScale);
        // PIP's GUI blit reverses V (v0=1, v1=0). Capture the
        // corresponding bottom-up region and flip the alpha mask afterward.
        int sy = Math.round((y1 - (item.y() + 16)) * physicalScale);
        capture(item, texture, sx, sy, size, size, true);
    }

    private static void capture(GuiItemRenderState item,
                                com.mojang.renderpearl.api.textures.GpuTexture texture,
                                int x, int y, int w, int h, boolean flipY) {
        Request requested = TARGETS.get(item);
        if (requested == null || requested.epoch() != epoch) return;
        Identifier structure = requested.structure();
        if (IMAGES.containsKey(structure) || UNSUPPORTED.contains(structure)) {
            TARGETS.remove(item);
            LAST_TARGET.remove(structure, item);
            return;
        }
        if (IN_FLIGHT.contains(structure) || IN_FLIGHT.size() >= MAX_IN_FLIGHT) return;
        TARGETS.remove(item);
        LAST_TARGET.remove(structure, item);

        if (w < 8 || h < 8 || w > 256 || h > 256 || w != h
                || x < 0 || y < 0 || x + w > texture.getWidth(0)
                || y + h > texture.getHeight(0)) {
            unsupported(structure, "Invalid GPU capture region " + x + "," + y + " " + w + "x" + h, null);
            return;
        }
        final int generation = epoch;
        final GpuBuffer staging;
        try {
            staging = RenderSystem.getDevice().createBuffer(
                    () -> "SeedMap captured item silhouette",
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) w * h * 4);
        } catch (RuntimeException error) {
            unsupported(structure, "Could not allocate readback buffer", error);
            return;
        }
        IN_FLIGHT.add(structure);
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(
                    texture, staging, 0L,
                    () -> Minecraft.getInstance().execute(
                            () -> processReadback(structure, generation, staging, w, h, flipY)),
                    0, x, y, w, h);
        } catch (RuntimeException error) {
            IN_FLIGHT.remove(structure);
            unsupported(structure, "Could not start GPU copy", error);
            staging.close();
        }
    }

    private static void processReadback(Identifier structure, int generation,
                                        GpuBuffer staging, int width, int height,
                                        boolean flipY) {
        try {
            if (generation != epoch) return;
            BufferedImage rendered;
            try (var mapped = staging.map(true, false)) {
                rendered = decodeAlpha(mapped.data(), width, height, flipY);
            }
            if (rendered == null) {
                unsupported(structure, "GPU capture was fully transparent or incomplete", null);
                return;
            }
            IMAGES.put(structure, rendered);
            // The previous absence was cached: allow the next frame to bake a
            // single outline texture from the real 3D model's alpha channel.
            IconSilhouette.invalidate(structure);
        } catch (RuntimeException error) {
            unsupported(structure, "GPU capture readback failed", error);
        } finally {
            if (generation == epoch) IN_FLIGHT.remove(structure);
            staging.close();
        }
    }

    private static void unsupported(Identifier structure, String reason, Throwable error) {
        if (UNSUPPORTED.add(structure)) {
            if (error == null) SeedMapClient.LOGGER.warn("Cannot capture item outline for {}: {}", structure, reason);
            else SeedMapClient.LOGGER.warn("Cannot capture item outline for {}: {}", structure, reason, error);
        }
    }

    /** Decodes only the alpha of the RGBA item-atlas GPU readback. */
    static BufferedImage decodeAlpha(ByteBuffer rgba, int width, int height) {
        return decodeAlpha(rgba, width, height, false);
    }

    static BufferedImage decodeAlpha(ByteBuffer rgba, int width, int height, boolean flipY) {
        if (rgba == null || width <= 0 || height <= 0 || width > 256 || height > 256
                || rgba.remaining() < width * height * 4) return null;
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int offset = rgba.position();
        boolean hasPixels = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = rgba.get(offset + (y * width + x) * 4 + 3) & 0xFF;
                if (alpha != 0) hasPixels = true;
                mask.setRGB(x, flipY ? height - 1 - y : y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return hasPixels ? mask : null;
    }

    public static void clear() {
        epoch++;
        endFrame();
        IMAGES.clear();
        IN_FLIGHT.clear();
        UNSUPPORTED.clear();
    }
}
