package dev.minetorio.client.oil;

import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.oil.OilMenu;
import dev.minetorio.content.oil.OilProcessingBlock;
import dev.minetorio.content.oil.OilProcessingBlockEntity;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.oil.OilRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Shows all connected fractions, current production status and the recipe's full requirements. */
public final class OilScreen extends AbstractContainerScreen<OilMenu> {
    private int candidate = -1;
    public OilScreen(OilMenu menu,Inventory inv,Component title) { super(menu,inv,title); imageWidth=240; imageHeight=240; inventoryLabelX=39; inventoryLabelY=143; }
    @Override protected void init() {
        super.init(); candidate=menu.selected();
        addRenderableWidget(Button.builder(Component.literal("<"), b->step(-1)).bounds(leftPos+8,topPos+23,20,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b->step(1)).bounds(leftPos+212,topPos+23,20,20).build());
        addRenderableWidget(Button.builder(Component.translatable("minetorio.oil.flush_fluids"), b->{ if(minecraft!=null && minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,-2); }).bounds(leftPos+147,topPos+96,85,20).build());
    }
    private OilRecipe selected() { int i=menu.selected(); return i>=0 && i<OilMenu.RECIPES.size()?OilMenu.RECIPES.get(i):null; }
    private static Component recipeName(OilRecipe recipe) { return Component.translatable("recipe.minetorio."+recipe.id()); }
    private static Component fluidName(String id) { return Component.translatable("minetorio.fluid."+id); }
    private void step(int direction) {
        if(minecraft==null || minecraft.level==null || minecraft.gameMode==null)return;
        var block=minecraft.level.getBlockState(menu.pos()).getBlock(); if(!(block instanceof OilProcessingBlock processing))return;
        int n=OilMenu.RECIPES.size(), current=candidate<0?(direction>0?-1:0):candidate;
        for(int i=0;i<n;i++) { current=Math.floorMod(current+direction,n); OilRecipe r=OilMenu.RECIPES.get(current);
            if(r.kind()==processing.kind() && RecipeLocks.isUnlockedOnClient(r.id())) { candidate=current; minecraft.gameMode.handleInventoryButtonClick(menu.containerId,current); return; }
        }
    }
    private static List<Component> recipeTooltip(OilRecipe recipe) {
        List<Component> lines=new ArrayList<>(); lines.add(recipeName(recipe));
        lines.add(Component.translatable("minetorio.assembler.ingredients").withStyle(ChatFormatting.GRAY));
        recipe.itemInputs().forEach(i->lines.add(Component.literal(i.count()+" × ").append(MachineItems.stack(i.item(),1).getHoverName()).withStyle(ChatFormatting.GRAY)));
        recipe.fluidInputs().forEach((id,n)->lines.add(Component.literal(amount(n)+" × ").append(fluidName(id)).withStyle(ChatFormatting.GRAY)));
        lines.add(Component.translatable("minetorio.oil.products").withStyle(ChatFormatting.GRAY));
        recipe.itemOutputs().forEach(i->lines.add(Component.literal(i.count()+" × ").append(MachineItems.stack(i.item(),1).getHoverName()).withStyle(ChatFormatting.GRAY)));
        recipe.fluidOutputs().forEach((id,n)->lines.add(Component.literal(amount(n)+" × ").append(fluidName(id)).withStyle(ChatFormatting.GRAY)));
        lines.add(Component.translatable("minetorio.assembler.time",String.valueOf(recipe.ticks()/20.0)).withStyle(ChatFormatting.GRAY));
        return lines;
    }
    private static String amount(double n) { return n==(long)n?Long.toString((long)n):Double.toString(n); }
    private void clipped(GuiGraphics g,Component text,int x,int y,int width) {
        String raw=text.getString(); String visible=font.plainSubstrByWidth(raw,width);
        if(visible.length()<raw.length())visible=font.plainSubstrByWidth(raw,width-font.width("…"))+"…";
        g.drawString(font,visible,x,y,0xff202020,false);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffc6c6c6);
        for(var slot:menu.slots) { int x=leftPos+slot.x,y=topPos+slot.y; g.fill(x-1,y-1,x+17,y+17,0xff555555); g.fill(x,y,x+16,y+16,0xff8b8b8b); }
        OilRecipe recipe=selected();
        clipped(g,recipe==null?Component.translatable("minetorio.assembler.select_recipe"):recipeName(recipe),leftPos+33,topPos+29,172);
        g.drawString(font,Component.translatable("minetorio.oil.inputs"),leftPos+12,topPos+47,0xff404040,false);
        g.drawString(font,Component.translatable("minetorio.oil.outputs"),leftPos+128,topPos+47,0xff404040,false);
        for(int i=0;i<5;i++) {
            String id=OilProcessingBlockEntity.portFluid(recipe,i); if(id==null)continue;
            int x=leftPos+(i<2?12:128), y=topPos+59+(i<2?i:i-2)*11;
            clipped(g,fluidName(id),x,y,52);
            g.drawString(font,menu.fluidAmount(i)+(i<2?"/1000":"/100"),x+54,y,0xff202020,false);
        }
        String[] status={"select_recipe","waiting_inputs","no_power","output_full","working"};
        int state=Math.clamp(menu.status(),0,status.length-1);
        clipped(g,Component.translatable("minetorio.oil.status."+status[state]),leftPos+12,topPos+95,130);
        int bar=(int)(Math.clamp(menu.progress(),0,1)*104);
        g.fill(leftPos+12,topPos+108,leftPos+116,topPos+114,0xff555555); g.fill(leftPos+12,topPos+108,leftPos+12+bar,topPos+114,0xff60c040);
        g.drawString(font,Math.round(menu.satisfaction()*100)+"%",leftPos+119,topPos+106,0xff404040,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        super.render(g,x,y,partial);
        OilRecipe recipe=selected();
        if(recipe!=null && isHovering(32,23,175,20,x,y))g.renderComponentTooltip(font,recipeTooltip(recipe),x,y);
        else if(recipe!=null && isHovering(8,47,224,46,x,y))g.renderComponentTooltip(font,recipeTooltip(recipe),x,y);
        else renderTooltip(g,x,y);
    }
}
