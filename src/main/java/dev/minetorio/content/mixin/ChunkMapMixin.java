package dev.minetorio.content.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.minetorio.content.map.RemoteView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Remote view: while a player looks at a remote place ({@link RemoteView}), vanilla's chunk
 * tracking view for him is centred on the remote focus with the remote radius instead of on his
 * body, so vanilla sends him the chunks there (and their block updates and entities) and drops
 * the rest, exactly as when a player moves. His body, its chunk ticket and gameplay stay home.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @ModifyExpressionValue(method = "updateChunkTracking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos minetorio$remoteCenter(ChunkPos own, @Local(argsOnly = true) ServerPlayer player) {
        ChunkPos remote = RemoteView.trackingCenter(player, level);
        return remote != null ? remote : own;
    }

    @ModifyExpressionValue(method = "updateChunkTracking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getPlayerViewDistance(Lnet/minecraft/server/level/ServerPlayer;)I"))
    private int minetorio$remoteViewDistance(int own, @Local(argsOnly = true) ServerPlayer player) {
        return RemoteView.trackingCenter(player, level) != null ? RemoteView.TRACKING_VIEW_DISTANCE : own;
    }
}
