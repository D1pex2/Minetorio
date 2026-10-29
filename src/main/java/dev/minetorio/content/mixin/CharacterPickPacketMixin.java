package dev.minetorio.content.mixin;

import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject out-of-range pick packets before vanilla dereferences their slot. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CharacterPickPacketMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", ordinal = 0))
    private void minetorio$changeHand(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (packet.getSlot() != player.getInventory().selected) {
            ((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand(false);
        }
    }

    @Inject(method = "handlePickItem", at = @At("HEAD"), cancellable = true)
    private void minetorio$validatePickSlot(ServerboundPickItemPacket packet, CallbackInfo ci) {
        int index = packet.getSlot();
        if (!((index >= 0 && index < 36) || (index >= 41 && index < 85))) ci.cancel();
    }
}
