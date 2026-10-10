package dev.fayber.seedmap.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fayber.seedmap.SeedMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A hollow, sharp-tipped heading pointer with a transparent interior and no
 * rectangular background. Generated once as a high-resolution textured glyph:
 * player, Freecam and waypoint HUD all use the same antialiased shape.
 */
public final class NavigationPointer {
    private static final int TEXTURE_SIZE = 64;
    private static final float GUI_SIZE = 18.0f;
    private static Identifier texture;
    private static int generation;

    private NavigationPointer() {}

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, float radians, int tint) {
        if (texture == null) upload();
        if (texture == null) return;
        var matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.rotate(radians);
        matrices.scale(GUI_SIZE / TEXTURE_SIZE, GUI_SIZE / TEXTURE_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -TEXTURE_SIZE / 2,
                -TEXTURE_SIZE / 2, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE,
                TEXTURE_SIZE, TEXTURE_SIZE, tint);
        matrices.popMatrix();
    }

    /** For testability: one cached texture, no per-frame polygon draws. */
    public static int[] rasterize() {
        int[] pixels = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        // A hollow navigation chevron with a recessed tail. The triangle's
        // center stays transparent; thin white strokes get a dark edge for
        // legibility against both light and dark biomes.
        float[][] path = {
                {32f, 5f}, {49f, 53f}, {32f, 43f}, {15f, 53f}, {32f, 5f}
        };
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                float distance = Float.POSITIVE_INFINITY;
                for (int i = 1; i < path.length; i++) {
                    distance = Math.min(distance, distance(x + .5f, y + .5f,
                            path[i - 1][0], path[i - 1][1], path[i][0], path[i][1]));
                }
                float core = coverage(distance, 3.1f);
                float border = coverage(distance, 5.0f);
                int alpha;
                int rgb;
                if (core > 0) {
                    alpha = Math.clamp(Math.round(255f * core + 170f * (1f - core) * border), 0, 255);
                    rgb = 0xF8FBFF;
                } else {
                    alpha = Math.round(170f * border);
                    rgb = 0x10151B;
                }
                if (alpha != 0) pixels[y * TEXTURE_SIZE + x] = (alpha << 24) | rgb;
            }
        }
        return pixels;
    }

    private static float coverage(float distance, float radius) {
        return Math.clamp(radius + .5f - distance, 0f, 1f);
    }

    private static float distance(float x, float y, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float length = dx * dx + dy * dy;
        float t = length <= 0 ? 0 : Math.clamp(((x - ax) * dx + (y - ay) * dy) / length, 0f, 1f);
        return (float) Math.hypot(x - (ax + t * dx), y - (ay + t * dy));
    }

    private static void upload() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getTextureManager() == null) return;
        NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false);
        int[] pixels = rasterize();
        for (int y = 0; y < TEXTURE_SIZE; y++)
            for (int x = 0; x < TEXTURE_SIZE; x++)
                image.setPixel(x, y, pixels[y * TEXTURE_SIZE + x]);
        Identifier id = Identifier.fromNamespaceAndPath(SeedMapClient.MOD_ID,
                "dynamic/heading_pointer/" + generation++);
        try {
            client.getTextureManager().register(id, new DynamicTexture(id::toString, image));
            texture = id;
        } catch (RuntimeException exception) {
            image.close();
            SeedMapClient.LOGGER.warn("Unable to upload navigation pointer", exception);
        }
    }

    public static void clearCache() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(texture);
            texture = null;
        }
    }
}
