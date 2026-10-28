package dev.minetorio.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.client.camera.BuildCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Top-down {@link BuildCamera}: the pick follows the mouse cursor instead of the crosshair, and
 * view bobbing / hurt tilt are off (they would move the picture away from the cursor ray).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void minetorio$cursorPick(float partialTicks, CallbackInfo ci) {
        if (BuildCamera.isActive()) BuildCamera.pick(minecraft, partialTicks);
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void minetorio$noBob(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (BuildCamera.isActive()) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void minetorio$noHurtTilt(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (BuildCamera.isActive()) ci.cancel();
    }
}
