package dev.fayber.seedmap.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Silhouette-following outlines, sampled from the actual alpha of map icons.
 *
 * Generates a smoothed contour from the PNG's transparency once per structure
 * type, then draws a thin white physical-pixel stroke. Unlike a card border,
 * it never encloses transparent corners of round icons such as the Ender Eye.
 */
public final class IconSilhouette {
    private static final Map<Identifier, Optional<List<SilhouetteContour.Segment>>> CACHE = new HashMap<>();
    private static final int WHITE = 0xEDFFFFFF;
    private static final int GLOW = 0x45FFFFFF;

    private IconSilhouette() {}

    public static void draw(GuiGraphicsExtractor gfx, Identifier structure, int x, int y,
                            Identifier customPng, String mapSprite, Item item) {
        if (structure == null) return;
        Optional<List<SilhouetteContour.Segment>> segments =
                CACHE.computeIfAbsent(structure, ignored -> load(customPng, mapSprite, item));
        if (segments.isEmpty()) return;
        float scale = Minecraft.getInstance().getWindow() == null
                ? 1f : (float) Minecraft.getInstance().getWindow().getGuiScale();
        gfx.pose().pushMatrix();
        // Physical-pixel drawing instead of GUI-scale rectangles: lines remain thin
        // and smooth at GUI scales 2–6, with no dependency on Modern GUI.
        gfx.pose().scale(1f / scale, 1f / scale);
        for (SilhouetteContour.Segment segment : segments.get()) {
            float x1 = (x + segment.x1()) * scale;
            float y1 = (y + segment.y1()) * scale;
            float x2 = (x + segment.x2()) * scale;
            float y2 = (y + segment.y2()) * scale;
            float dx = x2 - x1;
            float dy = y2 - y1;
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) * 1.25));
            for (int k = 0; k <= steps; k++) {
                float f = k / (float) steps;
                int px = Math.round(x1 + dx * f);
                int py = Math.round(y1 + dy * f);
                gfx.fill(px - 1, py - 1, px + 2, py + 2, GLOW);
                gfx.fill(px, py, px + 1, py + 1, WHITE);
            }
        }
        gfx.pose().popMatrix();
    }

    private static Optional<List<SilhouetteContour.Segment>> load(
            Identifier customPng, String mapSprite, Item item) {
        // Explicit custom icon has priority, since its alpha matches exactly
        // what is rendered on screen.
        if (customPng != null) {
            Optional<List<SilhouetteContour.Segment>> custom = read(customPng);
            if (custom.isPresent()) return custom;
        }
        if (mapSprite != null) {
            var spriteTexture = Identifier.fromNamespaceAndPath("minecraft",
                    "textures/map/decorations/" + mapSprite + ".png");
            Optional<List<SilhouetteContour.Segment>> sprite = read(spriteTexture);
            if (sprite.isPresent()) return sprite;
        }
        if (item != null) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            Identifier texture = Identifier.fromNamespaceAndPath(key.getNamespace(),
                    "textures/item/" + key.getPath() + ".png");
            Optional<List<SilhouetteContour.Segment>> sprite = read(texture);
            if (sprite.isPresent()) return sprite;
        }
        return Optional.empty();
    }

    private static Optional<List<SilhouetteContour.Segment>> read(Identifier path) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(path);
            if (resource.isEmpty()) return Optional.empty();
            try (InputStream input = resource.get().open()) {
                BufferedImage image = ImageIO.read(input);
                if (image == null || image.getWidth() > 512 || image.getHeight() > 512) {
                    return Optional.empty();
                }
                List<SilhouetteContour.Segment> contour = SilhouetteContour.trace(image);
                return contour.isEmpty() ? Optional.empty() : Optional.of(contour);
            }
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
