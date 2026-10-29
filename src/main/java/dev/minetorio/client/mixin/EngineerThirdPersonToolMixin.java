package dev.minetorio.client.mixin;

import dev.minetorio.client.character.EngineerToolVisuals;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandLayer.class)
public abstract class EngineerThirdPersonToolMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack minetorio$miningTool(LivingEntity entity) {
        return EngineerToolVisuals.mainHand(entity,entity.getMainHandItem());
    }
}
