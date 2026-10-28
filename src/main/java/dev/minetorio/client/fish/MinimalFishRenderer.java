package dev.minetorio.client.fish;

import dev.minetorio.Minetorio;
import dev.minetorio.content.fish.MinimalFishEntity;
import net.minecraft.client.model.CodModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws the minimal fish with the cod model and texture (goes with {@link MinimalFishEntity}). */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public class MinimalFishRenderer extends MobRenderer<MinimalFishEntity, CodModel<MinimalFishEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/cod.png");

    public MinimalFishRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CodModel<>(ctx.bakeLayer(ModelLayers.COD)), 0.3f);
    }

    @Override
    public ResourceLocation getTextureLocation(MinimalFishEntity entity) {
        return TEXTURE;
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MinimalFishEntity.FISH.get(), MinimalFishRenderer::new);
    }
}
