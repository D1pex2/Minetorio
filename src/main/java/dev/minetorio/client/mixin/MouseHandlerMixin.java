package dev.minetorio.client.mixin;

import dev.minetorio.client.camera.BuildCamera;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the mouse cursor free (no mouse-look) while the top-down {@link BuildCamera} is on. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void minetorio$keepCursorFree(CallbackInfo ci) {
        if (BuildCamera.isActive()) ci.cancel();
    }
}
