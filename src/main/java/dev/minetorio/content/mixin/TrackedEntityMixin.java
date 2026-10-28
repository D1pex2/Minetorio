package dev.minetorio.content.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.minetorio.content.map.RemoteView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Remote view: entities (robots at work) are tracked for a player by their distance to his remote
 * focus while he looks there ({@link RemoteView#trackingPosition}), not to his body.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {
    @ModifyExpressionValue(method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 minetorio$remotePosition(Vec3 own, @Local(argsOnly = true) ServerPlayer player) {
        Vec3 remote = RemoteView.trackingPosition(player);
        return remote != null ? remote : own;
    }
}
