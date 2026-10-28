package dev.minetorio.content.power;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltSync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity shared by the steam-power machines: flows its ports with touching machine ports
 * each tick ({@link FluidPorts#flowToMachines}; pipes flow on their own), then runs the machine.
 *
 * <p><b>Client sync.</b> Besides the alt-mode info ({@link dev.minetorio.content.alt.AltSource}, on change), only {@link #isWorking()} is synced, when it changes; the client advances
 * an animation angle while it is set (engine flywheel; the boiler shows its fire).
 */
public abstract class SteamMachineBlockEntity extends MultiblockControllerBlockEntity implements FluidPorts.Box {
    private static final String KEY_WORKING = "working";

    /** Idle ticks before the working flag turns off. */
    private static final int IDLE_GRACE = 10;

    private boolean working;
    private int idleTicks;
    /** Alt-mode throttle and last received info (used by machines implementing {@link dev.minetorio.content.alt.AltSource}). */
    protected final AltSync altSync = new AltSync();
    protected AltInfo clientAlt = AltInfo.NONE;
    // client animation
    private float angle;
    private float prevAngle;

    protected SteamMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    /** Doing its job this tick (pumping, burning, producing); synced to the client. */
    public boolean isWorking() { return working; }

    /** Runs the machine for one tick (after the port flow). */
    protected abstract void tickMachine(Level level);

    /** Status line for the action bar. */
    public abstract Component status();

    /** Animation speed while working, degrees per tick (client). */
    protected float animationDegreesPerTick() { return 0; }

    final void tickServer(Level level) {
        if (getBlockState().getBlock() instanceof FluidPorts.Machine machine) {
            FluidPorts.flowToMachines(level, worldPosition, facing(), machine, this);
        }
        tickMachine(level);
        if (this instanceof dev.minetorio.content.alt.AltSource) dev.minetorio.content.alt.AltSource.tickAlt(this, altSync);
    }

    final void tickClient() {
        prevAngle = angle;
        if (working) angle = (angle + animationDegreesPerTick()) % 360f;
        if (angle < prevAngle) prevAngle -= 360f; // keep interpolation monotonic across the wrap
    }

    /**
     * Reports whether the machine did its job this tick. The working flag turns on at once and off
     * only after {@value #IDLE_GRACE} idle ticks, so a machine running in bursts (partial load)
     * does not toggle it, and send a packet, every tick.
     */
    protected void setActive(Level level, boolean active) {
        idleTicks = active ? 0 : Math.min(idleTicks + 1, IDLE_GRACE);
        boolean now = active || working && idleTicks < IDLE_GRACE;
        if (now == working) return;
        working = now;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void fluidChanged() { setChanged(); }

    /** Client: animation angle in degrees, interpolated between the last two ticks. */
    public float renderAngle(float partialTick) {
        return prevAngle + (angle - prevAngle) * partialTick;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_WORKING)) working = tag.getBoolean(KEY_WORKING); // client update
        if (this instanceof dev.minetorio.content.alt.AltSource) clientAlt = dev.minetorio.content.alt.AltSource.read(tag, clientAlt);
    }

    /** Only the working flag (see class doc). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_WORKING, working);
        if (this instanceof dev.minetorio.content.alt.AltSource source) dev.minetorio.content.alt.AltSource.write(tag, source, altSync);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Fluid amount for status lines, rounded to whole units. */
    protected static String units(double amount) {
        return String.valueOf(Math.round(amount));
    }
}
