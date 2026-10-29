package dev.minetorio.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Pixel-beveled controls sharing the machine panel palette. */
public final class FactoryButton extends Button {
    private boolean clipped;
    public FactoryButton(int x,int y,int width,int height,Component message,OnPress action) {super(x,y,width,height,message,action,DEFAULT_NARRATION);}
    @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial) {
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        FactoryTheme.inset(g,x,y,w,h);
        g.fill(x+1,y+1,x+w-1,y+h-1,isHoveredOrFocused()?0xFF75613B:0xFF4D5144);
        g.fill(x+1,y+1,x+w-1,y+2,active?FactoryTheme.ACCENT:FactoryTheme.MUTED);
        var font=Minecraft.getInstance().font;
        String text=font.plainSubstrByWidth(getMessage().getString(),w-6);
        boolean nowClipped = font.width(getMessage()) > w-6;
        if (nowClipped != clipped) {
            clipped = nowClipped;
            setTooltip(clipped ? net.minecraft.client.gui.components.Tooltip.create(getMessage()) : null);
        }
        g.drawString(font,text,x+(w-font.width(text))/2,y+(h-8)/2,active?FactoryTheme.TEXT:FactoryTheme.MUTED,false);
    }
}
