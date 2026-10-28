package dev.minetorio.client.inserter;

import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.inserter.InserterFilterMenu;
import dev.minetorio.core.inserter.ItemFilter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Filter inserter GUI: a row of ghost filter slots (click with an item to set it, with an empty
 * cursor to clear it), the whitelist/blacklist toggle and the player inventory. The slots show the
 * synced client block entity's filter.
 */
public class InserterFilterScreen extends AbstractContainerScreen<InserterFilterMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL = 0xFFC6C6C6;
    private static final int TEXT = 0x404040;
    private static final int HOVER = 0x80FFFFFF;

    private Button mode;

    public InserterFilterScreen(InserterFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        mode = addRenderableWidget(Button.builder(modeText(), b -> clickButton(InserterFilterMenu.BUTTON_TOGGLE_MODE))
                .bounds(leftPos + 38, topPos + InserterFilterMenu.FILTER_Y + 22, 100, 16).build());
    }

    private Component modeText() {
        InserterBlockEntity be = menu.inserter();
        boolean blacklist = be != null && be.filter() != null && be.filter().isBlacklist();
        return Component.translatable(blacklist ? "minetorio.filter_inserter.blacklist" : "minetorio.filter_inserter.whitelist");
    }

    private void clickButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (mode != null) mode.setMessage(modeText());
    }

    /** Filter slot under the mouse, or -1. */
    private int filterSlotAt(double mouseX, double mouseY) {
        int n = menu.filterSlots();
        for (int i = 0; i < n; i++) {
            int x = leftPos + InserterFilterMenu.filterX(i, n);
            int y = topPos + InserterFilterMenu.FILTER_Y;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = filterSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            clickButton(slot);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hovered = filterSlotAt(mouseX, mouseY);
        ItemFilter filter = menu.inserter() == null ? null : menu.inserter().filter();
        if (hovered >= 0 && filter != null && filter.get(hovered) != null && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, ghost(filter.get(hovered)), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    private static ItemStack ghost(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int rows = 3;
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, rows * 18 + 17);
        g.blit(TEXTURE, leftPos, topPos + rows * 18 + 17, 0, 126, imageWidth, 96);
        g.fill(leftPos + 7, topPos + 17, leftPos + 169, topPos + 17 + rows * 18, PANEL);
        int n = menu.filterSlots();
        ItemFilter filter = menu.inserter() == null ? null : menu.inserter().filter();
        int hovered = filterSlotAt(mouseX, mouseY);
        for (int i = 0; i < n; i++) {
            int x = leftPos + InserterFilterMenu.filterX(i, n);
            int y = topPos + InserterFilterMenu.FILTER_Y;
            slotFrame(g, x - 1, y - 1);
            if (filter != null && filter.get(i) != null) {
                ItemStack stack = ghost(filter.get(i));
                if (!stack.isEmpty()) g.renderFakeItem(stack, x, y);
            }
            if (i == hovered) g.fill(x, y, x + 16, y + 16, HOVER);
        }
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("minetorio.filter_inserter.hint"), 8, 62, TEXT, false);
    }
}
