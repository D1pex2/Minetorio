package dev.minetorio.client.machineui;

import dev.minetorio.Minetorio;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.client.gui.FactoryButton;
import dev.minetorio.content.machineui.MachineStatusMenu;
import dev.minetorio.content.machineui.MachineUi;
import dev.minetorio.core.fluid.FluidData;
import net.minecraft.client.gui.GuiGraphics;
import dev.minetorio.client.gui.FactoryContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Compact machine panel beside the shared eighty-slot player inventory. */
public final class MachineStatusScreen extends FactoryContainerScreen<MachineStatusMenu> {
    public MachineStatusScreen(MachineStatusMenu menu,Inventory inventory,Component title) {super(menu,inventory,title);imageWidth=406;imageHeight=190;inventoryLabelX=210;inventoryLabelY=20;}
    @Override protected void init() {
        super.init();
        if(menu.kind()==MachineUi.Kind.LAB)addRenderableWidget(new FactoryButton(leftPos+12,topPos+125,172,18,Component.translatable("minetorio.research.title"),button->{
            if(minecraft!=null && minecraft.player!=null) {minecraft.player.closeContainer();minecraft.setScreen(new dev.minetorio.client.research.ResearchScreen());}
        }));
        if(menu.kind()==MachineUi.Kind.PIPE)for(var direction:net.minecraft.core.Direction.values()) {
            int index=direction.ordinal();
            addRenderableWidget(new FactoryButton(leftPos+12+(index%3)*58,topPos+43+(index/3)*22,54,18,Component.translatable("minetorio.machineui.side."+direction.getSerializedName()),button->{
                if(minecraft!=null && minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,index);
            }));
        }
    }
    @Override protected void renderFactory(GuiGraphics g,int x,int y,float partial) {super.renderFactory(g,x,y,partial);renderTooltip(g,x,y);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        String name=font.plainSubstrByWidth(title.getString(),174);
        FactoryTheme.title(g,font,Component.literal(name),10,6);
        FactoryTheme.title(g,font,playerInventoryTitle,210,6);
        FactoryTheme.label(g,font,Component.translatable("minetorio.machineui."+(MachineUi.slotCount(menu.kind())>0?"contents":"monitor")),12,25);
        FactoryTheme.label(g,font,Component.translatable("minetorio.machineui.inventory_size"),210,20);
    }
    private void text(GuiGraphics g,Component text,int x,int y) {FactoryTheme.label(g,font,text,leftPos+x,topPos+y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        FactoryTheme.panel(g,leftPos,topPos,196,imageHeight);FactoryTheme.panel(g,leftPos+202,topPos,204,imageHeight);
        FactoryTheme.slots(g,leftPos,topPos,menu.slots);
        var kind=menu.kind();
        boolean progress=kind==MachineUi.Kind.BURNER_DRILL || kind==MachineUi.Kind.ELECTRIC_DRILL || kind==MachineUi.Kind.LAB || kind==MachineUi.Kind.RADAR || kind==MachineUi.Kind.ACCUMULATOR;
        if(progress) {
            text(g,Component.translatable(kind==MachineUi.Kind.ACCUMULATOR?"minetorio.machineui.charge":"minetorio.machineui.progress",Math.round(menu.progress()*100)),12,69);
            FactoryTheme.bar(g,leftPos+12,topPos+81,172,8,menu.progress(),FactoryTheme.GREEN);
        }
        boolean power=switch(kind) {case BURNER_DRILL,BOILER,ELECTRIC_DRILL,LAB,RADAR,PUMP,PUMPJACK,POLE->true;default->false;};
        if(power) {
            text(g,Component.translatable(kind==MachineUi.Kind.BURNER_DRILL || kind==MachineUi.Kind.BOILER?"minetorio.machineui.fuel":"minetorio.machineui.power",Math.round(menu.satisfaction()*100)),12,94);
            FactoryTheme.bar(g,leftPos+12,topPos+106,172,8,menu.satisfaction(),menu.satisfaction()>0?FactoryTheme.ACCENT:FactoryTheme.RED);
        }
        for(int p=0;p<2;p++)if(menu.fluidCapacity(p)>0) {
            String id=menu.fluid(p);int y=125+p*26;
            Component name=id==null?Component.translatable("minetorio.machineui.empty"):Component.translatable("minetorio.fluid."+id);
            String line=font.plainSubstrByWidth(name.getString(),90)+" "+menu.fluidAmount(p)+"/"+menu.fluidCapacity(p);
            text(g,Component.literal(line),12,y);
            FactoryTheme.bar(g,leftPos+12,topPos+y+11,172,7,(double)menu.fluidAmount(p)/menu.fluidCapacity(p),0xFF000000|FluidData.color(id));
        }
        if(kind==MachineUi.Kind.BURNER_DRILL || kind==MachineUi.Kind.ELECTRIC_DRILL)text(g,Component.translatable("minetorio.machineui.mining_area",menu.value(10),menu.value(10),String.format(java.util.Locale.ROOT,"%.1f",menu.remainingOre()/1000.0)),12,125);
        if(kind==MachineUi.Kind.BURNER_DRILL || kind==MachineUi.Kind.BOILER)text(g,Component.translatable("minetorio.machineui.burner_energy",menu.value(11)),12,kind==MachineUi.Kind.BOILER?69:146);
        if(kind==MachineUi.Kind.ACCUMULATOR) {text(g,Component.literal(menu.value(11)+" / 5000 kJ"),12,95);text(g,Component.translatable("minetorio.machineui.flow."+menu.value(14)),12,110);}
        if(kind==MachineUi.Kind.PIPE)text(g,Component.translatable("minetorio.machineui.closed_sides",Integer.bitCount(menu.value(16))),12,98);
        if(kind==MachineUi.Kind.SOLAR || kind==MachineUi.Kind.ENGINE)text(g,Component.translatable("minetorio.machineui.available_power",menu.availableKw()),12,69);
        if(kind==MachineUi.Kind.SOLAR)text(g,Component.translatable(menu.value(8)>0?"minetorio.machineui.sky_clear":"minetorio.machineui.sky_blocked"),12,86);
        if(kind==MachineUi.Kind.POLE) {text(g,Component.translatable("minetorio.machineui.connections",menu.value(10)),12,43);text(g,Component.translatable("minetorio.machineui.network",menu.availableKw(),menu.demandKw()),12,69);}
        if(kind==MachineUi.Kind.SOLAR || kind==MachineUi.Kind.ACCUMULATOR || kind==MachineUi.Kind.POLE)text(g,Component.translatable(menu.value(13)>0?"minetorio.machineui.connected":"minetorio.machineui.disconnected"),12,172);
        else if(kind!=MachineUi.Kind.PIPE && kind!=MachineUi.Kind.TANK)text(g,Component.translatable(menu.value(8)>0?"minetorio.machineui.active":"minetorio.machineui.waiting"),12,172);
    }
    @EventBusSubscriber(modid=Minetorio.MODID,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {event.register(MachineUi.STATUS.get(),MachineStatusScreen::new);}
    }
}
