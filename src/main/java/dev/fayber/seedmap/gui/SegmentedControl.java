package dev.fayber.seedmap.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class SegmentedControl {
    public record Segment(Supplier<Component> label, BooleanSupplier selected, Runnable onClick) {
        public static Segment of(Component label, BooleanSupplier selected, Runnable onClick) {
            return new Segment(() -> label, selected, onClick);
        }
    }

    private static final int ON = 0xFFE6E6E6;
    private static final int ON_HOVER = 0xFFFFFFFF;
    private static final int OFF = 0xFF1A1A1A;
    private static final int OFF_HOVER = 0xFF262626;
    private static final int BORDER = 0xFF262626;
    private static final int TEXT_ON = 0xFF121212;
    private static final int TEXT_OFF = 0xFFA3A3A3;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<Segment> segments;

    public SegmentedControl(int x, int y, int width, int height, List<Segment> segments) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.segments = segments;
    }

    private int segmentLeft(int index) {
        return x + index * width / segments.size();
    }

    private int segmentRight(int index) {
        return x + (index + 1) * width / segments.size();
    }

    public void render(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        SeedMapUi.panel(context, x, y, width, height, 5.0f, BORDER);
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            int left = segmentLeft(i) + 1;
            int right = segmentRight(i) - 1;
            boolean selected = segment.selected().getAsBoolean();
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + height;
            int fill = selected ? (hovered ? ON_HOVER : ON) : (hovered ? OFF_HOVER : OFF);
            SeedMapUi.panel(context, left, y + 1, right - left, height - 2, 4.0f, fill);
            String raw = segment.label().get().getString();
            var label = SeedMapUi.text(raw);
            int avail = Math.max(1, right - left - 6);
            while (raw.length() > 1 && font.width(label) > avail) {
                raw = raw.substring(0, raw.length() - 1);
                label = SeedMapUi.text(raw);
            }
            int textX = (left + right) / 2 - font.width(label) / 2;
            int textY = y + (height - 8) / 2;
            context.text(font, label, textX, textY, selected ? TEXT_ON : TEXT_OFF, false);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 1 || mx < x || mx >= x + width || my < y || my >= y + height) {
            return false;
        }
        for (int i = 0; i < segments.size(); i++) {
            if (mx >= segmentLeft(i) && mx < segmentRight(i)) {
                segments.get(i).onClick().run();
                return true;
            }
        }
        return false;
    }
}
