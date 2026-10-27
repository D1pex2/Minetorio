package dev.minetorio.content.inserter;

import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.InserterLogic;
import dev.minetorio.core.inserter.InserterType;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.machine.Burner;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Inserter state: the core {@link InserterLogic} (the hand holds a stack key, so data components
 * survive) and, for the burner inserter, its own {@link Burner}.
 *
 * <p><b>Energy.</b> Burner: the arm moves on what the burner delivers; it burns only while the arm
 * moves (or is about to pick something up). When it carries a fuel item and its own fuel slot is
 * empty, it keeps that item for itself. It starts without fuel. Electric inserters draw power
 * through an {@link ElectricConsumerHelper} and move at the satisfaction the grid supplied at the
 * end of the previous tick — every consumer in a network gets the network-wide satisfaction, even
 * at zero demand, so an idle inserter can start a swing; outside every pole's area it is 0.
 *
 * <p><b>Client sync.</b> The server sends the logic state (phase, arm leg, held key) plus the arm
 * speed ({@code rate}) when the item in the hand changes (pickup, drop, kept as fuel) or the speed
 * changes; the client then ticks its own copy of the logic with an empty source and a refusing
 * target, which reproduces the arm motion between those events (the arm goes to the target and
 * waits there, or returns and waits at the pickup). The renderer interpolates with the partial tick.
 */
public class InserterBlockEntity extends BlockEntity {
    private static final String KEY_LOGIC = "logic";
    private static final String KEY_BURNER = "burner";
    private static final String KEY_RATE = "rate";
    /** Re-sync the arm speed when it drifts this much from what the client was told. */
    private static final double RATE_SYNC_DELTA = 0.05;

    private final InserterType type;
    private final InserterLogic logic;
    @Nullable
    private final Burner burner;

    /** Grid connection; null for the burner inserter. */
    @Nullable
    private final ElectricConsumerHelper power;

    // sync bookkeeping (server) / arm motion (client)
    @Nullable
    private String syncedHeld;
    private double rate;
    private boolean working;
    private boolean syncedOnce;
    private double prevArm;

