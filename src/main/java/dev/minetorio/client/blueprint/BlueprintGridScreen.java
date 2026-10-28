package dev.minetorio.client.blueprint;

import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.content.blueprint.BlueprintStacks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * A window with a grid of blueprints/books above the player's inventory (the book window and the
 * library): click an entry to select it, double-click to open it, right-click to edit its name and
 * icons; drag entries to reorder them, drag one onto an inventory slot to take it out, drag a
 * blueprint or book from the inventory into the grid to put it in. Wheel over the grid scrolls;
 * Delete deletes the selection, Ctrl+C copies its blueprint string, Ctrl+V imports one.
 */
abstract class BlueprintGridScreen extends Screen {
    protected static final int COLS = 9;
    protected static final int W = BlueprintGui.INV_W + 16;
    private static final int DRAG_START = 4;
    private static final long DOUBLE_CLICK_MS = 300;

    protected final int rows;
    protected int left, top;
    protected int selected = -1;
    private int firstRow;
    @Nullable
    protected Component message;

    // mouse state
    private int pressEntry = -1, pressSlot = -1;
    private double pressX, pressY;
    private boolean dragging;
    private int lastClickEntry = -1;
    private long lastClickTime;

    protected BlueprintGridScreen(Component title, int rows) {
        super(title);
        this.rows = rows;
    }

    // --- what the grid shows and does --------------------------------------------------------------

    protected abstract List<BlueprintRecord> entries();

    /** The active entry (framed blue), or -1. */
    protected int active() { return -1; }

    /** An inventory slot that cannot be dragged (the open book), or -1. */
    protected int lockedSlot() { return -1; }

    protected abstract void select(int i);

    protected abstract void move(int from, int to);

    protected abstract void toInventory(int i, int slot);

    protected abstract void fromInventory(int slot, int at);

    protected void open(int i) {}

    protected abstract void edit(int i);

    protected abstract void delete(int i);

    protected abstract void export();

    protected abstract void importString();

    /** Adds the buttons; two rows, at {@code y} and {@code y + 20}, between {@code left + 8} and {@code left + W - 8}. */
    protected abstract void addButtons(int y);

    // --- layout ----------------------------------------------------------------------------------

    protected int panelH() { return 16 + rows * BlueprintGui.CELL + 4 + 40 + 4 + BlueprintGui.INV_H + 8; }

    protected int gridX() { return left + 8; }

    protected int gridY() { return top + 16; }

    protected int buttonsY() { return gridY() + rows * BlueprintGui.CELL + 4; }

    protected int invX() { return left + 8; }

    protected int invY() { return top + panelH() - 8 - BlueprintGui.INV_H; }

    /** Width of one of {@code n} buttons in a row. */
    protected int buttonW(int n) { return (W - 16 - 2 * (n - 1)) / n; }

