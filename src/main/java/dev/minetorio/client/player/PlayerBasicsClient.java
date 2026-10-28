package dev.minetorio.client.player;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.Minetorio;
import dev.minetorio.content.player.CharacterCorpseEntity;
import dev.minetorio.content.player.PlayerBasics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client registrations of the Factorio player (mod bus): the character corpse drawn as a chest. See also {@link NoHungerHud}. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class PlayerBasicsClient {
    private PlayerBasicsClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PlayerBasics.CHARACTER_CORPSE.get(), CorpseRenderer::new);
    }

    /** The corpse: a chest on the ground, with the dead player's name above it. */
    static final class CorpseRenderer extends EntityRenderer<CharacterCorpseEntity> {
        CorpseRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.4f;
        }

        @Override
        public void render(CharacterCorpseEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
            pose.scale(0.8f, 0.8f, 0.8f);
            pose.translate(-0.5, 0, -0.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.CHEST.defaultBlockState(), pose, buffers, light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }

        @Override
        @SuppressWarnings("deprecation")
        public ResourceLocation getTextureLocation(CharacterCorpseEntity entity) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }
}
