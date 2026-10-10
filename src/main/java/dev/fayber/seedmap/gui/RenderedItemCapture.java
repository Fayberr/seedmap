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
        // The map may show many shipwrecks, but only one thumbnail per model
        // needs capturing. Bound the request queue to guard against huge maps.
        if (REQUESTS.size() < 64) {
            REQUESTS.putIfAbsent(at(x, y), new Request(structure, epoch));
        }
    }

    /** Invoked by the sole GuiRenderer mixin at the item atlas blit boundary. */
    public static void onResolved(GuiItemRenderState item, GuiItemAtlas.SlotView slot) {
        Request requested = REQUESTS.get(at(item.x(), item.y()));
        if (requested == null || requested.epoch() != epoch) return;
        Identifier structure = requested.structure();
        if (IMAGES.containsKey(structure) || UNSUPPORTED.contains(structure)) {
            REQUESTS.remove(at(item.x(), item.y()));
            return;
        }
        if (IN_FLIGHT.contains(structure) || IN_FLIGHT.size() >= MAX_IN_FLIGHT) return;
        REQUESTS.remove(at(item.x(), item.y()));

        int atlasWidth = slot.textureView().getWidth(0);
        int atlasHeight = slot.textureView().getHeight(0);
        int x = Math.round(slot.u0() * atlasWidth);
        int y = Math.round(slot.v0() * atlasHeight);
        int w = Math.round(Math.abs(slot.u1() - slot.u0()) * atlasWidth);
        int h = Math.round(Math.abs(slot.v1() - slot.v0()) * atlasHeight);
        if (w < 8 || h < 8 || w > 256 || h > 256 || w != h
                || x < 0 || y < 0 || x + w > atlasWidth || y + h > atlasHeight) {
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
                    slot.textureView().texture(), staging, 0L, () -> {
                        Minecraft.getInstance().execute(
                                () -> processReadback(structure, generation, staging, w, h));
                    }, 0, x, y, w, h);
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
            BufferedImage rendered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            try (var mapped = staging.map(true, false)) {
                ByteBuffer pixels = mapped.data();
                if (pixels.remaining() < width * height * 4) return;
                int count = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int alpha = pixels.get((y * width + x) * 4 + 3) & 0xFF;
                        if (alpha != 0) count++;
                        rendered.setRGB(x, y, alpha << 24 | 0xFFFFFF);
                    }
                }
                if (count == 0) {
                    UNSUPPORTED.add(structure);
                    return;
                }
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

    public static void clear() {
        epoch++;
        REQUESTS.clear();
        IMAGES.clear();
        IN_FLIGHT.clear();
        UNSUPPORTED.clear();
    }
}
