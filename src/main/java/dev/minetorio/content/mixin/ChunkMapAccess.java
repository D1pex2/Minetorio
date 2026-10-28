package dev.minetorio.content.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the remote view recompute a player's chunk tracking view right away ({@code RemoteView}). */
@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Invoker("updateChunkTracking")
    void minetorio$updateChunkTracking(ServerPlayer player);
}
