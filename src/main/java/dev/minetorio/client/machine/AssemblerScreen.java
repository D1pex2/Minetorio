package dev.minetorio.client.machine;

import dev.minetorio.content.machine.AssemblerMenu;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.registry.ModPayloads.SelectRecipePayload;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.Units;
import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.recipe.Recipe;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Assembling machine GUI: a scrollable grid of the unlocked CRAFTING recipes (output icons, tooltip
 * with inputs and time; click to choose, sent as {@link SelectRecipePayload}), the recipe's input
 * slots, a progress bar, the output slot and a power indicator (green at full satisfaction, yellow
 * when short, red without power). Drawn with plain fills (placeholder art).
 */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {
    private static final int COLS = 8;
    private static final int ROWS = 3;
    private static final int CELL = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int SCROLL_X = GRID_X + COLS * CELL + 4;
    private static final int BAR_X = 86;
    private static final int BAR_W = 24;
    private static final int POWER_X = 152;
    private static final int POWER_W = 8;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SELECTED = 0x8040C040;
    private static final int HOVERED = 0x80FFFFFF;
    private static final int GREEN = 0xFF3FB93F;
    private static final int YELLOW = 0xFFE0C040;
    private static final int RED = 0xFFC03030;

    private final List<Recipe> recipes = new ArrayList<>();
    private int scrollRow;

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = AssemblerMenu.INVENTORY_Y + 3 * 18 + 4 + 18 + 6;
        inventoryLabelY = AssemblerMenu.INVENTORY_Y - 11;
        for (Recipe r : AssemblerMenu.CRAFTING) if (RecipeLocks.isUnlockedOnClient(r.id())) recipes.add(r);
    }

    private int maxScroll() { return Math.max(0, Mth.positiveCeilDiv(recipes.size(), COLS) - ROWS); }

    /** Recipe under the mouse in the grid, or null. */
    @Nullable
    private Recipe recipeAt(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - leftPos - GRID_X;
        int y = (int) Math.floor(mouseY) - topPos - GRID_Y;
        if (x < 0 || y < 0 || x >= COLS * CELL || y >= ROWS * CELL) return null;
        int index = (scrollRow + y / CELL) * COLS + x / CELL;
        return index < recipes.size() ? recipes.get(index) : null;
    }

    private static ItemStack icon(Recipe r) {
        Ingredient out = r.outputs().get(0);
        return MachineItems.stack(out.item(), out.count());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Recipe r = button == 0 ? recipeAt(mouseX, mouseY) : null;
        if (r != null) {
            if (r != menu.recipe()) PacketDistributor.sendToServer(new SelectRecipePayload(menu.pos(), r.id()));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= GRID_X && x < SCROLL_X + 6 && y >= GRID_Y && y < GRID_Y + ROWS * CELL) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Recipe hovered = recipeAt(mouseX, mouseY);
        if (hovered != null) {
            g.renderComponentTooltip(font, recipeTooltip(hovered), mouseX, mouseY);
        } else if (isHovering(POWER_X, AssemblerMenu.SLOTS_Y, POWER_W, 16, mouseX, mouseY)) {
            g.renderTooltip(font, powerTooltip(), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    private static List<Component> recipeTooltip(Recipe r) {
        List<Component> lines = new ArrayList<>();
        Ingredient out = r.outputs().get(0);
        Component name = MachineItems.stack(out.item(), 1).getHoverName();
        lines.add(out.count() > 1 ? Component.translatable("minetorio.assembler.amount", out.count(), name) : name.copy());
        lines.add(Component.translatable("minetorio.assembler.ingredients").withStyle(ChatFormatting.GRAY));
        for (Ingredient in : r.inputs()) {
            lines.add(Component.translatable("minetorio.assembler.amount", in.count(),
                    MachineItems.stack(in.item(), 1).getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        String seconds = String.valueOf(r.ticks() / (double) Units.TICKS_PER_SECOND);
        lines.add(Component.translatable("minetorio.assembler.time", seconds).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private Component powerTooltip() {
        float s = menu.satisfaction();
        if (s <= 0) return Component.translatable("minetorio.assembler.no_power");
        return Component.translatable("minetorio.assembler.power", Math.round(s * 100));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (menu.recipe() == null) {
            g.drawString(font, Component.translatable("minetorio.assembler.select_recipe"), 8, AssemblerMenu.SLOTS_Y + 4, 0x404040, false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(g, x, y, imageWidth, imageHeight);

        // recipe grid
        Recipe selected = menu.recipe();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int cx = x + GRID_X + col * CELL;
                int cy = y + GRID_Y + row * CELL;
                slotFrame(g, cx, cy);
                int index = (scrollRow + row) * COLS + col;
                if (index >= recipes.size()) continue;
                Recipe r = recipes.get(index);
                if (r == selected) g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, SELECTED);
                ItemStack icon = icon(r);
                g.renderItem(icon, cx + 1, cy + 1);
                g.renderItemDecorations(font, icon, cx + 1, cy + 1);
            }
        }
        Recipe hovered = recipeAt(mouseX, mouseY);
        if (hovered != null) {
            int index = recipes.indexOf(hovered) - scrollRow * COLS;
            int cx = x + GRID_X + (index % COLS) * CELL;
            int cy = y + GRID_Y + (index / COLS) * CELL;
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, HOVERED);
        }
        // scrollbar
        int trackTop = y + GRID_Y;
        int trackHeight = ROWS * CELL;
        g.fill(x + SCROLL_X, trackTop, x + SCROLL_X + 6, trackTop + trackHeight, SLOT_DARK);
        int max = maxScroll();
        int thumb = max == 0 ? trackHeight : Math.max(8, trackHeight * ROWS / (ROWS + max));
        int thumbY = max == 0 ? 0 : (trackHeight - thumb) * scrollRow / max;
        g.fill(x + SCROLL_X, trackTop + thumbY, x + SCROLL_X + 6, trackTop + thumbY + thumb, SLOT_FILL);

        // slots (machine inputs/output and player inventory)
        for (Slot slot : menu.slots) {
            if (slot.isActive()) slotFrame(g, x + slot.x - 1, y + slot.y - 1);
        }

        // progress bar
        int barY = y + AssemblerMenu.SLOTS_Y + 5;
        g.fill(x + BAR_X, barY, x + BAR_X + BAR_W, barY + 6, SHADOW);
        int filled = Mth.ceil(menu.progress() * BAR_W);
        if (filled > 0) g.fill(x + BAR_X, barY, x + BAR_X + filled, barY + 6, LIGHT);

        // power indicator
        float s = menu.satisfaction();
        int color = s >= 0.99f ? GREEN : s > 0 ? YELLOW : RED;
        int py = y + AssemblerMenu.SLOTS_Y;
        g.fill(x + POWER_X - 1, py - 1, x + POWER_X + POWER_W + 1, py + 17, SLOT_DARK);
        g.fill(x + POWER_X, py, x + POWER_X + POWER_W, py + 16, color);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
    }

    /** An 18×18 vanilla-style slot frame with its top-left corner at (x, y). */
    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
    }
}
