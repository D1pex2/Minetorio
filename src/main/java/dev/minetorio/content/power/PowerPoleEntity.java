package dev.minetorio.content.power;

import dev.minetorio.core.power.PoleType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * The block entity of an electric pole of any type: registered with the {@link PowerGridManager}
 * while loaded, wired to the poles the manager tells it ({@link #setConnections}), which the client
 * gets in the update tag and draws as copper wires.
 */
public interface PowerPoleEntity {
    String KEY_WIRES = "wires";

    BlockPos getBlockPos();

    boolean isRemoved();

    PoleType poleType();

    /** The lowest, smallest-x, smallest-z block of the pole's footprint (its key in the grid). */
    BlockPos footprintMin();

    /** Poles this one is wired to, by block entity position (server: as set by the manager; client: as synced). */
    List<BlockPos> connections();

    /** Server: new wire connections from the manager; synced to clients when they changed. */
    void setConnections(Set<BlockPos> poles);

    /**
     * Where wires attach, relative to the block entity's position (in blocks), for a pole standing
     * as placed.
     */
    Vec3 wireAttach();

    /** Sorted connection list of {@code poles}. */
    static List<BlockPos> sorted(Set<BlockPos> poles) {
        List<BlockPos> now = new ArrayList<>(poles);
        now.sort(null);
        return List.copyOf(now);
    }

    static void writeWires(CompoundTag tag, List<BlockPos> wires) {
        tag.putLongArray(KEY_WIRES, wires.stream().mapToLong(BlockPos::asLong).toArray());
    }

    /** The wires in an update tag, or {@code previous} if it has none. */
    static List<BlockPos> readWires(CompoundTag tag, List<BlockPos> previous) {
        if (!tag.contains(KEY_WIRES)) return previous;
        List<BlockPos> list = new ArrayList<>();
        for (long l : tag.getLongArray(KEY_WIRES)) list.add(BlockPos.of(l));
        return List.copyOf(list);
    }
}