    public InserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INSERTER.get(), pos, state);
        this.type = ((InserterBlock) state.getBlock()).type();
        this.logic = new InserterLogic(type);
        this.burner = type.burner ? new Burner(type.powerPerTickJ, Phase1Data.fuelValues()) : null;
        this.power = type.burner ? null : new ElectricConsumerHelper(this, logic::powerDemand, () -> new AABB(worldPosition));
    }

    public InserterType type() { return type; }

    public InserterLogic logic() { return logic; }

    /** The burner inserter's fuel slot; null for electric inserters. */
    @Nullable
    public Burner burner() { return burner; }

    public Direction facing() { return getBlockState().getValue(InserterBlock.FACING); }

    /** Where items are picked up: {@code reach} blocks behind. */
    public BlockPos pickupPos() { return worldPosition.relative(facing().getOpposite(), type.reach); }

    /** Where items are dropped: {@code reach} blocks ahead. */
    public BlockPos dropPos() { return worldPosition.relative(facing(), type.reach); }

    /** Whether the arm is moving under power (synced to the client, for animation). */
    public boolean isWorking() { return working; }

    // --- power -------------------------------------------------------------------------------------

    /** Joules wanted this tick from the grid (0 for the burner inserter). */
    public long demand() { return power == null ? 0 : power.demand(); }

    /** Satisfaction last supplied by the grid (0 for the burner inserter). */
    public double satisfaction() { return power == null ? 0 : power.satisfaction(); }

    @Override
    public void onLoad() {
        super.onLoad();
        if (power != null) power.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (power != null) power.onRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (power != null) power.onRemoved();
    }

    /** Puts fuel into the burner's slot by hand; returns the amount accepted (0 for electric inserters). */
    public int addFuel(String item, int count) {
        if (burner == null) return 0;
        int accepted = burner.insertFuel(item, count, false);
        if (accepted > 0) setChanged();
        return accepted;
    }

    // --- ticking -----------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, InserterBlockEntity be) {
        be.tickServer(level);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, InserterBlockEntity be) {
        be.prevArm = be.logic.armProgress();
        be.logic.tick(InserterEndpoints.NO_SOURCE, InserterEndpoints.NO_TARGET, be.rate);
    }

    private void tickServer(Level level) {
        Direction f = facing();
        // "from" = the side of the endpoint block the inserter stands on.
        PickupSource src = Objects.requireNonNullElse(InserterEndpoints.source(level, pickupPos(), f), InserterEndpoints.NO_SOURCE);
        DropTarget dst = Objects.requireNonNullElse(InserterEndpoints.target(level, dropPos(), f.getOpposite()), InserterEndpoints.NO_TARGET);
        keepFuel();
        double satisfaction = burner != null ? burnerSatisfaction(src, dst) : power.satisfaction();
        String heldBefore = logic.held();
        double armBefore = logic.armProgress();
        logic.tick(src, dst, satisfaction);
        keepFuel();
        if (!Objects.equals(heldBefore, logic.held()) || armBefore != logic.armProgress() || burner != null && satisfaction > 0) {
            level.blockEntityChanged(worldPosition);
        }
        sync(level, satisfaction);
    }

    /**
     * Burns fuel only while the arm moves or is about to pick an item up (the logic picks up and
     * starts moving in the same tick); waiting at either end costs nothing.
     */
    private double burnerSatisfaction(PickupSource src, DropTarget dst) {
        boolean starting = logic.held() == null && !logic.isMoving() && logic.armProgress() == 0
                && src.peek(dst::wants).isPresent();
        return logic.isMoving() || starting ? burner.draw() : 0;
    }

    /**
     * Burner inserter carrying fuel while its own fuel slot is empty: keeps that item as fuel, unless
     * it has data components (the fuel slot stores plain item ids, the data would be lost).
     */
    private void keepFuel() {
        if (burner == null || logic.held() == null || !burner.fuel().isEmpty() || !LaneItem.isPlain(logic.held())) return;
        if (burner.insertFuel(logic.held(), 1, true) == 1) {
            logic.takeHeld();
            setChanged();
        }
    }

    /** Sends the arm state when the hand's content or the arm speed changed (see class doc). */
    private void sync(Level level, double satisfaction) {
        double newRate = logic.isMoving() ? satisfaction : rate;
        boolean newWorking = logic.isMoving() && satisfaction > 0;
        boolean changed = !syncedOnce || !Objects.equals(syncedHeld, logic.held()) || newWorking != working
                || logic.isMoving() && Math.abs(newRate - rate) > RATE_SYNC_DELTA;
        if (!changed) return;
        syncedOnce = true;
        syncedHeld = logic.held();
        rate = newRate;
        working = newWorking;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // --- drops -------------------------------------------------------------------------------------

    /** Drops the held item and the unburnt fuel (server side) and empties both. */
    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        String held = logic.takeHeld();
        if (held != null) LaneStacks.drop(level, worldPosition, held);
        if (burner == null) return;
        MachineItems.drop(level, worldPosition, burner.fuel());
        burner.load(Map.of(), burner.remainingJ());
    }

    // --- persistence and sync ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(KEY_LOGIC, CoreNbt.write(logic.save()));
        if (burner != null) tag.put(KEY_BURNER, CoreNbt.writeBurner(burner));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_LOGIC)) logic.load(CoreNbt.read(tag.getCompound(KEY_LOGIC)));
        if (burner != null && tag.contains(KEY_BURNER)) CoreNbt.loadBurnerInto(burner, tag.getCompound(KEY_BURNER));
        if (tag.contains(KEY_RATE)) { // client update
            rate = tag.getDouble(KEY_RATE);
            working = logic.isMoving() && rate > 0;
        }
    }

    /** Arm state only (no fuel): logic + arm speed. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(KEY_LOGIC, CoreNbt.write(logic.save()));
        tag.putDouble(KEY_RATE, rate);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Client: arm position (0 = pickup, 1 = drop) interpolated between the last two ticks. */
    public double renderArm(float partialTick) {
        return prevArm + (logic.armProgress() - prevArm) * partialTick;
    }
}
