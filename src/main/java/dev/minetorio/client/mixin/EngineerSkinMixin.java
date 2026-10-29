package dev.minetorio.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bundled engineer appearance uses the same wide-arm UV model for every player. */
@Mixin(AbstractClientPlayer.class)
public abstract class EngineerSkinMixin {
    private static final PlayerSkin MINETORIO_ENGINEER = new PlayerSkin(
        ResourceLocation.fromNamespaceAndPath("minetorio", "textures/entity/engineer.png"),
        null, null, null, PlayerSkin.Model.WIDE, true);

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void minetorio$engineer(CallbackInfoReturnable<PlayerSkin> cir) {
        cir.setReturnValue(MINETORIO_ENGINEER);
    }
}
