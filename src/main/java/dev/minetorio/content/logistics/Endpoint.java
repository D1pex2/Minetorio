package dev.minetorio.content.logistics;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Where a robot picks up or drops: a logistic chest, a player, or a ghost (personal construction). */
public record Endpoint(Type type, @Nullable BlockPos pos, @Nullable UUID player) {
    public enum Type { CHEST, PLAYER, GHOST }

    public static Endpoint chest(BlockPos pos) { return new Endpoint(Type.CHEST, pos.immutable(), null); }

    public static Endpoint player(UUID id) { return new Endpoint(Type.PLAYER, null, id); }

    public static Endpoint ghost(BlockPos pos) { return new Endpoint(Type.GHOST, pos.immutable(), null); }

    /** The node id used by the planner and the delivery ledger. */
    public String nodeId() {
        return type == Type.PLAYER ? LogisticsManager.playerId(player) : LogisticsManager.chestId(pos);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.name());
        if (pos != null) t.put("pos", NbtUtils.writeBlockPos(pos));
        if (player != null) t.putUUID("player", player);
        return t;
    }

    @Nullable
    public static Endpoint load(CompoundTag t) {
        try {
            Type type = Type.valueOf(t.getString("type"));
            BlockPos pos = NbtUtils.readBlockPos(t, "pos").orElse(null);
            UUID player = t.hasUUID("player") ? t.getUUID("player") : null;
            if (type == Type.PLAYER ? player == null : pos == null) return null;
            return new Endpoint(type, pos, player);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
