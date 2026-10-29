package dev.minetorio.client.machine;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.machine.StoneFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import dev.minetorio.client.gui.FactoryContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
/** Original industrial furnace panel, backed by the live fuel, input and output slots. */
public class StoneFurnaceScreen extends FactoryContainerScreen<StoneFurnaceMenu> {
 public StoneFurnaceScreen(StoneFurnaceMenu menu,Inventory inventory,Component title) {super(menu,inventory,title);imageWidth=406;imageHeight=190;inventoryLabelX=210;inventoryLabelY=6;}
 @Override protected void renderFactory(GuiGraphics g,int x,int y,float partial) {
  super.renderFactory(g,x,y,partial);
  var input=menu.getSlot(1).getItem();
  var recipe=input.isEmpty()?null:dev.minetorio.core.data.Phase1Data.recipes().smeltingFor(dev.minetorio.content.machine.MachineItems.id(input)).orElse(null);
  if(recipe!=null && (isHovering(56,25,18,18,x,y)||isHovering(116,35,18,18,x,y)))
   dev.minetorio.client.gui.RecipeView.tooltip(g,font,recipe,null,null,dev.minetorio.content.machine.MachineItems.stack(recipe.outputs().getFirst().item(),1).getHoverName(),x,y,width,height);
  else renderTooltip(g,x,y);
 }
 @Override protected void renderLabels(GuiGraphics g,int x,int y) {FactoryTheme.title(g,font,title,10,6);FactoryTheme.label(g,font,playerInventoryTitle,210,6);}
 @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
  FactoryTheme.panel(g,leftPos,topPos,196,imageHeight);FactoryTheme.panel(g,leftPos+202,topPos,204,imageHeight);FactoryTheme.slots(g,leftPos,topPos,menu.slots);
  var input=menu.getSlot(1).getItem();
  if (!input.isEmpty()) {
   var recipe=dev.minetorio.core.data.Phase1Data.recipes().smeltingFor(dev.minetorio.content.machine.MachineItems.id(input)).orElse(null);
   if(recipe!=null && !recipe.outputs().isEmpty()) dev.minetorio.client.gui.RecipeView.ghost(g,font,menu.getSlot(2),recipe.outputs().getFirst(),leftPos,topPos);
  }
  FactoryTheme.bar(g,leftPos+80,topPos+39,28,9,menu.progress(),FactoryTheme.GREEN);
  g.drawString(font,">",leftPos+110,topPos+39,FactoryTheme.ACCENT,false);
  FactoryTheme.bar(g,leftPos+42,topPos+54,7,16,menu.isLit()?1:0,FactoryTheme.ACCENT);
  FactoryTheme.label(g,font,Component.translatable("minetorio.machineui.input"),leftPos+8,topPos+28);
  FactoryTheme.label(g,font,Component.translatable("minetorio.machineui.fuel_slot"),leftPos+8,topPos+73);
  FactoryTheme.label(g,font,Component.translatable("minetorio.machineui.progress",Math.round(menu.progress()*100)),leftPos+8,topPos+88);
  FactoryTheme.bar(g,leftPos+8,topPos+101,176,9,menu.progress(),FactoryTheme.GREEN);
 }
}
