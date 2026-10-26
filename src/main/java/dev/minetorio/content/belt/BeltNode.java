package dev.minetorio.content.belt;

import dev.minetorio.core.belt.LaneSink;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A block entity that moves items on lanes (belt, underground belt, splitter). Nodes are ticked
 * by {@link BeltTicker} downstream-first, so a hand-off always lands on a lane that has already
 * moved this tick and the sub-tick overflow can be passed on (see {@code BeltLane}).
 */
public interface BeltNode {
    /** Block positions this node occupies (where other nodes hand items into it). */
    List<BlockPos> occupied();

    /** Block positions this node hands items into; used only to order the ticking. */
    List<BlockPos> downstream();

    /** Moves the lanes one tick and hands items off downstream. */
    void tickBelt();

    /** Called after every node of the level has ticked (server: client sync, save marking). */
    void afterBeltTick();

    boolean isRemoved();

    /**
     * The receiver for an item travelling in {@code travel} that enters this node at {@code at}
     * on lane {@code side} (relative to {@code travel}), or null if this node does not accept
     * items from that direction. The sink takes the overflow past the sender's lane end.
     */
    @Nullable
    LaneSink incoming(BlockPos at, Direction travel, LaneSide side);
}
