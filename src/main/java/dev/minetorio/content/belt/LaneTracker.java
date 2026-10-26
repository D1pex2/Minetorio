package dev.minetorio.content.belt;

import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.belt.LaneSink;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Per-block-entity bookkeeping shared by belts, underground belts and splitters.
 *
 * <p>Server: detects composition changes (an item entered or left one of the lanes) and sends the
 * block entity's update packet, at most once every {@link #MIN_SYNC_INTERVAL} ticks; marks the
 * chunk unsaved while items move.
 *
 * <p>Client: lanes are ticked locally for smooth motion; this remembers each item's position before
 * the tick so the renderer can interpolate with the partial tick. Server snapshots replace the
 * lanes ({@link #snap}); matching items keep their interpolation.
 */
public final class LaneTracker {
    /** Minimum ticks between two composition syncs of one block entity. */
    public static final int MIN_SYNC_INTERVAL = 4;
    private static final double SNAP_MATCH = 0.35;

    private final Map<LaneItem, Double> prev = new IdentityHashMap<>();
    private int lastSignature;
    private boolean syncPending;
    private long lastSync = Long.MIN_VALUE / 2;

    /** Start of this node's tick (client): forget last tick's positions. */
    public void beginTick(Level level) {
        if (level.isClientSide()) prev.clear();
    }

    /** Ticks {@code lane}; on the client records where each surviving item was before the move. */
    public void tick(Level level, BeltLane lane, double speed, LaneSink next) {
        if (!level.isClientSide()) {
            lane.tick(speed, next);
            return;
        }
        List<LaneItem> before = lane.items();
        lane.tick(speed, next);
        List<LaneItem> after = lane.items();
        // Items only leave from the front during a tick, so after[i] is before[i + removed].
        int removed = before.size() - after.size();
        if (removed < 0) return;
        for (int i = 0; i < after.size(); i++) prev.put(after.get(i), before.get(i + removed).pos());
    }

    /** Interpolated render position of {@code it}; items without a recorded start slide in from behind. */
    public double renderPos(LaneItem it, double speed, float partialTick) {
        Double from = prev.get(it);
        double start = from != null ? from : Math.max(0, it.pos() - speed);
        return start + (it.pos() - start) * partialTick;
    }

    /** Client: lanes were replaced by a server snapshot; carry interpolation over to matching items. */
    public void snap(List<BeltLane> oldLanes, List<BeltLane> newLanes) {
        Map<LaneItem, Double> carried = new IdentityHashMap<>();
        for (int l = 0; l < newLanes.size() && l < oldLanes.size(); l++) {
            List<LaneItem> olds = oldLanes.get(l).items();
            for (LaneItem n : newLanes.get(l).items()) {
                LaneItem best = null;
                for (LaneItem o : olds) {
                    if (!o.item().equals(n.item()) || Math.abs(o.pos() - n.pos()) > SNAP_MATCH) continue;
                    if (best == null || Math.abs(o.pos() - n.pos()) < Math.abs(best.pos() - n.pos())) best = o;
                }
                if (best != null) carried.put(n, prev.getOrDefault(best, best.pos()));
            }
        }
        prev.clear();
        prev.putAll(carried);
    }

    /** Server: after all nodes ticked; syncs composition changes and keeps the chunk marked for saving. */
    public void afterTick(BlockEntity be, List<BeltLane> lanes) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        int sig = signature(lanes);
        boolean any = false;
        for (BeltLane lane : lanes) any |= !lane.isEmpty();
        if (sig != lastSignature) {
            lastSignature = sig;
            syncPending = true;
            any = true;
        }
        if (any) level.blockEntityChanged(be.getBlockPos());
        long now = level.getGameTime();
        if (syncPending && now - lastSync >= MIN_SYNC_INTERVAL) {
            syncPending = false;
            lastSync = now;
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Forces a sync on the next {@link #afterTick} (e.g. after a shape or link change). */
    public void requestSync() {
        syncPending = true;
    }

    private static int signature(List<BeltLane> lanes) {
        int h = 1;
        for (BeltLane lane : lanes) {
            h = 31 * h + lane.items().size();
            for (LaneItem it : lane.items()) h = 31 * h + it.item().hashCode();
        }
        return h;
    }
}
