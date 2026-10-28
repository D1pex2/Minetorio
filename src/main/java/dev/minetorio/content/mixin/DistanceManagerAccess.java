package dev.minetorio.content.mixin;

import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads a chunk's strongest ticket (GameTests check that the remote view releases its tickets). */
@Mixin(DistanceManager.class)
public interface DistanceManagerAccess {
    @Invoker("getTicketDebugString")
    String minetorio$ticketDebugString(long chunkPos);
}
