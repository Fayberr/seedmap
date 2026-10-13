package dev.fayber.seedmap.gui;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class StructureGrid {
    public static final int CELL = 22;
    private static final int ON = 0xFF262626;
    private static final int OFF = 0xFF1A1A1A;
    private static final int DIM = 0x99000000;
    private static final int TARGET = 0xFF7AA2F7;

    private final int x;
    private final int y;
    private final int columns;
    private final int maxVisibleRows;
    private int scrollRow;
    private final List<Identifier> ids;
    private final Set<Identifier> enabled;
    private final Runnable onChange;
    private final Consumer<Identifier> onSelect;
    private final Supplier<Identifier> target;

    public StructureGrid(int x, int y, int columns, List<Identifier> ids, Set<Identifier> enabled,
                         Runnable onChange, Consumer<Identifier> onSelect, Supplier<Identifier> target) {
        this(x, y, columns, ids, enabled, onChange, onSelect, target, Integer.MAX_VALUE);
    }

    public StructureGrid(int x, int y, int columns, List<Identifier> ids, Set<Identifier> enabled,
                         Runnable onChange, Consumer<Identifier> onSelect, Supplier<Identifier> target,
                         int maxVisibleRows) {
        this.maxVisibleRows = Math.max(1, maxVisibleRows);
        this.x = x;
        this.y = y;
        this.columns = Math.max(1, columns);
        this.ids = ids;
        this.enabled = enabled;
        this.onChange = onChange;
        this.onSelect = onSelect;
        this.target = target;
    }

    public int rows() {
        return (ids.size() + columns - 1) / columns;
    }

    public int height() {
        return Math.min(rows(), maxVisibleRows) * CELL;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + columns * CELL && my >= y && my < y + height();
    }

    public void scroll(double verticalAmount) {
        scrollRow = Math.clamp(scrollRow - (int) Math.signum(verticalAmount), 0,
                Math.max(0, rows() - maxVisibleRows));
    }

    public void render(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        Identifier hovered = null;
        Identifier selected = target.get();
        for (int i = scrollRow * columns;
             i < ids.size() && i < (scrollRow + maxVisibleRows) * columns; i++) {
            Identifier id = ids.get(i);
            int cx = x + (i % columns) * CELL;
            int cy = y + (i / columns - scrollRow) * CELL;
            boolean on = enabled.contains(id);
            context.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, on ? ON : OFF);
            StructureIcons.drawIcon(context, id, cx + 2, cy + 2);
            if (!on) {
                context.fill(cx + 1, cy + 1, cx + CELL - 3, cy + CELL - 3, DIM);
            }
            if (id.equals(selected)) {
                context.fill(cx, cy, cx + CELL - 2, cy + 1, TARGET);
                context.fill(cx, cy + CELL - 3, cx + CELL - 2, cy + CELL - 2, TARGET);
                context.fill(cx, cy, cx + 1, cy + CELL - 2, TARGET);
                context.fill(cx + CELL - 3, cy, cx + CELL - 2, cy + CELL - 2, TARGET);
            }
            if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) {
                hovered = id;
            }
        }
        if (hovered != null) {
            context.setTooltipForNextFrame(font, Component.literal(StructureIcons.displayName(hovered)), mouseX, mouseY);
        }
        if (rows() > maxVisibleRows) {
            String range = (scrollRow + 1) + "-" + Math.min(rows(), scrollRow + maxVisibleRows)
                    + "/" + rows();
            context.text(font, range, x + columns * CELL - font.width(range) - 3,
                    y + height() - 10, 0xFFA3A3A3, false);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 1 && button != 3) return false;
        if (mx < x || my < y || mx >= x + columns * CELL || my >= y + height()) return false;
        int col = (int) ((mx - x) / CELL);
        int row = (int) ((my - y) / CELL) + scrollRow;
        int index = row * columns + col;
        if (col >= columns || index >= ids.size()) return false;
        Identifier id = ids.get(index);
        if (button == 3) {
            onSelect.accept(id);
            return true;
        }
        if (!enabled.remove(id)) {
            enabled.add(id);
        }
        onChange.run();
        return true;
    }

    public void setAll(boolean on) {
        if (on) {
            enabled.addAll(ids);
        } else {
            enabled.removeAll(ids);
        }
        onChange.run();
    }
}
