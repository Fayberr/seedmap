package dev.fayber.seedmap.gui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Optional modern input bridge: use the real Modern GUI TextField when present,
 * fall back to vanilla EditBox otherwise. No hard dependency on Modern GUI.
 */
public final class SeedMapTextInput {
    private final AbstractWidget widget;
    private final EditBox fallback;
    private final Object modern;
    private Consumer<String> responder;
    private Predicate<String> validator = value -> true;

    public SeedMapTextInput(Font font, int x, int y, int width, int height, Component accessibleLabel) {
        Object modernField = null;
        AbstractWidget visual = null;
        if (FabricLoader.getInstance().isModLoaded("moderngui")) {
            try {
                Class<?> clazz = Class.forName("net.fayber.moderngui.widget.TextField");
                Constructor<?> ctor = clazz.getConstructor(int.class, int.class, int.class, int.class);
                modernField = ctor.newInstance(x, y, width, height);
                visual = (AbstractWidget) modernField;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                modernField = null;
                visual = null;
            }
        }
        modern = modernField;
        if (visual != null) {
            widget = visual;
            fallback = null;
            call("onChanged", new Class<?>[]{Consumer.class},
                    (Consumer<String>) this::changed);
        } else {
            fallback = new EditBox(font, x, y, width, height, accessibleLabel);
            fallback.setResponder(this::changed);
            widget = fallback;
        }
    }

    private Object call(String method, Class<?>[] signature, Object... args) {
        if (modern == null) return null;
        try {
            Method member = modern.getClass().getMethod(method, signature);
            return member.invoke(modern, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private void changed(String value) {
        if (responder != null) responder.accept(value);
    }

    public AbstractWidget widget() { return widget; }
    public int getX() { return widget.getX(); }
    public int getY() { return widget.getY(); }
    public int getWidth() { return widget.getWidth(); }
    public int getHeight() { return widget.getHeight(); }
    public boolean isFocused() { return widget.isFocused(); }
    public void setFocused(boolean focused) { widget.setFocused(focused); }
    public void setEditable(boolean editable) {
        if (fallback != null) fallback.setEditable(editable);
        else widget.active = editable;
    }

    public void setResponder(Consumer<String> onChange) { responder = onChange; }

    public void setHint(Component hint) {
        if (fallback != null) fallback.setHint(hint);
        else call("hint", new Class<?>[]{String.class}, hint.getString());
    }

    public void setMaxLength(int limit) {
        if (fallback != null) fallback.setMaxLength(limit);
        else call("maxLength", new Class<?>[]{int.class}, limit);
    }

    public void setValidator(Predicate<String> predicate) {
        validator = predicate;
        if (modern != null) call("validator", new Class<?>[]{Predicate.class}, predicate);
    }

    public String getValue() {
        if (fallback != null) return fallback.getValue();
        Object result = call("getValue", new Class<?>[0]);
        return result instanceof String s ? s : "";
    }

    public void setValue(String value) {
        if (fallback != null) fallback.setValue(value);
        else call("value", new Class<?>[]{String.class}, value);
    }

    public void setTextColor(int color) {
        // The Modern GUI field uses its validator and theme for automatic
        // colour/error states, rather than overriding its styled text colour.
        if (fallback != null) fallback.setTextColor(color);
    }
}
