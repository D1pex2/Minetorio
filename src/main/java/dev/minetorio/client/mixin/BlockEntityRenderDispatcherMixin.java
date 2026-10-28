package dev.minetorio.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.client.camera.LayerCutClient;
import dev.minetorio.core.camera.LayerCut;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Layer cut ({@link LayerCutClient}): block entities above the cut are not drawn (global ones included). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void minetorio$layerCut(E blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, CallbackInfo ci) {
        int cut = LayerCutClient.renderCut();
        if (cut != LayerCut.NONE && blockEntity.hasLevel() && LayerCut.blockHidden(blockEntity.getBlockPos().getY(), cut)) ci.cancel();
    }
}
