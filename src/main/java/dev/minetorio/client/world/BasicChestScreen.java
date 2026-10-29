package dev.minetorio.client.world;

import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.world.BasicChestMenu;
import dev.minetorio.content.world.BasicChests;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class BasicChestScreen extends dev.minetorio.client.gui.FactoryContainerScreen<BasicChestMenu> {
    public BasicChestScreen(BasicChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 196; imageHeight = 259; inventoryLabelY = 95;
    }
    public static void registerScreens(RegisterMenuScreensEvent event) { event.register(BasicChests.MENU.get(), BasicChestScreen::new); }
    @Override protected void renderBg(GuiGraphics g, float partial, int x, int y) {
        FactoryTheme.panel(g, leftPos, topPos, imageWidth, imageHeight);
        FactoryTheme.slots(g, leftPos, topPos, menu.slots);
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        FactoryTheme.title(g, font, title, titleLabelX, titleLabelY);
        FactoryTheme.label(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
    }
    @Override protected void renderFactory(GuiGraphics g, int x, int y, float partial) { super.renderFactory(g,x,y,partial); renderTooltip(g,x,y); }
}
