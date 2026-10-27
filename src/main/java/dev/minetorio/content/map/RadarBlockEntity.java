package dev.minetorio.content.map;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.map.RadarFrontier;
import dev.minetorio.core.map.RadarLogic;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Radar: a {@link RadarLogic} (300 kW, constantly) powered through an {@link ElectricConsumerHelper}.
 * In the Overworld, while it gets power, it keeps the {@value RadarLogic#NEAR_RADIUS}-chunk area
 * around itself live (requests a {@link WorldMap} scan of it every 5 s; throttled there) and
 * explores one more chunk per 30 s at full power from its own {@link RadarFrontier} (radius
 * {@value RadarLogic#FAR_RADIUS}), which skips chunks already explored by anyone. The frontier
 * index and the charge are saved with the radar; a radar broken and placed again starts its
 * frontier over (explored chunks are skipped at once).
 *
 * <p><b>Client sync.</b> Only {@link #isWorking()} (powered) is synced; the client turns the dish
 * while it is set.
 */
public class RadarBlockEntity extends MultiblockControllerBlockEntity {
    /** Explored chunks skipped per tick at most while looking for the next frontier chunk. */
    private static final int MAX_SKIPS_PER_TICK = 512;
    /** Dish turn while powered, degrees per tick (client). */
    private static final float DISH_DEGREES_PER_TICK = 3.6f;
    private static final String KEY_FRONTIER = "frontier";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_WORKING = "working";

    private final RadarLogic logic = new RadarLogic();
    private final RadarFrontier frontier = new RadarFrontier(RadarLogic.FAR_RADIUS);
    private final ElectricConsumerHelper power = new ElectricConsumerHelper(this, () -> RadarLogic.POWER,
            () -> ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())));
    private boolean working;
    // client animation
    private float dishAngle;
    private float prevDishAngle;

    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADAR.get(), pos, state);
    }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return ((MultiblockControllerBlock) getBlockState().getBlock()).shape(); }

    public RadarLogic logic() { return logic; }

    public RadarFrontier frontier() { return frontier; }

    /** Last supplied power satisfaction (server). */
    public double satisfaction() { return power.satisfaction(); }

    /** Powered (synced to the client). */
    public boolean isWorking() { return working; }

    /** Chunk x, z of the footprint's center. */
    public int[] centerChunk() {
        Vec3 c = ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())).getCenter();
        return new int[] {((int) Math.floor(c.x)) >> 4, ((int) Math.floor(c.z)) >> 4};
    }

    // --- ticking -----------------------------------------------------------------------------------

    void tickServer(Level level) {
        double satisfaction = power.satisfaction();
        if (level instanceof ServerLevel server && level.dimension() == Level.OVERWORLD) {
            WorldMap map = WorldMap.get(server.getServer());
            int[] c = centerChunk();
            if (logic.tick(satisfaction)) map.requestArea(c[0], c[1], RadarLogic.NEAR_RADIUS);
            if (logic.revealDue() && !frontier.isExhausted()) {
                long next = frontier.next(map::isExplored, c[0], c[1], MAX_SKIPS_PER_TICK);
                if (next != RadarFrontier.BUSY) {
                    if (next != RadarFrontier.NONE) map.requestScan(next);
                    logic.revealed();
                }
            }
            if (satisfaction > 0) setChanged();
        }
        boolean now = satisfaction > 0;
        if (now != working) {
            working = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    void tickClient() {
        prevDishAngle = dishAngle;
        if (working) dishAngle = (dishAngle + DISH_DEGREES_PER_TICK) % 360f;
        if (dishAngle < prevDishAngle) prevDishAngle -= 360f; // keep interpolation monotonic across the wrap
    }

    /** Client: dish rotation in degrees, interpolated between the last two ticks. */
    public float renderDishAngle(float partialTick) {
        return prevDishAngle + (dishAngle - prevDishAngle) * partialTick;
    }

    /** Action-bar status: power, explored chunks and the time to the next one. */
    public Component status() {
        if (level == null || level.dimension() != Level.OVERWORLD) return Component.translatable("minetorio.radar.wrong_dimension");
        if (power.satisfaction() <= 0) return Component.translatable("minetorio.radar.no_power");
        int explored = level.getServer() == null ? 0 : WorldMap.get(level.getServer()).exploredCount();
        if (frontier.isExhausted()) return Component.translatable("minetorio.radar.done", explored);
        int seconds = (int) Math.ceil((1 - logic.progress()) * RadarLogic.REVEAL_TICKS / power.satisfaction() / 20);
        return Component.translatable("minetorio.radar.scanning", explored, seconds);
    }

    // --- power, persistence and sync ---------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        power.onLoad();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        power.onRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        power.onRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_FRONTIER, frontier.index());
        tag.putDouble(KEY_PROGRESS, logic.progress());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_FRONTIER)) frontier.setIndex(tag.getInt(KEY_FRONTIER));
        if (tag.contains(KEY_PROGRESS)) logic.load(tag.getDouble(KEY_PROGRESS));
        if (tag.contains(KEY_WORKING)) working = tag.getBoolean(KEY_WORKING); // client update
    }

    /** Only the working flag (see class doc). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_WORKING, working);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
