package dev.minetorio.client.mixin;

import dev.minetorio.client.camera.LayerCutClient;
import dev.minetorio.core.camera.LayerCut;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Layer cut ({@link LayerCutClient}): entities standing above the cut ({@link LayerCut#entityHidden}) are not drawn. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void minetorio$layerCut(E entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (LayerCut.entityHidden(entity.getY(), LayerCutClient.renderCut())) cir.setReturnValue(false);
    }
}
