package dev.fayber.seedmap.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fayber.seedmap.SeedMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Two independent navigation symbols, inspired by the user's silhouette
 * references rather than copied assets:
 *
 * MAP: solid, compact pointed cursor with a small notched tail, pointing north.
 * HUD: broad straight-shaft arrow with a triangular head, pointing east.
 *
 * Both masks are rasterized and uploaded once; a normal frame draws exactly
 * one cached textured quad, with no rectangle backgrounds or per-pixel draw calls.
 */
public final class NavigationPointer {
    private static final int TEXTURE_SIZE = 64;
    private static final float MAP_GUI_SIZE = 19.0f;
    private static final float HUD_GUI_SIZE = 23.0f;

    // The first and last vertices connect automatically.
    private static final float[][] MAP_SHAPE = {
            // Slightly asymmetric filled pointer, like the red map reference:
            // a sharp nose, broad rear edge and only a shallow tail notch.
            {32f, 6f}, {51f, 53f}, {30f, 43f}, {12f, 47f}
    };
    private static final float[][] HUD_SHAPE = {
            {6f, 23f}, {35f, 23f}, {35f, 13f}, {60f, 32f},
            {35f, 51f}, {35f, 41f}, {6f, 41f}
    };

    private static Identifier mapTexture;
    private static Identifier hudTexture;
    private static int generation;

    private NavigationPointer() {}

    /** Map-facing north-pointing cursor; alpha tint distinguishes Freecam. */
    public static void drawMap(GuiGraphicsExtractor graphics, int x, int y,
                               float radians, int tint) {
        if (mapTexture == null) mapTexture = upload("map_cursor", rasterizeMap());
        draw(graphics, mapTexture, x, y, radians, MAP_GUI_SIZE, tint);
    }

    /** Tracking HUD: horizontal block arrow pointing east before rotation. */
    public static void drawHud(GuiGraphicsExtractor graphics, int x, int y,
                               float relativeRadians, int tint) {
        if (hudTexture == null) hudTexture = upload("tracking_arrow", rasterizeHud());
        // Keep previous bearing semantics: zero degrees means straight ahead/up.
        draw(graphics, hudTexture, x, y,
                relativeRadians - (float) (Math.PI / 2.0), HUD_GUI_SIZE, tint);
    }

    private static void draw(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
                             float radians, float guiSize, int tint) {
        if (texture == null) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(radians);
        pose.scale(guiSize / TEXTURE_SIZE, guiSize / TEXTURE_SIZE);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                -TEXTURE_SIZE / 2, -TEXTURE_SIZE / 2, 0, 0,
                TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, tint);
        pose.popMatrix();
    }

    public static int[] rasterizeMap() {
        return rasterizePolygon(MAP_SHAPE);
    }

    public static int[] rasterizeHud() {
        return rasterizePolygon(HUD_SHAPE);
    }

    private static int[] rasterizePolygon(float[][] vertices) {
        int[] pixels = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int covered = 0;
                int edged = 0;
                // Supersample at rasterization time only, not while rendering.
                // The tiny dark contour stays attached to the arrow, never a box.
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float px = x + (sx + 0.5f) / 4f;
                        float py = y + (sy + 0.5f) / 4f;
                        boolean inside = contains(vertices, px, py);
                        if (inside) covered++;
                        if (inside || distanceToEdge(vertices, px, py) <= 1.6f) edged++;
                    }
                }
                if (edged == 0) continue;
                // Filled white arrow with a crisp dark stroke for contrast.
                int core = Math.round(covered / 16f * 255);
                int outer = Math.round(edged / 16f * 190);
                int alpha = core > 0 ? Math.max(core, outer) : outer;
                pixels[y * TEXTURE_SIZE + x] = (alpha << 24)
                        | (core > 0 ? 0xF7FAFC : 0x111820);
            }
        }
        return pixels;
    }

    private static boolean contains(float[][] shape, float x, float y) {
        boolean inside = false;
        for (int i = 0, j = shape.length - 1; i < shape.length; j = i++) {
            float xi = shape[i][0], yi = shape[i][1];
            float xj = shape[j][0], yj = shape[j][1];
            if ((yi > y) != (yj > y)
                    && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static float distanceToEdge(float[][] shape, float x, float y) {
        float minimum = Float.POSITIVE_INFINITY;
        for (int i = 0; i < shape.length; i++) {
            int next = (i + 1) % shape.length;
            float ax = shape[i][0], ay = shape[i][1];
            float dx = shape[next][0] - ax, dy = shape[next][1] - ay;
            float length = dx * dx + dy * dy;
            float t = length <= 0 ? 0
                    : Math.clamp(((x - ax) * dx + (y - ay) * dy) / length, 0f, 1f);
            minimum = Math.min(minimum, (float) Math.hypot(x - ax - dx * t, y - ay - dy * t));
        }
        return minimum;
    }

    private static Identifier upload(String name, int[] pixels) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getTextureManager() == null) return null;
        NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false);
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                image.setPixel(x, y, pixels[y * TEXTURE_SIZE + x]);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(SeedMapClient.MOD_ID,
                "dynamic/" + name + "/" + generation++);
        try {
            client.getTextureManager().register(id, new DynamicTexture(id::toString, image));
            return id;
        } catch (RuntimeException exception) {
            image.close();
            SeedMapClient.LOGGER.warn("Could not upload SeedMap {} glyph", name, exception);
            return null;
        }
    }

    public static void clearCache() {
        Minecraft client = Minecraft.getInstance();
        if (mapTexture != null) {
            client.getTextureManager().release(mapTexture);
            mapTexture = null;
        }
        if (hudTexture != null) {
            client.getTextureManager().release(hudTexture);
            hudTexture = null;
        }
    }
}
