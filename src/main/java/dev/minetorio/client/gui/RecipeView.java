package dev.minetorio.client.gui;

import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.core.fluid.FluidData;
import dev.minetorio.core.oil.OilRecipe;
import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.recipe.Recipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/** Recipe presentation only: ghosts never modify the real menu slots. */
public final class RecipeView {
    private RecipeView() {}
    private record Entry(String id, double amount, boolean fluid, boolean available) {}
    private static String amount(double n) { return n == Math.rint(n) ? Long.toString((long)n) : Double.toString(n); }
    private static Component name(Entry e) {
        return e.fluid ? Component.translatable("minetorio.fluid." + e.id) : MachineItems.stack(e.id, 1).getHoverName();
    }
    private static void icon(GuiGraphics g, Font font, Entry e, int x, int y, boolean count) {
        if (e.fluid) {
            FactoryTheme.slot(g, x - 1, y - 1);
            g.fill(x + 2, y + 2, x + 14, y + 14, 0xFF000000 | FluidData.color(e.id));
            g.fill(x + 4, y + 3, x + 7, y + 6, 0x99FFFFFF);
        } else {
            var stack = MachineItems.stack(e.id, 1);
            if (!stack.isEmpty()) g.renderItem(stack, x, y);
        }
        if (count) {
            String n = amount(e.amount);
            g.pose().pushPose(); g.pose().translate(0, 0, 200);
            g.drawString(font, n, x + 16 - font.width(n), y + 9, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }
    public static void ghost(GuiGraphics g, Font font, Slot slot, Ingredient ingredient, int left, int top) {
        if (slot.hasItem()) return;
        int x = left + slot.x, y = top + slot.y;
        icon(g, font, new Entry(ingredient.item(), ingredient.count(), false, true), x, y, false);
        g.pose().pushPose(); g.pose().translate(0, 0, 200);
        g.fill(x, y, x + 16, y + 16, 0x900D1010);
        String n = Integer.toString(ingredient.count());
        g.drawString(font, n, x + 16 - font.width(n), y + 9, 0xFFDBD5C5, true);
        g.pose().popPose();
    }
    public static void tooltip(GuiGraphics g, Font font, Recipe recipe, OilRecipe oil, Map<String,Integer> inventory,
                               Component title, int mouseX, int mouseY, int screenW, int screenH) {
        List<Entry> products = new ArrayList<>(), inputs = new ArrayList<>();
        recipe.outputs().forEach(i -> products.add(new Entry(i.item(), i.count(), false, true)));
        recipe.inputs().forEach(i -> inputs.add(new Entry(i.item(), i.count(), false, inventory == null || inventory.getOrDefault(i.item(), 0) >= i.count())));
        if (oil != null) {
            oil.fluidOutputs().forEach((id,n) -> products.add(new Entry(id,n,true,true)));
            oil.fluidInputs().forEach((id,n) -> inputs.add(new Entry(id,n,true,true)));
        }
        show(g,font,title,products,inputs,recipe.ticks(),mouseX,mouseY,screenW,screenH);
    }
    public static void tooltip(GuiGraphics g, Font font, OilRecipe r, Component title, int mouseX, int mouseY, int screenW, int screenH) {
        List<Entry> products = new ArrayList<>(), inputs = new ArrayList<>();
        r.itemOutputs().forEach(i -> products.add(new Entry(i.item(),i.count(),false,true)));
        r.fluidOutputs().forEach((id,n) -> products.add(new Entry(id,n,true,true)));
        r.itemInputs().forEach(i -> inputs.add(new Entry(i.item(),i.count(),false,true)));
        r.fluidInputs().forEach((id,n) -> inputs.add(new Entry(id,n,true,true)));
        show(g,font,title,products,inputs,r.ticks(),mouseX,mouseY,screenW,screenH);
    }
    private static void show(GuiGraphics g, Font font, Component title, List<Entry> products, List<Entry> inputs, int ticks,
                             int mx,int my,int sw,int sh) {
        int w = Math.min(210, Math.max(120, sw - 12));
        int h = 64 + (products.size() + inputs.size()) * 19;
        float scale = Math.min(1f, Math.min((sw - 8f) / w, (sh - 8f) / h));
        int drawnW = (int)Math.ceil(w * scale), drawnH = (int)Math.ceil(h * scale);
        int x = mx + 12; if (x + drawnW > sw - 4) x = Math.max(4, mx - drawnW - 12);
        int y = Math.max(4, Math.min(my + 8, sh - drawnH - 4));
        g.pose().pushPose(); g.pose().translate(x,y,400); g.pose().scale(scale,scale,1);
        x=0; y=0;
        FactoryTheme.panel(g,x,y,w,h);
        g.drawString(font,font.plainSubstrByWidth(title.getString(),w-16),x+8,y+6,FactoryTheme.TEXT,false);
        g.drawString(font,Component.translatable("minetorio.assembler.time",amount(ticks/20.0)),x+8,y+18,FactoryTheme.MUTED,false);
        g.drawString(font, Component.translatable("minetorio.oil.products"), x+8, y+30, FactoryTheme.ACCENT, false);
        int row=y+41;
        for (Entry e : products) { row(g,font,e,x,row,w); row+=19; }
        g.drawString(font, Component.translatable("minetorio.assembler.ingredients"), x+8, row+1, FactoryTheme.ACCENT, false);
        row+=12;
        for (Entry e : inputs) { row(g,font,e,x,row,w); row+=19; }
        g.pose().popPose();
    }
    private static void row(GuiGraphics g,Font font,Entry e,int x,int y,int width) {
        icon(g,font,e,x+8,y,true);
        String text=amount(e.amount)+" × "+name(e).getString();
        g.drawString(font,font.plainSubstrByWidth(text,width-34),x+29,y+4,e.available?FactoryTheme.TEXT:0xFFFF7065,false);
    }
}
