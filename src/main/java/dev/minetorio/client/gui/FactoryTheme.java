package dev.minetorio.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/** Original procedural industrial UI. Coordinates are GUI pixels; slots use an 18px cell. */
public final class FactoryTheme {
    public static final int CELL=18, TEXT=0xFFE7DFCB, MUTED=0xFFADA894, ACCENT=0xFFE5B84C;
    public static final int PANEL=0xFF393B38, SLOT=0xFF262923, GREEN=0xFF91B85B, RED=0xFFC36A51;
    private FactoryTheme() {}
    public static void panel(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x+3,y+3,x+w+3,y+h+3,0x88000000);
        g.fill(x,y,x+w,y+h,0xFF121511);
        g.fill(x+1,y+1,x+w-1,y+h-1,0xFF65675B);
        g.fill(x+2,y+2,x+w-2,y+h-2,PANEL);
        g.fill(x+3,y+3,x+w-3,y+18,0xFF45483F);
        g.fill(x+4,y+18,x+w-4,y+19,0xFF1A1D18);
        g.fill(x+4,y+3,x+6,y+16,ACCENT);
        for(int sx=x+8;sx<x+w-6;sx+=w-16) {
            g.fill(sx,y+h-7,sx+2,y+h-5,0xFF81816E);
            g.fill(sx,y+h-6,sx+2,y+h-5,0xFF20231D);
        }
    }
    public static void inset(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+h,0xFF161A14); g.fill(x+1,y+1,x+w-1,y+h-1,0xFF30342B);
        g.fill(x+1,y+h-1,x+w,y+h,0xFF5C6051);
    }
    public static void slot(GuiGraphics g,int x,int y) {
        g.fill(x,y,x+18,y+18,0xFF141811); g.fill(x+1,y+1,x+17,y+17,SLOT);
        g.fill(x+1,y+17,x+18,y+18,0xFF737464); g.fill(x+17,y+1,x+18,y+17,0xFF54584A);
        g.fill(x+2,y+2,x+16,y+3,0xFF20241C);
    }
    public static void slots(GuiGraphics g,int x,int y,Iterable<Slot> slots) {
        for(Slot slot:slots)if(slot.isActive())slot(g,x+slot.x-1,y+slot.y-1);
    }
    public static void bar(GuiGraphics g,int x,int y,int w,int h,double fill,int color) {
        inset(g,x,y,w,h); int n=(int)Math.round(Math.clamp(fill,0,1)*(w-2));
        if(n>0) {g.fill(x+1,y+1,x+1+n,y+h-1,color); g.fill(x+1,y+1,x+1+n,y+2,0x44FFFFFF);}
    }
    public static void title(GuiGraphics g,Font font,Component text,int x,int y) {g.drawString(font,text,x,y,TEXT,false);}
    public static void label(GuiGraphics g,Font font,Component text,int x,int y) {g.drawString(font,text,x,y,MUTED,false);}
}
