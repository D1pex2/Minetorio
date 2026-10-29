package dev.minetorio.client.oil;

import dev.minetorio.content.oil.SteelChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SteelChestScreen extends AbstractContainerScreen<SteelChestMenu> {
    public SteelChestScreen(SteelChestMenu menu,Inventory inv,Component title) { super(menu,inv,title); imageWidth=176; imageHeight=222; inventoryLabelY=129; }
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffc6c6c6);
        for(var slot:menu.slots) { int sx=leftPos+slot.x,sy=topPos+slot.y; g.fill(sx-1,sy-1,sx+17,sy+17,0xff555555); g.fill(sx,sy,sx+16,sy+16,0xff8b8b8b); }
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) { super.render(g,x,y,partial); renderTooltip(g,x,y); }
}
