package dev.fayber.seedmap.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Resolves the visible flat GUI item sprite through the active resource pack's
 * item definition + model JSON chain. The previous implementation assumed
 * every item uses textures/item/{itemId}.png, which is not true for retextured
 * or model-redirected resource packs.
 *
 * 3D/custom renderers intentionally do not return a flat texture: tracing a
 * flat PNG for a 3D item produces a visibly misplaced outline.
 */
public final class ItemModelTexture {
    public record Result(boolean hasDefinition, Identifier texture) {}

    private ItemModelTexture() {}

    public static Result resolve(ResourceManager manager, Identifier item) {
        Identifier definition = Identifier.fromNamespaceAndPath(item.getNamespace(),
                "items/" + item.getPath() + ".json");
        JsonObject json = json(manager, definition);
        if (json == null) return new Result(false, null);
        if (!json.has("model") || !json.get("model").isJsonObject()) return new Result(true, null);
        JsonObject descriptor = json.getAsJsonObject("model");
        if (!"minecraft:model".equals(string(descriptor, "type"))) {
            // Composite, range dispatch and dynamic models can change their
            // appearance based on item state; don't invent an outline mask.
            return new Result(true, null);
        }
        String model = string(descriptor, "model");
        if (model == null) return new Result(true, null);
        Identifier modelId = asset(model);
        for (int depth = 0; depth < 10; depth++) {
            JsonObject baked = json(manager, Identifier.fromNamespaceAndPath(modelId.getNamespace(),
                    "models/" + modelId.getPath() + ".json"));
            if (baked == null || baked.has("elements")) {
                return new Result(true, null);
            }
            if (baked.has("textures") && baked.get("textures").isJsonObject()) {
                String texture = string(baked.getAsJsonObject("textures"), "layer0");
                if (texture != null && !texture.startsWith("#")) {
                    Identifier textureId = asset(texture);
                    return new Result(true, Identifier.fromNamespaceAndPath(textureId.getNamespace(),
                            "textures/" + textureId.getPath() + ".png"));
                }
            }
            String parent = string(baked, "parent");
            if (parent == null) return new Result(true, null);
            if (parent.equals("minecraft:item/generated") || parent.equals("item/generated")
                    || parent.equals("minecraft:item/handheld") || parent.equals("item/handheld")) {
                return new Result(true, null);
            }
            modelId = asset(parent);
        }
        return new Result(true, null);
    }

    private static Identifier asset(String raw) {
        int split = raw.indexOf(':');
        return split < 0 ? Identifier.withDefaultNamespace(raw)
                : Identifier.fromNamespaceAndPath(raw.substring(0, split), raw.substring(split + 1));
    }

    private static String string(JsonObject object, String member) {
        return object != null && object.has(member) && object.get(member).isJsonPrimitive()
                ? object.get(member).getAsString() : null;
    }

    private static JsonObject json(ResourceManager manager, Identifier path) {
        try {
            var resource = manager.getResource(path);
            if (resource.isEmpty()) return null;
            try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                var parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
