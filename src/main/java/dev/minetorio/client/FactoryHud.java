package dev.minetorio.client;

import dev.minetorio.Minetorio;
import dev.minetorio.client.alt.MachineIndex;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.alt.AltSource;
import dev.minetorio.content.character.CharacterShortcuts;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.core.alt.AltStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Research and nearby actionable alerts, based on the server's existing synchronized state. */
@EventBusSubscriber(modid=Minetorio.MODID,value=Dist.CLIENT)
public final class FactoryHud {
    private FactoryHud() {}
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR,ResourceLocation.fromNamespaceAndPath(Minetorio.MODID,"factory_status"),FactoryHud::render);
    }
    private static void render(GuiGraphics g,DeltaTracker delta) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.options.hideGui) return;
        var research=ClientResearch.state();
        int w=Math.min(196,g.guiWidth()/2-8);
        if(w<90) return;
        FactoryTheme.panel(g,4,4,w,43);
        Component name=research.current().map(t->Component.translatable(ResearchSavedData.nameKey(t.id())))
            .orElseGet(()->Component.translatable("minetorio.hud.choose_research",ModKeys.RESEARCH.getTranslatedKeyMessage()));
        g.drawString(mc.font,mc.font.plainSubstrByWidth(name.getString(),w-18),14,10,FactoryTheme.TEXT,false);
        double progress=research.current().map(t->research.unitsDone()/(double)t.units()).orElse(0.0);
        FactoryTheme.bar(g,12,27,w-16,10,progress,FactoryTheme.GREEN);
        g.drawCenteredString(mc.font,Math.round(progress*100)+"%",4+w/2,28,FactoryTheme.TEXT);
        var alerts=MachineIndex.machines().stream().filter(m->m.be() instanceof AltSource source
            && (source.altInfo().status()==AltStatus.NO_POWER || source.altInfo().status()==AltStatus.NO_FUEL
            || source.altInfo().status()==AltStatus.NO_ORE))
            .sorted(java.util.Comparator.comparingDouble(m->m.be().getBlockPos().distSqr(mc.player.blockPosition())))
            .limit(3).toList();
        if(alerts.isEmpty()) return;
        int rows=mc.player instanceof CharacterShortcuts shortcuts?shortcuts.visibleRows():1;
        int aw=Math.min(204,g.guiWidth()/2-8),x=g.guiWidth()-aw-4,y=g.guiHeight()-rows*20-30-alerts.size()*22;
        FactoryTheme.panel(g,x,y,aw,24+alerts.size()*22);
        FactoryTheme.title(g,mc.font,Component.translatable("minetorio.hud.nearby_alerts"),x+12,y+6);
        for(int i=0;i<alerts.size();i++) {
            var be=alerts.get(i).be();var status=((AltSource)be).altInfo().status();var p=be.getBlockPos();
            String key=switch(status){case NO_FUEL->"no_fuel";case NO_ORE->"no_ore";default->"no_power";};
            Component label=Component.translatable("minetorio.hud."+key,p.getX(),p.getZ());
            g.drawString(mc.font,mc.font.plainSubstrByWidth(label.getString(),aw-16),x+8,y+23+i*22,FactoryTheme.ACCENT,false);
            g.drawString(mc.font,mc.font.plainSubstrByWidth(be.getBlockState().getBlock().getName().getString(),aw-16),x+8,y+33+i*22,FactoryTheme.MUTED,false);
        }
    }
}
