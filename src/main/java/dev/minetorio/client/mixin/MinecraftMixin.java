package dev.minetorio.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.minetorio.client.camera.BuildCamera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Holding the left button keeps breaking under the free cursor of the top-down {@link BuildCamera}. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyExpressionValue(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean minetorio$attackWithFreeCursor(boolean grabbed) {
        return grabbed || BuildCamera.isActive();
    }
}
