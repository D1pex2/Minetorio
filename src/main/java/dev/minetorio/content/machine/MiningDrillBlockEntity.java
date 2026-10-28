package dev.minetorio.content.machine;

import dev.minetorio.content.alt.AltSource;
import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltRules;
import dev.minetorio.core.alt.AltSync;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.orefield.OreFields;
import dev.minetorio.core.Units;
import dev.minetorio.core.area.AreaMath;
import dev.minetorio.core.area.GroundRect;
import dev.minetorio.core.orefield.MiningOrder;
import dev.minetorio.core.orefield.OreType;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared state and logic of the mining drills (controller block entity).
 *
 * <p><b>Mining.</b> The drill stands on an ore field and mines the field blocks inside its mining
 * area ({@link #areaSize()} square centered on the machine), one per column: the topmost within a
 * step up or two down from the layer it stands on ({@link #fieldAt}), so slopes are covered. It takes
 * ore from its current block until that block is empty (and has turned into dirt), then moves on to
 * the next field block, nearest to the center first ({@link MiningOrder}). The current block is kept
 * between ticks; without one the area is searched again at most every {@value #RESCAN_TICKS} ticks
 * (and right away after a neighbor change), so an idle drill costs next to nothing. While the output
 * buffer is empty and there is a field block, each tick adds {@code rate / 20 × satisfaction} to the
 * progress (satisfaction from {@link #energy()}); at 1 one ore is taken from the block
 * ({@link OreFields#mineOne}) and goes into the {@link DrillOutput} buffer, which pushes it into the
 * cell in front of the drill ({@link #outputPos()}). A blocked buffer or no field block under the
 * area stop the drill and its energy draw.
 *
 * <p><b>Client sync.</b> {@link #isWorking()} (mined with energy this tick) is synced when it
 * changes; the client spins the drill head while it is set. The alt-mode info ({@link AltSource})
 * is synced when it changes, checked every {@value AltSync#PERIOD} ticks.
 */
public abstract class MiningDrillBlockEntity extends MultiblockControllerBlockEntity implements AltSource {
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_OUTPUT = "output";
    private static final String KEY_WORKING = "working";
    /** Progress at or above this counts as a finished ore (absorbs floating-point drift). */
    private static final double DONE = 1 - 1e-9;
    /** Drill head spin while working, degrees per tick (client). */
    private static final float HEAD_DEGREES_PER_TICK = 12f;
    /** Ticks between searches of the mining area while the drill has no field block. */
    public static final int RESCAN_TICKS = 20;

    private final DrillOutput output = new DrillOutput();
    private double progress;

    /** The field block being mined (server side, not saved: found again after loading). */
    @Nullable
    private BlockPos target;
    /** Ticks until the next search while {@link #target} is null. */
    private int rescanIn;
    /** Whether the drill could mine this tick if it had energy (buffer empty, a field block under it). */
    private boolean active;
    private boolean working;
    private final AltSync altSync = new AltSync();
    private AltInfo clientAlt = AltInfo.NONE;

    // client animation
    private float headAngle;
    private float prevHeadAngle;

    protected MiningDrillBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Ore per second at full satisfaction. */
    protected abstract double miningRate();

    /** Side of the square mining area, in blocks. */
    protected abstract int areaSize();

    /** Satisfaction (0..1) for this tick's mining; called only when the drill can mine (may burn fuel). */
    protected abstract double energy();

    /** Translation key shown when the drill could mine but gets no energy. */
    protected abstract String noEnergyKey();

    /** Alt-mode energy status (no fuel / no or low power). */
    protected abstract dev.minetorio.core.alt.AltStatus energyStatus();

    /** Alt mode: the ore of the field block being mined; no ore, output full, energy (see {@link AltRules#drill}). */
    @Override
    public AltInfo computeAlt() {
        OreType ore = target != null && level != null ? OreFields.typeOf(level.getBlockState(target)) : null;
        return AltRules.drill(ore == null ? null : ore.item(), output.held(), energyStatus());
    }

    @Override
    public AltInfo altInfo() { return clientAlt; }


    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    public DrillOutput output() { return output; }

    /** Mining with energy this tick (synced to the client). */
    public boolean isWorking() { return working; }

    /** Whether the drill can mine right now (buffer empty, a field block under it), regardless of energy. */
    protected boolean isActive() { return active; }

    public double progress() { return progress; }

    /**
     * The cell the ore goes to: in front of the output (front) face, at its center column — for an
     * even width (the 2×2 burner drill), the column of the controller's side of the center line.
     */
    public BlockPos outputPos() {
        MultiblockShape s = shape();
        Direction f = facing();
        return worldPosition.relative(f, s.depth()).relative(f.getClockWise(), (s.width() - 1) / 2);
    }

    /** Mining area as {minX, minZ, maxX, maxZ} (inclusive): {@link #areaSize()} square around the footprint's center. */
    public int[] miningArea() {
        MultiblockShape s = shape();
        Direction f = facing();
        GroundRect r = AreaMath.drillArea(worldPosition.getX(), worldPosition.getZ(), f.getStepX(), f.getStepZ(),
                s.width(), s.depth(), areaSize());
        return new int[] {r.minX(), r.minZ(), r.maxX(), r.maxZ()};
    }

    /** Height of the layer the drill stands on. */
    public int fieldY() { return worldPosition.getY() - 1; }

    /** How far above {@link #fieldY()} a column's field block may be (a step up beside the drill). */
    public static final int REACH_UP = 1;
    /** How far below {@link #fieldY()} a column's field block may be (steps down a slope). */
    public static final int REACH_DOWN = 2;

    /**
     * The field block the drill mines in column (x, z): the topmost one from {@link #REACH_UP} above
     * the layer it stands on down to {@link #REACH_DOWN} below it, so the mining area stays a flat
     * footprint on stepped ground. Null if the column has none.
     */
    @Nullable
    public BlockPos fieldAt(Level level, int x, int z) {
        return OreFields.fieldInColumn(level, x, z, fieldY() + REACH_UP, fieldY() - REACH_DOWN);
    }

    /** The field block being mined (server side), if any. */
    @Nullable
    public BlockPos target() { return target; }

    /** Forgets the current field block; the next tick searches the area again. */
    public void invalidateTarget() {
        target = null;
        rescanIn = 0;
    }

    /** The first field block of the area in {@link MiningOrder}, or null. */
    @Nullable
    private BlockPos findTarget(Level level) {
        int[] a = miningArea();
        for (int[] c : MiningOrder.nearestFirst(a[0], a[1], a[2], a[3])) {
            BlockPos f = fieldAt(level, c[0], c[1]);
            if (f != null) return f;
        }
        return null;
    }

    /** The current field block, searched for again if it is gone (throttled while there is none). */
    @Nullable
    private BlockPos currentTarget(ServerLevel level) {
        if (target != null && OreFields.typeOf(level.getBlockState(target)) == null) invalidateTarget();
        if (target == null) {
            if (rescanIn > 0) {
                rescanIn--;
                return null;
            }
            target = findTarget(level);
            if (target == null) rescanIn = RESCAN_TICKS;
        }
        return target;
    }

    // --- ticking -----------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, MiningDrillBlockEntity be) {
        if (level instanceof ServerLevel server) be.tickServer(server);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MiningDrillBlockEntity be) {
        be.prevHeadAngle = be.headAngle;
        if (be.working) be.headAngle = (be.headAngle + HEAD_DEGREES_PER_TICK) % 360f;
        if (be.headAngle < be.prevHeadAngle) be.prevHeadAngle -= 360f; // keep interpolation monotonic across the wrap
    }

    private void tickServer(ServerLevel level) {
        Direction from = facing().getOpposite(); // the side of the output cell the drill faces
        if (output.push(level, outputPos(), from)) setChanged();
        BlockPos t = output.isEmpty() ? currentTarget(level) : null;
        active = t != null;
        double satisfaction = active ? energy() : 0;
        if (satisfaction > 0) {
            progress += miningRate() / Units.TICKS_PER_SECOND * satisfaction;
            if (progress >= DONE) {
                progress = Math.max(0, progress - 1);
                OreType type = OreFields.mineOne(level, t);
                if (type != null) {
                    output.hold(type.item());
                    output.push(level, outputPos(), from);
                }
            }
            setChanged();
        }
        setWorking(level, satisfaction > 0);
        AltSource.tickAlt(this, altSync);
    }

    private void setWorking(Level level, boolean now) {
        if (now == working) return;
        working = now;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Status line for the action bar (right-click with an empty hand): what is mined and how much ore the area has left. */
    public Component status() {
        if (!(level instanceof ServerLevel server)) return Component.empty();
        BlockPos t = target != null && OreFields.typeOf(server.getBlockState(target)) != null ? target : findTarget(server);
        if (t == null) return Component.translatable("minetorio.drill.no_ore");
        if (!output.isEmpty()) return Component.translatable("minetorio.drill.blocked");
        if (!working) return Component.translatable(noEnergyKey());
        OreType type = OreFields.typeOf(server.getBlockState(t));
        return Component.translatable("minetorio.drill.mining", OreFields.oreName(type), OreFields.formatAmount(areaRemaining(server)));
    }

    /** Ore left in all field blocks under the mining area. */
    public long areaRemaining(Level level) {
        int[] a = miningArea();
        long sum = 0;
        for (int x = a[0]; x <= a[2]; x++) {
            for (int z = a[1]; z <= a[3]; z++) {
                BlockPos f = fieldAt(level, x, z);
                if (f != null) sum += OreFields.remaining(level, f);
            }
        }
        return sum;
    }

    // --- drops -------------------------------------------------------------------------------------

    @Override
    protected void dropContents(Level level, BlockPos pos) {
        output.drop(level, pos);
    }

    // --- persistence and sync ----------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(KEY_PROGRESS, progress);
        CompoundTag out = new CompoundTag();
        output.save(out);
        tag.put(KEY_OUTPUT, out);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_PROGRESS)) progress = tag.getDouble(KEY_PROGRESS);
        if (tag.contains(KEY_OUTPUT)) output.load(tag.getCompound(KEY_OUTPUT));
        if (tag.contains(KEY_WORKING)) working = tag.getBoolean(KEY_WORKING); // client update
        clientAlt = AltSource.read(tag, clientAlt);
        invalidateTarget();
    }

    /** The working flag and the alt-mode info. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_WORKING, working);
        AltSource.write(tag, this, altSync);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Client: drill head rotation in degrees, interpolated between the last two ticks. */
    public float renderHeadAngle(float partialTick) {
        return prevHeadAngle + (headAngle - prevHeadAngle) * partialTick;
    }
}
