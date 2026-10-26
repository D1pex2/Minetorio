package dev.minetorio.content.power;

import dev.minetorio.core.power.PowerConsumer;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The power connection of an electric machine's block entity: registers it with the
 * {@link PowerGridManager} as a {@link PowerConsumer} and keeps the satisfaction last supplied.
 * The owner calls {@link #onLoad} from {@code onLoad} and {@link #onRemoved} from
 * {@code setRemoved} and {@code onChunkUnloaded}, and runs at {@link #satisfaction()} — 0 outside
 * every pole's supply area (the manager supplies 0 there each tick) and after removal.
 */
public final class ElectricConsumerHelper implements PowerConsumer {
    private final BlockEntity owner;
    private final LongSupplier demand;
    private final Supplier<AABB> footprint;
    private double satisfaction;

    /**
     * @param demand    joules wanted this tick (asked once per tick, after the owner ticked)
     * @param footprint the blocks the machine occupies (see {@link #box})
     */
    public ElectricConsumerHelper(BlockEntity owner, LongSupplier demand, Supplier<AABB> footprint) {
        this.owner = owner;
        this.demand = demand;
        this.footprint = footprint;
    }

    /** The smallest box around the given block positions. */
    public static AABB box(List<BlockPos> blocks) {
        AABB box = new AABB(blocks.get(0));
        for (BlockPos p : blocks) box = box.minmax(new AABB(p));
        return box;
    }

    public void onLoad() {
        if (owner.getLevel() instanceof ServerLevel level) {
            PowerGridManager.get(level).registerConsumer(owner.getBlockPos(), footprint.get(), this);
        }
    }

    public void onRemoved() {
        satisfaction = 0;
        if (owner.getLevel() instanceof ServerLevel level) {
            PowerGridManager grid = PowerGridManager.getIfPresent(level);
            if (grid != null) grid.unregister(owner.getBlockPos(), this);
        }
    }

    /** Satisfaction (0..1) supplied at the end of the previous tick. */
    public double satisfaction() { return satisfaction; }

    @Override
    public long demand() { return demand.getAsLong(); }

    @Override
    public void supply(double satisfaction) { this.satisfaction = satisfaction; }
}
