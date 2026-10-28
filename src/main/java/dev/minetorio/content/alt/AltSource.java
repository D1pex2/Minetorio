package dev.minetorio.content.alt;

import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltSync;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity that shows an icon and status in alt mode. The server computes its
 * {@link AltInfo} ({@link #computeAlt()}), checks it every {@value AltSync#PERIOD} ticks and sends
 * it in the update tag only when it changed ({@link #tickAlt}); the client keeps the last value
 * received ({@link #altInfo()}).
 */
public interface AltSource {
    String KEY_ALT = "alt";

    /** Server: the current alt info. */
    AltInfo computeAlt();

    /** Client: the last alt info received (server: the last sent). */
    AltInfo altInfo();

    /**
     * Server tick hook: re-evaluates on this machine's turn and sends a block update when the info
     * changed. {@code sync} is the machine's throttle.
     */
    static void tickAlt(BlockEntity be, AltSync sync) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        BlockPos pos = be.getBlockPos();
        if (!AltSync.due(level.getGameTime(), pos.asLong())) return;
        if (sync.offer(((AltSource) be).computeAlt())) {
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Writes the current info into an update tag (and records it as sent). */
    static void write(CompoundTag tag, AltSource source, AltSync sync) {
        AltInfo info = source.computeAlt();
        sync.offer(info);
        CompoundTag alt = new CompoundTag();
        if (info.icon() != null) alt.putString("icon", info.icon());
        alt.putByte("status", (byte) info.status().ordinal());
        tag.put(KEY_ALT, alt);
    }

    /** Reads the info from an update tag, or {@code previous} if the tag has none. */
    static AltInfo read(CompoundTag tag, AltInfo previous) {
        if (!tag.contains(KEY_ALT)) return previous;
        CompoundTag alt = tag.getCompound(KEY_ALT);
        return AltInfo.of(alt.contains("icon") ? alt.getString("icon") : null,
                dev.minetorio.core.alt.AltStatus.byId(alt.getByte("status")));
    }
}