    protected int buttonX(int i, int n) { return left + 8 + i * (buttonW(n) + 2); }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - panelH()) / 2;
        addButtons(buttonsY());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int maxFirstRow() {
        int needed = (entries().size() + 1 + COLS - 1) / COLS; // one free cell to drop into
        return Math.max(0, needed - rows);
    }

    /** The entry index of a grid cell under the mouse (may be past the end), or -1 when not over the grid. */
    private int cellIndex(double mx, double my) {
        int c = BlueprintGui.cellAt(mx, my, gridX(), gridY(), COLS, rows);
        return c < 0 ? -1 : firstRow * COLS + c;
    }

    // --- rendering ---------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderBackground(g, mx, my, partialTick);
        BlueprintGui.panel(g, left, top, W, panelH());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        List<BlueprintRecord> list = entries();
        if (selected >= list.size()) selected = list.size() - 1;
        firstRow = Math.min(firstRow, maxFirstRow());
        g.drawString(font, BlueprintGui.clip(font, getTitle().getString(), W - 60), left + 8, top + 5, BlueprintGui.TEXT, false);
        String count = list.size() + "";
        g.drawString(font, count, left + W - 8 - font.width(count), top + 5, BlueprintGui.TEXT_DIM, false);
        int dragEntry = dragging ? pressEntry : -1;
        int hovered = BlueprintGui.drawEntries(g, font, list, gridX(), gridY(), COLS, rows, firstRow, selected, active(), dragEntry, mx, my);
        if (maxFirstRow() > 0) {
            int barH = rows * BlueprintGui.CELL;
            int knob = Math.max(6, barH * rows / (rows + maxFirstRow()));
            int ky = gridY() + (barH - knob) * firstRow / maxFirstRow();
            g.fill(left + W - 6, ky, left + W - 4, ky + knob, BlueprintGui.PANEL_BORDER);
        }
        Inventory inv = minecraft.player.getInventory();
        int hoveredSlot = BlueprintGui.drawInventory(g, font, inv, invX(), invY(), mx, my, lockedSlot(), dragging ? pressSlot : -1);
        if (message != null) g.drawCenteredString(font, message, width / 2, top + panelH() + 4, BlueprintGui.TEXT);
        if (dragging) {
            ItemStack s = pressEntry >= 0 && pressEntry < list.size() ? BlueprintGui.stackOf(list.get(pressEntry))
                    : pressSlot >= 0 ? inv.getItem(pressSlot) : ItemStack.EMPTY;
            BlueprintGui.drawDragged(g, s, mx, my);
        } else if (hovered >= 0) {
            g.renderComponentTooltip(font, BlueprintGui.tooltip(list.get(hovered)), mx, my);
        } else if (hoveredSlot >= 0 && !inv.getItem(hoveredSlot).isEmpty()) {
            g.renderTooltip(font, inv.getItem(hoveredSlot), mx, my);
        }
    }

    // --- mouse ---------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        List<BlueprintRecord> list = entries();
        int cell = cellIndex(mx, my);
        if (cell >= 0 && cell < list.size()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                edit(cell);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                pressEntry = cell;
                pressX = mx;
                pressY = my;
                return true;
            }
        }
        int slot = BlueprintGui.invSlotAt(mx, my, invX(), invY());
        if (slot >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && slot != lockedSlot()
                && BlueprintStacks.isBlueprintLike(minecraft.player.getInventory().getItem(slot))) {
            pressSlot = slot;
            pressX = mx;
            pressY = my;
            return true;
        }
        return cell >= 0 || slot >= 0;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if ((pressEntry >= 0 || pressSlot >= 0) && !dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) > DRAG_START) dragging = true;
        return dragging || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        int fromEntry = pressEntry, fromSlot = pressSlot;
        boolean wasDragging = dragging;
        pressEntry = pressSlot = -1;
        dragging = false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || (fromEntry < 0 && fromSlot < 0)) return super.mouseReleased(mx, my, button);
        int size = entries().size();
        int cell = cellIndex(mx, my);
        int slot = BlueprintGui.invSlotAt(mx, my, invX(), invY());
        if (wasDragging) {
            if (fromEntry >= 0 && fromEntry < size) {
                if (cell >= 0) {
                    int to = Math.min(cell, size - 1);
                    if (to != fromEntry) {
                        move(fromEntry, to);
                        selected = to;
                    }
                } else if (slot >= 0) {
                    toInventory(fromEntry, slot);
                    selected = -1;
                }
            } else if (fromSlot >= 0 && cell >= 0) {
                fromInventory(fromSlot, Math.min(cell, size));
                selected = Math.min(cell, size);
            }
            return true;
        }
        if (fromEntry >= 0 && fromEntry < size) {
            long now = Util.getMillis();
            boolean twice = lastClickEntry == fromEntry && now - lastClickTime < DOUBLE_CLICK_MS;
            lastClickEntry = fromEntry;
            lastClickTime = now;
            selected = fromEntry;
            select(fromEntry);
            if (twice) open(fromEntry);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (BlueprintGui.inside(mx, my, gridX(), gridY(), COLS * BlueprintGui.CELL, rows * BlueprintGui.CELL)) {
            firstRow = Math.max(0, Math.min(maxFirstRow(), firstRow - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox e && e.isFocused()) return super.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_DELETE && selected >= 0 && selected < entries().size()) {
            delete(selected);
            return true;
        }
        if (Screen.isCopy(key)) {
            export();
            return true;
        }
        if (Screen.isPaste(key)) {
            importString();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** The selected record, or null. */
    @Nullable
    protected BlueprintRecord selectedRecord() {
        List<BlueprintRecord> l = entries();
        return selected >= 0 && selected < l.size() ? l.get(selected) : null;
    }
}
