package dev.fayber.seedmap.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import dev.fayber.seedmap.SeedMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.resources.Identifier;

/**
 * Copies the actual finished GUI item model (including 3D models and active
 * resource packs) from Minecraft 26.3's own cached item atlas.
 *
 * Capture is queued during vanilla's item preparation and copied only after
 * GuiRenderer.render has finished its item-atlas and PIP render passes.
 * At most two one-off readbacks are in flight; steady-state draws use cached
 * silhouettes and perform no GPU readbacks.
 *
 * Requests are associated with the exact GuiItemRenderState in addItem.
 * Failures are rate-limited and retried instead of permanently blacklisting
 * model outlines until resource reload.
 */
public final class RenderedItemCapture {
    private static final int MAX_IN_FLIGHT = 2;
    private record Request(Identifier structure, int epoch) {}
    private record Pending(Identifier structure, int generation,
                           com.mojang.renderpearl.api.textures.GpuTexture texture,
                           int x, int y, int width, int height, boolean flipY) {}

    private static final Map<GuiItemRenderState, Request> TARGETS = new IdentityHashMap<>();
    private static final Map<Identifier, GuiItemRenderState> LAST_TARGET = new HashMap<>();
    private static final Map<Identifier, BufferedImage> IMAGES = new HashMap<>();
    private static final Map<Identifier, Pending> PENDING = new LinkedHashMap<>();
    private static final Map<Identifier, Boolean> IN_FLIGHT = new HashMap<>();
    private static final Map<Identifier, Integer> RETRY_AFTER = new HashMap<>();
    private static final Map<Identifier, Integer> FAILURES = new HashMap<>();
    private static int epoch;
    private static int frame;
    private static Identifier extractingStructure;

    private RenderedItemCapture() {}

    public static BufferedImage cached(Identifier structure) {
        return IMAGES.get(structure);
    }

    private static boolean shouldRequest(Identifier structure) {
        return structure != null && !IMAGES.containsKey(structure)
                && !IN_FLIGHT.containsKey(structure) && !PENDING.containsKey(structure)
                && frame >= RETRY_AFTER.getOrDefault(structure, 0);
    }

    /** Called immediately around SeedMap's own item extraction. */
    public static void beginItem(Identifier structure) {
        extractingStructure = shouldRequest(structure) ? structure : null;
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

    /** Do not clear targets from GuiRenderState.reset: the renderer may not
     * have consumed those item objects yet. Cleanup runs after render instead. */
    private static void endFrame() {
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
        Request request = TARGETS.remove(item);
        if (request == null || request.epoch() != epoch) return;
        Identifier structure = request.structure();
        LAST_TARGET.remove(structure, item);
        if (!shouldRequest(structure)) return;
        if (w < 8 || h < 8 || w > 256 || h > 256 || w != h
                || x < 0 || y < 0 || x + w > texture.getWidth(0)
                || y + h > texture.getHeight(0)) {
            failed(structure, "Invalid GPU capture region " + x + "," + y
                    + " " + w + "x" + h, null);
            return;
        }
        // The slot may not have been drawn yet while GuiRenderer prepares its
        // elements. Never issue the copy here: hold the texture and region
        // until the final GUI render pass has finished.
        PENDING.put(structure, new Pending(structure, epoch, texture, x, y, w, h, flipY));
    }

    /** Invoked from GuiRenderer.render(TAIL), after the GUI render pass closes. */
    public static void onFrameRendered() {
        frame++;
        try {
            var iterator = PENDING.values().iterator();
            while (iterator.hasNext() && IN_FLIGHT.size() < MAX_IN_FLIGHT) {
                Pending pending = iterator.next();
                iterator.remove();
                if (pending.generation() != epoch || !shouldRequest(pending.structure())) continue;
                startCopy(pending);
            }
        } finally {
            // These references belong to the frame that just finished.
            // Resetting them earlier loses all 3D item capture requests.
            PENDING.clear();
            endFrame();
        }
    }

    private static void startCopy(Pending pending) {
        Identifier structure = pending.structure();
        int w = pending.width(), h = pending.height();
        final GpuBuffer staging;
        try {
            // The GPU backend handles its own row-pitch conversion for
            // copyTextureToBuffer; the public API exposes a packed readback.
            staging = RenderSystem.getDevice().createBuffer(
                    () -> "SeedMap captured item silhouette",
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) w * h * 4);
        } catch (RuntimeException error) {
            failed(structure, "Could not allocate readback buffer", error);
            return;
        }
        IN_FLIGHT.put(structure, Boolean.TRUE);
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(
                    pending.texture(), staging, 0L,
                    () -> Minecraft.getInstance().execute(
                            () -> processReadback(structure, pending.generation(), staging,
                                    w, h, pending.flipY())),
                    0, pending.x(), pending.y(), w, h);
        } catch (RuntimeException error) {
            IN_FLIGHT.remove(structure);
            failed(structure, "Could not copy rendered model", error);
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
                failed(structure, "GPU capture was fully transparent or incomplete", null);
                return;
            }
            IMAGES.put(structure, rendered);
            RETRY_AFTER.remove(structure);
            FAILURES.remove(structure);
            // The previous absence was cached: allow the next frame to bake a
            // single outline texture from the real 3D model's alpha channel.
            IconSilhouette.invalidate(structure);
        } catch (RuntimeException error) {
            failed(structure, "GPU capture readback failed", error);
        } finally {
            if (generation == epoch) IN_FLIGHT.remove(structure);
            staging.close();
        }
    }

    private static void failed(Identifier structure, String reason, Throwable error) {
        int failures = FAILURES.merge(structure, 1, Integer::sum);
        RETRY_AFTER.put(structure, frame + Math.min(600, failures * 120));
        if (failures == 1 || failures % 10 == 0) {
            if (error == null) SeedMapClient.LOGGER.warn(
                    "Item silhouette capture for {} failed (attempt {}): {}",
                    structure, failures, reason);
            else SeedMapClient.LOGGER.warn(
                    "Item silhouette capture for {} failed (attempt {}): {}",
                    structure, failures, reason, error);
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
        PENDING.clear();
        IN_FLIGHT.clear();
        RETRY_AFTER.clear();
        FAILURES.clear();
        frame = 0;
    }
}
