package dev.minetorio.client.mixin;

import dev.minetorio.client.character.EngineerToolVisuals;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandRenderer.class)
public abstract class EngineerFirstPersonToolMixin {
    @ModifyArgs(method="renderHandsWithItems",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void minetorio$miningTool(Args args) {
        if(args.get(3)==InteractionHand.MAIN_HAND) args.set(5,EngineerToolVisuals.mainHand(args.get(0),args.get(5)));
    }
}
