package dev.minetorio.content.fluid;

import dev.minetorio.core.fluid.FluidGauge;
import dev.minetorio.core.fluid.FluidNetwork;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The segment-node side of a {@link FluidNodeEntity}: its capacity, the fluid it held when saved
 * (put back into the network when it registers), registration with the {@link FluidNetworkManager}
 * and, on the client, the alt-mode gauge received in update tags.
 *
 * <p>Persistence: the network keeps one amount per segment; a node saves its share of it (equal
 * fill level), so the contents survive saving, chunk unloading and reloading.
 */
public final class FluidNode {
    private static final String KEY_FLUID = "fluid";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_GAUGE_FLUID = "gauge_fluid";
    private static final String KEY_GAUGE_LEVEL = "gauge_level";

    private final BlockEntity owner;
    private final double capacity;
    @Nullable
    private String savedFluid;
    private double savedAmount;
    private FluidGauge clientGauge = FluidGauge.EMPTY;

    public FluidNode(BlockEntity owner, double capacity) {
        this.owner = owner;
        this.capacity = capacity;
    }

    public double capacity() { return capacity; }

    public long key() { return owner.getBlockPos().asLong(); }

    @Nullable
    private FluidNetworkManager manager() {
        return owner.getLevel() instanceof ServerLevel level ? FluidNetworkManager.getIfPresent(level) : null;
    }

    /** The segment this node is in, or null (client, or not registered yet). */
    @Nullable
    public FluidNetwork.Segment segment() {
        FluidNetworkManager m = manager();
        return m == null ? null : m.network().segment(key());
    }

    /** Like {@link #segment()}, but applies queued network changes first (a node placed this tick is registered). */
    @Nullable
    public FluidNetwork.Segment liveSegment() {
        if (!(owner.getLevel() instanceof ServerLevel level) || owner.isRemoved()) return null;
        FluidNetworkManager m = FluidNetworkManager.get(level);
        if (!m.network().has(key())) m.queueNode(owner.getBlockPos());
        m.flush();
        return m.network().segment(key());
    }

    /** Fluid of the node (its segment's), or the saved one before registration. */
    @Nullable
    public String fluid() {
        FluidNetwork.Segment s = segment();
        return s != null ? s.fluid() : savedFluid;
    }

    /** This node's share of its segment's contents, or the saved amount before registration. */
    public double amount() {
        FluidNetwork.Segment s = segment();
        return s != null ? s.shareOf(capacity) : savedAmount;
    }

    /** Server: the gauge from the live segment. Client: the last received. */
    public FluidGauge gauge() {
        if (owner.getLevel() == null || owner.getLevel().isClientSide()) return clientGauge;
        FluidNetwork.Segment s = segment();
        return s != null ? FluidGauge.of(s) : FluidGauge.of(savedFluid, capacity <= 0 ? 0 : savedAmount / capacity);
    }

    /** What the network takes when it registers this node (then forgotten: the network holds it). */
    @Nullable
    String takeSavedFluid() { return savedFluid; }

    double takeSavedAmount() { return savedAmount; }

    void forgetSaved() {
        savedFluid = null;
        savedAmount = 0;
    }

    public void onLoad() {
        if (owner.getLevel() instanceof ServerLevel level) FluidNetworkManager.get(level).queueNode(owner.getBlockPos());
    }

    public void onRemoved() {
        FluidNetworkManager m = manager();
        if (m != null) {
            // keep what it held, in case the block entity is saved after this (chunk unload)
            savedFluid = fluid();
            savedAmount = amount();
            m.queueNode(owner.getBlockPos());
        }
    }

    public void save(CompoundTag tag) {
        String f = fluid();
        double a = amount();
        if (f != null && a > 0) {
            tag.putString(KEY_FLUID, f);
            tag.putDouble(KEY_AMOUNT, a);
        }
    }

    public void load(CompoundTag tag) {
        if (tag.contains(KEY_FLUID)) {
            savedFluid = tag.getString(KEY_FLUID);
            savedAmount = tag.getDouble(KEY_AMOUNT);
        }
        if (tag.contains(KEY_GAUGE_LEVEL)) {
            clientGauge = new FluidGauge(tag.contains(KEY_GAUGE_FLUID) ? tag.getString(KEY_GAUGE_FLUID) : null, tag.getInt(KEY_GAUGE_LEVEL));
        }
    }

    /** Adds the gauge to an update tag. */
    public void writeUpdate(CompoundTag tag) {
        writeGauge(tag, gauge());
    }

    public static void writeGauge(CompoundTag tag, FluidGauge g) {
        if (g.fluid() != null) tag.putString(KEY_GAUGE_FLUID, g.fluid());
        tag.putInt(KEY_GAUGE_LEVEL, g.level());
    }

    /** Reads a gauge written by {@link #writeGauge}, or {@code previous} if the tag has none. */
    public static FluidGauge readGauge(CompoundTag tag, FluidGauge previous) {
        if (!tag.contains(KEY_GAUGE_LEVEL)) return previous;
        return new FluidGauge(tag.contains(KEY_GAUGE_FLUID) ? tag.getString(KEY_GAUGE_FLUID) : null, tag.getInt(KEY_GAUGE_LEVEL));
    }
}
