package dev.minetorio.client.mixin;

import dev.minetorio.client.camera.BuildCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the camera for the top-down {@link BuildCamera} after vanilla's setup (in any F5 mode). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void minetorio$buildCamera(BlockGetter level, Entity entity, boolean detachedIn, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        if (!BuildCamera.overrides(entity)) return;
        this.detached = true; // draws the player's body
        setRotation(BuildCamera.cameraYaw(), BuildCamera.cameraPitch());
        setPosition(BuildCamera.cameraPosition(entity, partialTick));
    }
}
