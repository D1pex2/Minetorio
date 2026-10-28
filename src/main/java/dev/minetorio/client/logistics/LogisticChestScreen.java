package dev.minetorio.client.logistics;

import dev.minetorio.content.logistics.LogisticChestMenu;
import dev.minetorio.content.logistics.RequestMenu;
import dev.minetorio.core.logistics.LogisticMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Logistic chest GUI: requester and buffer chests show their request row and count editor (a requester
 * also "request from buffer chests"), a storage chest its filter slot, then the chest and the inventory.
 */
public class LogisticChestScreen extends RequestScreen<LogisticChestMenu> {
    private Button fromBuffers;

    public LogisticChestScreen(LogisticChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.inventoryY() + 83);
    }

    @Override
    protected int editY() {
        return menu.mode().requestSlots() > 1 ? LogisticChestMenu.EDIT_Y : -1;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.mode() == LogisticMode.REQUESTER) {
            fromBuffers = addRenderableWidget(Button.builder(bufferText(), b -> clickButton(RequestMenu.BUTTON_TOGGLE))
                    .bounds(leftPos + 108, topPos + LogisticChestMenu.EDIT_Y - 1, 62, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("minetorio.logistics.from_buffers.tooltip")))
                    .build());
        }
    }

    private Component bufferText() {
        return Component.translatable(menu.toggle() ? "minetorio.logistics.from_buffers.on" : "minetorio.logistics.from_buffers.off");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (fromBuffers != null) fromBuffers.setMessage(bufferText());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (menu.mode() == LogisticMode.STORAGE) {
            g.drawString(font, Component.translatable("minetorio.logistics.filter"), 30, LogisticChestMenu.REQUEST_Y + 4, TEXT, false);
        }
    }
}
