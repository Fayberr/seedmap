package dev.fayber.seedmap.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Rounded, shadowless button, drawn standalone or through optional Modern GUI primitives. */
public final class SeedMapButton extends AbstractButton {
    private final Runnable onPress;

    public SeedMapButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, SeedMapUi.text(message));
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int fill = !isActive() ? 0xFF171717 : hovered ? 0xFF303030 : 0xFF222222;
        SeedMapUi.panel(gfx, getX(), getY(), getWidth(), getHeight(), 5.0f, fill);
        Component text = SeedMapUi.text(getMessage());
        gfx.centeredText(net.minecraft.client.Minecraft.getInstance().font, text,
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                isActive() ? 0xFFF0F0F0 : 0xFF6E6E6E);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
