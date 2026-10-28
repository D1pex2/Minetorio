package dev.minetorio.client.mixin;

import dev.minetorio.client.camera.LayerCutClient;
import dev.minetorio.core.camera.LayerCut;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Layer cut ({@link LayerCutClient}): the snapshot chunk sections are compiled from reports air
 * above the cut, so the hidden blocks, fluids and block entities are left out of the mesh, the faces
 * the cut exposes are drawn, and the section visibility graph treats the cut-away floors as open.
 * The cells above the cut count as open to the sky (light), so cut faces are not black.
 * Only chunk compilation reads this class; the real level is untouched.
 */
@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {
    @Shadow
    @Final
    protected Level level;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void minetorio$cutBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (LayerCut.blockHidden(pos.getY(), LayerCutClient.renderCut())) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void minetorio$cutFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (LayerCut.blockHidden(pos.getY(), LayerCutClient.renderCut())) cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void minetorio$cutBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (LayerCut.blockHidden(pos.getY(), LayerCutClient.renderCut())) cir.setReturnValue(null);
    }

    /** Overrides {@code BlockAndTintGetter.getBrightness}: full sky light above the cut, the real light elsewhere. */
    public int getBrightness(LightLayer layer, BlockPos pos) {
        if (layer == LightLayer.SKY && LayerCut.blockHidden(pos.getY(), LayerCutClient.renderCut())) return 15;
        return this.level.getLightEngine().getLayerListener(layer).getLightValue(pos);
    }
}
