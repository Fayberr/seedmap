package dev.fayber.seedmap.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * Requests are registered during GUI extraction and matched to the baked item
 * render state during GuiRenderer's preparation pass.
 */
public final class RenderedItemCapture {
    private static final int MAX_IN_FLIGHT = 2;
    private record Request(Identifier structure, int epoch) {}

    private static final Map<Long, Request> REQUESTS = new HashMap<>();
    private static final Map<Identifier, Long> REQUEST_POSITION = new HashMap<>();
    private static final Map<Identifier, BufferedImage> IMAGES = new HashMap<>();
    private static final Set<Identifier> IN_FLIGHT = new HashSet<>();
    private static final Set<Identifier> UNSUPPORTED = new HashSet<>();
    private static int epoch;

    private RenderedItemCapture() {}

    private static long at(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    public static BufferedImage cached(Identifier structure) {
        return IMAGES.get(structure);
    }

    public static void request(Identifier structure, int x, int y) {
        if (structure == null || IMAGES.containsKey(structure)
                || IN_FLIGHT.contains(structure) || UNSUPPORTED.contains(structure)) return;
        // At most one pending capture per actual model. A map can display
        // hundreds of copies of one boat; updating the screen-coordinate match
        // avoids a long request queue when panning and does not add extra copies.
        long position = at(x, y);
        Long previous = REQUEST_POSITION.put(structure, position);
        if (previous != null && previous.longValue() != position) {
            Request prior = REQUESTS.get(previous);
            if (prior != null && prior.structure().equals(structure)) REQUESTS.remove(previous);
        }
        REQUESTS.put(position, new Request(structure, epoch));
    }

    /** Minecraft's normal cached GUI-item atlas. */
    public static void onResolved(GuiItemRenderState item, GuiItemAtlas.SlotView slot) {
        if (!REQUESTS.containsKey(at(item.x(), item.y()))) return;
        int atlasWidth = slot.textureView().getWidth(0);
        int atlasHeight = slot.textureView().getHeight(0);
        int x = Math.round(Math.min(slot.u0(), slot.u1()) * atlasWidth);
        int y = Math.round(Math.min(slot.v0(), slot.v1()) * atlasHeight);
        int w = Math.round(Math.abs(slot.u1() - slot.u0()) * atlasWidth);
        int h = Math.round(Math.abs(slot.v1() - slot.v0()) * atlasHeight);
        capture(item.x(), item.y(), slot.textureView().texture(), x, y, w, h);
    }

    /**
     * Large custom 3D models are rendered into Minecraft's picture-in-picture
     * texture rather than the standard item atlas. Take only the 16x16-GUI-pixel
     * region that the map actually displays; avoid reading back the entire PIP.
     */
    public static void onOversizedResolved(GuiItemRenderState item,
                                           com.mojang.renderpearl.api.textures.GpuTexture texture,
                                           int x0, int y0, int x1, int y1) {
        if (texture == null || !REQUESTS.containsKey(at(item.x(), item.y()))) return;
        int physicalScale = Math.max(1, Math.round(
                texture.getWidth(0) / (float) Math.max(1, x1 - x0)));
        int size = Math.clamp(16 * physicalScale, 16, 256);
        int sx = Math.round((item.x() - x0) * physicalScale);
        int sy = Math.round((item.y() - y0) * physicalScale);
        capture(item.x(), item.y(), texture, sx, sy, size, size);
    }

    private static void capture(int requestedX, int requestedY,
                                com.mojang.renderpearl.api.textures.GpuTexture texture,
                                int x, int y, int w, int h) {
        Request requested = REQUESTS.get(at(requestedX, requestedY));
        if (requested == null || requested.epoch() != epoch) return;
        Identifier structure = requested.structure();
        if (IMAGES.containsKey(structure) || UNSUPPORTED.contains(structure)) {
            REQUESTS.remove(at(requestedX, requestedY));
            return;
        }
        if (IN_FLIGHT.contains(structure) || IN_FLIGHT.size() >= MAX_IN_FLIGHT) return;
        REQUESTS.remove(at(requestedX, requestedY));
        REQUEST_POSITION.remove(structure);

        if (w < 8 || h < 8 || w > 256 || h > 256 || w != h
                || x < 0 || y < 0 || x + w > texture.getWidth(0)
                || y + h > texture.getHeight(0)) {
            UNSUPPORTED.add(structure);
            return;
        }
        final int generation = epoch;
        final GpuBuffer staging;
        try {
            staging = RenderSystem.getDevice().createBuffer(
                    () -> "SeedMap captured item silhouette",
                    GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) w * h * 4);
        } catch (RuntimeException error) {
            UNSUPPORTED.add(structure);
            return;
        }
        IN_FLIGHT.add(structure);
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(
                    texture, staging, 0L,
                    () -> Minecraft.getInstance().execute(
                            () -> processReadback(structure, generation, staging, w, h)),
                    0, x, y, w, h);
        } catch (RuntimeException error) {
            IN_FLIGHT.remove(structure);
            UNSUPPORTED.add(structure);
            staging.close();
        }
    }

    private static void processReadback(Identifier structure, int generation,
                                        GpuBuffer staging, int width, int height) {
        try {
            if (generation != epoch) return;
            BufferedImage rendered;
            try (var mapped = staging.map(true, false)) {
                rendered = decodeAlpha(mapped.data(), width, height);
            }
            if (rendered == null) {
                UNSUPPORTED.add(structure);
                return;
            }
            IMAGES.put(structure, rendered);
            // The previous absence was cached: allow the next frame to bake a
            // single outline texture from the real 3D model's alpha channel.
            IconSilhouette.invalidate(structure);
        } catch (RuntimeException error) {
            UNSUPPORTED.add(structure);
        } finally {
            IN_FLIGHT.remove(structure);
            staging.close();
        }
    }

    /** Decodes only the alpha of the RGBA item-atlas GPU readback. */
    static BufferedImage decodeAlpha(ByteBuffer rgba, int width, int height) {
        if (rgba == null || width <= 0 || height <= 0 || width > 256 || height > 256
                || rgba.remaining() < width * height * 4) return null;
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int offset = rgba.position();
        boolean hasPixels = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = rgba.get(offset + (y * width + x) * 4 + 3) & 0xFF;
                if (alpha != 0) hasPixels = true;
                mask.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return hasPixels ? mask : null;
    }

    public static void clear() {
        epoch++;
        REQUESTS.clear();
        REQUEST_POSITION.clear();
        IMAGES.clear();
        IN_FLIGHT.clear();
        UNSUPPORTED.clear();
    }
}
