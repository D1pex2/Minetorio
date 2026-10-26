package dev.minetorio.client;

import dev.minetorio.Minetorio;
import dev.minetorio.client.belt.BeltRenderer;
import dev.minetorio.client.belt.SplitterRenderer;
import dev.minetorio.client.belt.UndergroundBeltRenderer;
import dev.minetorio.client.inserter.InserterRenderer;
import dev.minetorio.content.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registrations (mod bus). */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MinetorioClient {
    private MinetorioClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSPORT_BELT.get(), BeltRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.UNDERGROUND_BELT.get(), UndergroundBeltRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPLITTER.get(), SplitterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INSERTER.get(), InserterRenderer::new);
    }
}
