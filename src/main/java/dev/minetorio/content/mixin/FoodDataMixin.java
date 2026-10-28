package dev.minetorio.content.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No hunger (Factorio has none): the food bar never drains (no exhaustion) and the vanilla food tick
 * (saturation regeneration, regeneration from a full bar, starvation) never runs. Health regenerates by
 * the mod's own rule instead (dev.minetorio.content.player.CharacterHealth).
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void minetorio$noFoodTick(Player player, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void minetorio$noExhaustion(float exhaustion, CallbackInfo ci) {
        ci.cancel();
    }
}
