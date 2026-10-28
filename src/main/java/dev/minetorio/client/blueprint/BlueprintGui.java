package dev.minetorio.client.blueprint;

import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.content.blueprint.BlueprintStacks;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Drawing shared by the blueprint windows (book, library, name editor): the panel, a grid of
 * entry cells, the player's inventory (main rows, then the hotbar, as in the vanilla inventory)
 * and the icons drawn over a blueprint's or book's slot.
 */
final class BlueprintGui {
    static final int CELL = 18;
    static final int PANEL = 0xF0202228;
    static final int PANEL_BORDER = 0xFF6A6F7A;
    static final int SLOT = 0xFF3A3F4A;
    static final int SLOT_EMPTY = 0xFF2A2E36;
    static final int SELECTED = 0xFFFFD040;
    static final int ACTIVE = 0xFF5FB0FF;
    static final int HOVER = 0x60FFFFFF;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFFA0A0A0;
    static final int INV_W = 9 * CELL;
    static final int INV_H = 4 * CELL + 4;

    private BlueprintGui() {}

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, PANEL_BORDER);
    }

    static void slot(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + CELL - 1, y + CELL - 1, color);
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Cell index of the grid at ({@code x}, {@code y}) with {@code cols} columns under the mouse, or -1. */
    static int cellAt(double mx, double my, int x, int y, int cols, int rows) {
        if (!inside(mx, my, x, y, cols * CELL, rows * CELL)) return -1;
        return (int) ((my - y) / CELL) * cols + (int) ((mx - x) / CELL);
    }

    // --- the inventory panel ----------------------------------------------------------------------

    /** Screen position of inventory slot {@code i} (0..35) in a panel at ({@code x}, {@code y}). */
    static int[] invPos(int i, int x, int y) {
        if (i < 9) return new int[] {x + i * CELL, y + 3 * CELL + 4};
        int r = (i - 9) / 9, c = (i - 9) % 9;
        return new int[] {x + c * CELL, y + r * CELL};
    }

    /** The inventory slot under the mouse, or -1. */
    static int invSlotAt(double mx, double my, int x, int y) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            int[] p = invPos(i, x, y);
            if (inside(mx, my, p[0], p[1], CELL, CELL)) return i;
        }
        return -1;
    }

    /** Draws the inventory; {@code highlight} (or -1) framed; returns the hovered slot or -1. */
    static int drawInventory(GuiGraphics g, Font font, Inventory inv, int x, int y, int mx, int my, int highlight, int dimmed) {
        int hovered = -1;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            int[] p = invPos(i, x, y);
            ItemStack s = inv.getItem(i);
            slot(g, p[0], p[1], BlueprintStacks.isBlueprintLike(s) ? SLOT : SLOT_EMPTY);
            if (i != dimmed) {
                g.renderItem(s, p[0] + 1, p[1] + 1);
                g.renderItemDecorations(font, s, p[0] + 1, p[1] + 1);
            }
            if (i == highlight) g.renderOutline(p[0] - 1, p[1] - 1, CELL + 1, CELL + 1, SELECTED);
            if (inside(mx, my, p[0], p[1], CELL, CELL)) {
                hovered = i;
                g.fill(p[0], p[1], p[0] + CELL - 1, p[1] + CELL - 1, HOVER);
            }
        }
        return hovered;
    }

    // --- entries ----------------------------------------------------------------------------------

    /** Stacks drawn for records (one per record object, rebuilt when a record changes). */
    private static final Map<BlueprintRecord, ItemStack> STACKS = new IdentityHashMap<>();

    static ItemStack stackOf(BlueprintRecord r) {
        if (STACKS.size() > 4096) STACKS.clear();
        return STACKS.computeIfAbsent(r, BlueprintStacks::stack);
    }

    /** Draws {@code entries} as a grid from row {@code firstRow}; returns the hovered entry index or -1. */
    static int drawEntries(GuiGraphics g, Font font, List<BlueprintRecord> entries, int x, int y, int cols, int rows, int firstRow,
                           int selected, int active, int dragged, int mx, int my) {
        int hovered = -1;
        for (int cell = 0; cell < cols * rows; cell++) {
            int i = firstRow * cols + cell;
            int cx = x + (cell % cols) * CELL, cy = y + (cell / cols) * CELL;
            slot(g, cx, cy, i < entries.size() ? SLOT : SLOT_EMPTY);
            if (i < entries.size()) {
                if (i != dragged) {
                    ItemStack s = stackOf(entries.get(i));
                    g.renderItem(s, cx + 1, cy + 1);
                    g.renderItemDecorations(font, s, cx + 1, cy + 1);
                }
                if (i == active) g.renderOutline(cx - 1, cy - 1, CELL + 1, CELL + 1, ACTIVE);
                if (i == selected) g.renderOutline(cx - 1, cy - 1, CELL + 1, CELL + 1, SELECTED);
                if (inside(mx, my, cx, cy, CELL, CELL)) hovered = i;
            }
            if (inside(mx, my, cx, cy, CELL, CELL)) g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, HOVER);
        }
        return hovered;
    }

    /** The item being dragged, under the mouse. */
    static void drawDragged(GuiGraphics g, ItemStack s, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.renderItem(s, mx - 8, my - 8);
        g.pose().popPose();
    }

    /** A name tooltip for a record. */
    static List<Component> tooltip(BlueprintRecord r) {
        return Minecraft.getInstance().player == null ? List.of(BlueprintStacks.name(r))
                : net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stackOf(r));
    }

    // --- icons over the slot (item decorator) ------------------------------------------------------

    /** Draws a blueprint's or book's icons over its slot: one big, or up to four in a 2 × 2 grid. */
    static boolean drawIcons(GuiGraphics g, ItemStack stack, int x, int y) {
        List<Item> icons = BlueprintStacks.meta(stack).iconItems();
        if (icons.isEmpty()) return false;
        g.pose().pushPose();
        g.pose().translate(0, 0, 160);
        if (icons.size() == 1) {
            g.pose().translate(x + 3, y + 3, 0);
            g.pose().scale(0.625f, 0.625f, 1);
            g.renderFakeItem(new ItemStack(icons.getFirst()), 0, 0);
        } else {
            for (int i = 0; i < icons.size(); i++) {
                g.pose().pushPose();
                g.pose().translate(x + (i % 2) * 8, y + (i / 2) * 8 + (icons.size() == 2 ? 4 : 0), 0);
                g.pose().scale(0.5f, 0.5f, 1);
                g.renderFakeItem(new ItemStack(icons.get(i)), 0, 0);
                g.pose().popPose();
            }
        }
        g.pose().popPose();
        return false;
    }

    static String clip(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, width - font.width("...")) + "...";
    }
}
