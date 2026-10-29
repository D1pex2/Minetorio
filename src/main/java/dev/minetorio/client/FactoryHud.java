package dev.minetorio.client;

import dev.minetorio.Minetorio;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.ResearchSavedData;
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

/** Research progress based on the server's existing synchronized state. */
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
    }
}
