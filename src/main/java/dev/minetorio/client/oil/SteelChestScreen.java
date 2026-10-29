package dev.minetorio.client.oil;

import dev.minetorio.content.oil.SteelChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SteelChestScreen extends dev.minetorio.client.gui.FactoryContainerScreen<SteelChestMenu> {
    public SteelChestScreen(SteelChestMenu menu,Inventory inv,Component title) { super(menu,inv,title); imageWidth=196; imageHeight=293; inventoryLabelY=129; }
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y) {
        dev.minetorio.client.gui.FactoryTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);
        dev.minetorio.client.gui.FactoryTheme.slots(g,leftPos,topPos,menu.slots);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        dev.minetorio.client.gui.FactoryTheme.title(g,font,title,titleLabelX,titleLabelY);
        dev.minetorio.client.gui.FactoryTheme.label(g,font,playerInventoryTitle,inventoryLabelX,inventoryLabelY);
    }
    @Override protected void renderFactory(GuiGraphics g,int x,int y,float partial) { super.renderFactory(g,x,y,partial); renderTooltip(g,x,y); }
}
