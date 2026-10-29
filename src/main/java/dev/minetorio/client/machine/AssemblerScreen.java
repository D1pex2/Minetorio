package dev.minetorio.client.machine;

import dev.minetorio.content.machine.AssemblerMenu;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.registry.ModPayloads.SelectRecipePayload;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.Units;
import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.data.OilData;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.client.gui.FactoryContainerScreen;
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
public class AssemblerScreen extends FactoryContainerScreen<AssemblerMenu> {
    private static final int COLS = 8;
    private static final int ROWS = 3;
    private static final int CELL = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 24;
    private static final int SCROLL_X = GRID_X + COLS * CELL + 4;
    private static final int BAR_X = 101;
    private static final int BAR_W = 10;
    private static final int POWER_X = 152;
    private static final int POWER_W = 8;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = FactoryTheme.ACCENT;
    private static final int SELECTED = 0x807A642C;
    private static final int HOVERED = 0x44E5B84C;
    private static final int GREEN = 0xFF3FB93F;
    private static final int YELLOW = 0xFFE0C040;
    private static final int RED = 0xFFC03030;

    private final List<Recipe> recipes = new ArrayList<>();
    private int scrollRow;

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 406;
        imageHeight = 190;
        inventoryLabelX=210; inventoryLabelY=6;
        refreshRecipes();
    }

    private void refreshRecipes() {
        recipes.clear();
        for(Recipe r:AssemblerMenu.CRAFTING) if(RecipeLocks.isUnlockedOnClient(r.id())
                && (OilData.recipe(r.id())==null || menu.supportsFluids())) recipes.add(r);
        scrollRow=Math.min(scrollRow,maxScroll());
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
    protected boolean factoryMouseClicked(double mouseX, double mouseY, int button) {
        refreshRecipes();
        if(button==0 && menu.supportsFluids() && isHovering(8,114,144,11,mouseX,mouseY)) {
            PacketDistributor.sendToServer(new SelectRecipePayload(menu.pos(),AssemblerBlockEntity.FLUSH_RECIPE));
            return true;
        }
        Recipe r = button == 0 ? recipeAt(mouseX, mouseY) : null;
        if (r != null) {
            if (r != menu.recipe()) PacketDistributor.sendToServer(new SelectRecipePayload(menu.pos(), r.id()));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.factoryMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean factoryMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= GRID_X && x < SCROLL_X + 6 && y >= GRID_Y && y < GRID_Y + ROWS * CELL) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.factoryMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderFactory(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refreshRecipes();
        super.renderFactory(g, mouseX, mouseY, partialTick);
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
        var oil=OilData.recipe(r.id());
        if(oil!=null) {
            oil.fluidInputs().forEach((id,n)->lines.add(Component.translatable("minetorio.assembler.amount",n.intValue(),
                    Component.translatable("fluid.minetorio."+id)).withStyle(ChatFormatting.AQUA)));
            oil.fluidOutputs().forEach((id,n)->lines.add(Component.translatable("minetorio.assembler.fluid_output",n.intValue(),
                    Component.translatable("fluid.minetorio."+id)).withStyle(ChatFormatting.AQUA)));
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
        FactoryTheme.title(g,font,title,10,6);
        FactoryTheme.label(g,font,playerInventoryTitle,inventoryLabelX,inventoryLabelY);
        if (menu.recipe() == null) {
            g.drawString(font, Component.translatable("minetorio.assembler.select_recipe"), 8, AssemblerMenu.SLOTS_Y + 4, FactoryTheme.TEXT, false);
        }
        var oil=menu.recipe()==null?null:OilData.recipe(menu.recipe().id());
        if(oil!=null) {
            String in=oil.fluidInputs().isEmpty()?"—":menu.inputFluidAmount()+"/1000";
            String out=oil.fluidOutputs().isEmpty()?"—":menu.outputFluidAmount()+"/1000";
            g.drawString(font,Component.translatable("minetorio.assembler.fluids",in,out),8,101,FactoryTheme.MUTED,false);
            g.drawString(font,Component.translatable("minetorio.assembler.flush_fluids"),8,114,FactoryTheme.ACCENT,false);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(g, x, y, 196, imageHeight);
        panel(g,x+202,y,204,imageHeight);

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

        FactoryTheme.label(g,font,Component.translatable("minetorio.machineui.progress",Math.round(menu.progress()*100)),x+8,y+135);
        FactoryTheme.bar(g,x+8,y+147,176,7,menu.progress(),FactoryTheme.GREEN);
        FactoryTheme.label(g,font,Component.translatable("minetorio.machineui.power",Math.round(menu.satisfaction()*100)),x+8,y+160);
        FactoryTheme.bar(g,x+8,y+173,176,7,menu.satisfaction(),FactoryTheme.ACCENT);

        // power indicator
        float s = menu.satisfaction();
        int color = s >= 0.99f ? GREEN : s > 0 ? YELLOW : RED;
        int py = y + AssemblerMenu.SLOTS_Y;
        g.fill(x + POWER_X - 1, py - 1, x + POWER_X + POWER_W + 1, py + 17, SLOT_DARK);
        g.fill(x + POWER_X, py, x + POWER_X + POWER_W, py + 16, color);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        FactoryTheme.panel(g,x,y,w,h);
    }

    /** An 18×18 vanilla-style slot frame with its top-left corner at (x, y). */
    private static void slotFrame(GuiGraphics g, int x, int y) {
        FactoryTheme.slot(g,x,y);
    }
}
