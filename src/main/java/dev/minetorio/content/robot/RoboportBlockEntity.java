package dev.minetorio.content.robot;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.content.logistics.LogisticChestBlockEntity;
import dev.minetorio.content.logistics.LogisticRobotEntity;
import dev.minetorio.content.logistics.LogisticsRegistry;
import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.content.registry.ModEntities;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.robot.Cell;
import dev.minetorio.core.robot.ConstructionArea;
import dev.minetorio.core.robot.ConstructionQueue;
import dev.minetorio.core.robot.Dispatcher;
import dev.minetorio.core.robot.RobotBattery;
import dev.minetorio.core.robot.RobotReach;
import dev.minetorio.core.robot.RoboportAnimation;
import dev.minetorio.core.robot.RobotStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Roboport: an electric consumer with a {@value RobotStats#ROBOPORT_BUFFER_J}-joule buffer (input
 * up to 5 MW, 50 kW drain), {@value RobotStats#ROBOT_SLOTS} robot slots and a construction area of
 * {@link RobotStats#CONSTRUCTION_RADIUS} blocks around its center ({@link #area()}).
 *
 * <p><b>Dispatch</b> (every {@value #DISPATCH_INTERVAL} ticks, while its buffer holds energy): free
 * jobs in the area are handed out in queue order ({@link Dispatcher}), one stored robot each,
 * launched with a full battery. A build or upgrade job needs its item in a provider in the area —
 * any chest-like container ({@link #isProvider}); a deconstruction job needs a chest with room for
 * the block's item (robots take what they break and what an upgrade replaced to a chest,
 * {@link #storageFor}). Robots charge here from the buffer when they come back
 * (up to {@value RobotStats#CHARGING_STATIONS} at once, 1 MW each) and are then stored again.
 *
 * <p>{@link #setCreativePower} (debug command) keeps the buffer full without a grid.
 */
public class RoboportBlockEntity extends MultiblockControllerBlockEntity implements MenuProvider {
    static final int DISPATCH_INTERVAL = 10;
    public static final int DATA_AVAILABLE = 0;
    public static final int DATA_OUT = 1;
    /** Buffer in tenths of a MJ. */
    public static final int DATA_ENERGY = 2;
    public static final int DATA_COUNT = 3;

    private static final String KEY_BUFFER = "buffer";
    private static final String KEY_OUT = "out";
    private static final String KEY_CREATIVE = "creative_power";
    private static final String KEY_ROBOTS = "robots";

    private final SimpleContainer robots = new SimpleContainer(RobotStats.ROBOT_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) { return isRobotItem(stack); }

        @Override
        public void setChanged() {
            super.setChanged();
            RoboportBlockEntity.this.setChanged();
        }
    };
    private final ElectricConsumerHelper power = new ElectricConsumerHelper(this, this::demand,
            () -> ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())));
    private long buffer;
    private long lastDemand;
    private int out;
    private boolean creativePower;
    private int chargingThisTick;
    private int timer;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_AVAILABLE -> availableRobots();
                case DATA_OUT -> out;
                case DATA_ENERGY -> (int) (buffer / 100_000L);
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() { return DATA_COUNT; }
    };

    public RoboportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROBOPORT.get(), pos, state);
    }

    public Direction facing() { return getBlockState().getValue(MultiblockControllerBlock.FACING); }

    public MultiblockShape shape() { return RoboportBlock.SHAPE; }

    public SimpleContainer robots() { return robots; }

    public long buffer() { return buffer; }

    public int robotsOut() { return out; }

    public void setCreativePower(boolean on) {
        creativePower = on;
        if (on) buffer = RobotStats.ROBOPORT_BUFFER_J;
        setChanged();
    }

    /** Whether the robot slots take this item: a construction or (stage 4) a logistic robot. */
    public static boolean isRobotItem(ItemStack stack) {
        return stack.is(ModItems.CONSTRUCTION_ROBOT.get()) || stack.is(LogisticsRegistry.LOGISTIC_ROBOT.get());
    }

    /** Logistic robot items in the slots. */
    public int availableLogisticRobots() {
        int n = 0;
        for (int i = 0; i < robots.getContainerSize(); i++) {
            ItemStack s = robots.getItem(i);
            if (s.is(LogisticsRegistry.LOGISTIC_ROBOT.get())) n += s.getCount();
        }
        return n;
    }

    /** Takes one logistic robot out to launch it (counted as out, opens the iris); false if none is stored. */
    public boolean takeLogisticRobot() {
        if (availableLogisticRobots() <= 0) return false;
        robots.removeItemType(LogisticsRegistry.LOGISTIC_ROBOT.get(), 1);
        out++;
        doorHold = RoboportAnimation.DOOR_HOLD_TICKS;
        setChanged();
        return true;
    }

    /** Takes a docked robot of any kind back into the slots; false when they are full. */
    public boolean storeRobot(ItemStack robot) {
        return robots.addItem(robot.copy()).isEmpty();
    }

    /** Construction robot items in the slots. */
    public int availableRobots() {
        int n = 0;
        for (int i = 0; i < robots.getContainerSize(); i++) {
            ItemStack s = robots.getItem(i);
            if (s.is(ModItems.CONSTRUCTION_ROBOT.get())) n += s.getCount();
        }
        return n;
    }

    /** Footprint center, on the ground. */
    public Vec3 center() {
        return ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())).getBottomCenter();
    }

    public ConstructionArea area() {
        Vec3 c = center();
        return new ConstructionArea(c.x, worldPosition.getY() + 0.5, c.z, RobotStats.CONSTRUCTION_RADIUS);
    }

    /**
     * Height of the dock above the floor, in blocks: just above the iris hatch in the middle of the
     * roof (its silver lip tops out at 56.5 px in art/gen_robots.py), below the pylon tops.
     */
    public static final double DOCK_HEIGHT = 58 / 16.0;

    /** Where robots leave and enter: just above the roof hatch, over the footprint center. */
    public Vec3 dock() {
        return center().add(0, DOCK_HEIGHT, 0);
    }

    // --- power -----------------------------------------------------------------------------------

    private long demand() {
        lastDemand = creativePower ? 0
                : RobotStats.ROBOPORT_DRAIN_PER_TICK_J + Math.min(RobotStats.ROBOPORT_INPUT_PER_TICK_J, RobotStats.ROBOPORT_BUFFER_J - buffer);
        return lastDemand;
    }

    /** Charges {@code battery} for one tick from the buffer if a charging station is free; returns the joules given. */
    public long charge(RobotBattery battery) {
        if (chargingThisTick >= RobotStats.CHARGING_STATIONS || buffer <= 0 || battery.isFull()) return 0;
        chargingThisTick++;
        long given = battery.charge(Math.min(RobotStats.CHARGE_PER_TICK_J, buffer));
        buffer -= given;
        setChanged();
        return given;
    }

    /** Takes a docked robot back into the slots; false when they are full. */
    boolean store() {
        ItemStack rest = robots.addItem(new ItemStack(ModItems.CONSTRUCTION_ROBOT.get()));
        return rest.isEmpty();
    }

    /** A robot launched here is back home, or lost. */
    public void robotReturned() {
        if (out > 0) out--;
        setChanged();
    }

    // --- ticking ---------------------------------------------------------------------------------

    void tickServer() {
        int charged = chargingThisTick; // robots that charged here since the last tick (entities tick first)
        chargingThisTick = 0;
        if (creativePower) {
            buffer = RobotStats.ROBOPORT_BUFFER_J;
        } else {
            long in = Math.round(power.satisfaction() * lastDemand);
            long kept = in - Math.min(in, RobotStats.ROBOPORT_DRAIN_PER_TICK_J);
            if (kept > 0) {
                buffer = Math.min(RobotStats.ROBOPORT_BUFFER_J, buffer + kept);
                setChanged();
            }
        }
        if (++timer % DISPATCH_INTERVAL == 0 && level instanceof ServerLevel server) dispatch(server);
        if (level instanceof ServerLevel server) tickAnimation(server, charged);
    }

    // --- animation state (RoboportAnimation; synced to clients only when it changes) -------------

    private static final String KEY_ANIM_DOOR = "anim_door";
    private static final String KEY_ANIM_CHARGING = "anim_charging";
    private static final String KEY_ANIM_POWERED = "anim_powered";
    private int doorHold;
    private boolean doorOpen;
    private int charging;
    private boolean powered;
    // client
    private float door, prevDoor, antenna, prevAntenna;

    private void tickAnimation(ServerLevel level, int charged) {
        boolean near = charged > 0;
        if (!near && timer % 4 == 0) {
            AABB reach = new AABB(dock(), dock()).inflate(RoboportAnimation.DOOR_REACH);
            near = !level.getEntitiesOfClass(ConstructionRobotEntity.class, reach).isEmpty()
                    || !level.getEntitiesOfClass(LogisticRobotEntity.class, reach).isEmpty();
        }
        doorHold = RoboportAnimation.nextHold(doorHold, near);
        boolean open = doorHold > 0;
        int c = Math.min(charged, RobotStats.CHARGING_STATIONS);
        boolean p = buffer > 0;
        if (open != doorOpen || c != charging || p != powered) {
            doorOpen = open;
            charging = c;
            powered = p;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    void tickClient() {
        prevDoor = door;
        door = RoboportAnimation.stepDoor(door, doorOpen);
        prevAntenna = antenna;
        antenna = RoboportAnimation.stepAntenna(antenna, powered);
        if (antenna < prevAntenna) prevAntenna -= 360f; // keep interpolation monotonic across the wrap
    }

    /** Whether the roof iris is (to be) open: a robot launched or is at the dock lately. */
    public boolean doorOpen() { return doorOpen; }

    /** Robots charging here (0..{@value RobotStats#CHARGING_STATIONS}). */
    public int chargingRobots() { return charging; }

    /** Whether the buffer holds energy (antenna turns, lamps blink green). */
    public boolean isPowered() { return powered; }

    /** Client: the iris opening, 0 closed .. 1 open, eased and interpolated between ticks. */
    public float renderDoor(float partialTick) {
        return RoboportAnimation.ease(prevDoor + (door - prevDoor) * partialTick);
    }

    /** Client: the antenna angle in degrees, interpolated between ticks. */
    public float renderAntenna(float partialTick) {
        return prevAntenna + (antenna - prevAntenna) * partialTick;
    }

    private void dispatch(ServerLevel level) {
        int free = availableRobots();
        if (free <= 0 || buffer <= 0) return;
        // robots launch at the dock: a roboport at the edge of the loaded area (block-ticking only)
        // would launch them frozen
        if (!level.isPositionEntityTicking(BlockPos.containing(dock()))) return;
        ConstructionManager m = ConstructionManager.get(level);
        ConstructionArea area = area();
        ConstructionMarks marks = ConstructionMarks.get(level);
        List<ConstructionQueue.Job> pending = new ArrayList<>();
        for (ConstructionQueue.Job j : RobotReach.dispatchable(m.queue().pending(area::contains), ticking(level))) {
            BlockPos p = ConstructionManager.pos(j.cell());
            boolean ok = switch (j.kind()) {
                case BUILD -> Ghosts.canBuild(level, p);
                case DECONSTRUCT -> !RobotWork.protectedTerrain(level.getBlockState(p))
                        && marks.deconstruction().stillValid(j.cell(), ConstructionMarks.blockId(level.getBlockState(p)));
                case UPGRADE -> marks.upgrades().stillValid(j.cell(), ConstructionMarks.blockId(level.getBlockState(p)));
            };
            if (ok) pending.add(j);
        }
        if (pending.isEmpty()) return;
        Map<Cell, Map<String, Integer>> stock = scanProviders(level, area);
        if (stock.isEmpty()) return;
        List<BlockPos> chests = stock.keySet().stream().map(ConstructionManager::pos).toList();
        List<Dispatcher.Assignment> plan = Dispatcher.plan(pending, free, new ArrayList<>(stock.keySet()),
                (c, item) -> stock.getOrDefault(c, Map.of()).getOrDefault(item, 0), m.ledger(),
                j -> hasRoom(level, chests, RobotWork.mainItem(level.getBlockState(ConstructionManager.pos(j.cell())))));
        for (Dispatcher.Assignment a : plan) launch(level, m, a);
    }

    /**
     * Whether robots may put what they took apart ({@code stack}) into the provider at {@code p}: any
     * chest-like container, but of the logistic chests only storage chests (Factorio), a filtered
     * one only its filter item; provider and buffer chests are only taken from.
     */
    private static boolean storesDeconstructed(ServerLevel level, BlockPos p, ItemStack stack) {
        if (!(level.getBlockEntity(p) instanceof LogisticChestBlockEntity chest)) return true;
        if (chest.mode() != dev.minetorio.core.logistics.LogisticMode.STORAGE) return false;
        String filter = chest.filter();
        return filter == null || stack.isEmpty() || filter.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** Whether one of {@code chests} takes {@code stack} (an empty stack: any chest with an empty slot). */
    private static boolean hasRoom(ServerLevel level, List<BlockPos> chests, ItemStack stack) {
        for (BlockPos p : chests) {
            if (!storesDeconstructed(level, p, stack)) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (h == null) continue;
            if (stack.isEmpty()) {
                for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).isEmpty()) return true;
            } else if (net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(h, stack.copy(), true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The storage chest in {@code area} a robot at {@code from} puts {@code stack} into: the nearest
     * one already holding that item and taking all of it, else the nearest taking all of it; null
     * when none has room. Of the logistic chests only storage chests take it (Factorio), a filtered
     * one only its filter item; provider and buffer chests are only taken from.
     */
    @Nullable
    static BlockPos storageFor(ServerLevel level, ConstructionArea area, ItemStack stack, Vec3 from) {
        BlockPos same = null, any = null;
        double sameD = Double.MAX_VALUE, anyD = Double.MAX_VALUE;
        for (BlockPos p : providers(level, area)) {
            if (!storesDeconstructed(level, p, stack)) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (h == null || !net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(h, stack.copy(), true).isEmpty()) continue;
            double d = p.distToCenterSqr(from);
            boolean holds = false;
            for (int i = 0; i < h.getSlots() && !holds; i++) holds = ItemStack.isSameItemSameComponents(h.getStackInSlot(i), stack);
            if (holds && d < sameD) {
                sameD = d;
                same = p;
            }
            if (d < anyD) {
                anyD = d;
                any = p;
            }
        }
        return same != null ? same : any;
    }

    private void launch(ServerLevel level, ConstructionManager m, Dispatcher.Assignment a) {
        ConstructionRobotEntity robot = new ConstructionRobotEntity(ModEntities.CONSTRUCTION_ROBOT.get(), level);
        Vec3 d = dock();
        robot.moveTo(d.x, d.y, d.z, facing().toYRot(), 0);
        if (!m.queue().claim(a.job().cell(), robot.robotId())) {
            releaseItems(m, a);
            return;
        }
        robot.startJob(worldPosition, ConstructionManager.pos(a.job().cell()),
                a.provider() == null ? null : ConstructionManager.pos(a.provider()), a.job().item(), a.job().kind(), a.job().count());
        if (!level.addFreshEntity(robot)) {
            m.queue().release(a.job().cell(), robot.robotId());
            releaseItems(m, a);
            return;
        }
        m.working(robot);
        robots.removeItemType(ModItems.CONSTRUCTION_ROBOT.get(), 1);
        out++;
        doorHold = RoboportAnimation.DOOR_HOLD_TICKS;
        setChanged();
    }

    private static void releaseItems(ConstructionManager m, Dispatcher.Assignment a) {
        if (a.provider() == null) return;
        for (int i = 0; i < a.job().count(); i++) m.ledger().release(a.provider(), a.job().item());
    }

    /** Item counts of every provider in the area, by item id. */
    static Map<Cell, Map<String, Integer>> scanProviders(ServerLevel level, ConstructionArea area) {
        Map<Cell, Map<String, Integer>> out = new HashMap<>();
        for (BlockPos p : providers(level, area)) {
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (h == null) continue;
            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) counts.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
            out.put(ConstructionManager.cell(p), counts);
        }
        return out;
    }

    /**
     * Provider positions in the area that robots can reach: in entity-ticking chunks (a robot flying
     * into any other chunk would freeze there, {@link RobotReach}).
     */
    static List<BlockPos> providers(ServerLevel level, ConstructionArea area) {
        List<Cell> cells = new ArrayList<>();
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockPos p : chunk.getBlockEntities().keySet()) {
                    if (area.contains(ConstructionManager.cell(p)) && isProvider(level, p)) cells.add(ConstructionManager.cell(p));
                }
            }
        }
        return RobotReach.reachable(cells, ticking(level)).stream().map(ConstructionManager::pos).toList();
    }

    /** Whether a cell is in an entity-ticking chunk (where robots keep ticking). */
    static java.util.function.Predicate<Cell> ticking(ServerLevel level) {
        return c -> level.isPositionEntityTicking(ConstructionManager.pos(c));
    }

    /**
     * Whether robots may take items from (and return items to) the block entity at {@code p}: a
     * chest-like vanilla container (chests, barrels, shulker boxes, hoppers, dispensers) or another
     * mod's block with an item handler. Minetorio machines and vanilla furnaces never are (logistic
     * chests come later).
     */
    static boolean isProvider(Level level, BlockPos p) {
        BlockEntity be = level.getBlockEntity(p);
        if (be == null) return false;
        String ns = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getNamespace();
        if (be instanceof LogisticChestBlockEntity chest) return chest.mode().suppliesConstruction();
        boolean kind = be instanceof RandomizableContainerBlockEntity || (!ns.equals("minecraft") && !ns.equals("minetorio"));
        return kind && level.getCapability(Capabilities.ItemHandler.BLOCK, p, null) != null;
    }

    // --- lifecycle, persistence, GUI ------------------------------------------------------------

    /** Roboports loaded on the client (for the construction area outline). */
    public static final java.util.Set<BlockPos> CLIENT_LOADED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad() {
        super.onLoad();
        power.onLoad();
        if (level instanceof ServerLevel server) ConstructionManager.get(server).addRoboport(worldPosition);
        else if (level != null) CLIENT_LOADED.add(worldPosition.immutable());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        power.onRemoved();
        unregister();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        power.onRemoved();
        unregister();
    }

    private void unregister() {
        if (level != null && level.isClientSide()) CLIENT_LOADED.remove(worldPosition);
        if (level instanceof ServerLevel server) {
            ConstructionManager m = ConstructionManager.getIfPresent(server);
            if (m != null) m.removeRoboport(worldPosition);
        }
    }

    @Override
    protected void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, robots);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(KEY_BUFFER, buffer);
        tag.putInt(KEY_OUT, out);
        tag.putBoolean(KEY_CREATIVE, creativePower);
        CompoundTag inv = new CompoundTag();
        ContainerHelper.saveAllItems(inv, robots.getItems(), registries);
        tag.put(KEY_ROBOTS, inv);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_ANIM_DOOR)) { // client update: only the animation state (getUpdateTag)
            doorOpen = tag.getBoolean(KEY_ANIM_DOOR);
            charging = tag.getInt(KEY_ANIM_CHARGING);
            powered = tag.getBoolean(KEY_ANIM_POWERED);
            return;
        }
        buffer = tag.getLong(KEY_BUFFER);
        out = tag.getInt(KEY_OUT);
        creativePower = tag.getBoolean(KEY_CREATIVE);
        NonNullList<ItemStack> items = NonNullList.withSize(RobotStats.ROBOT_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound(KEY_ROBOTS), items, registries);
        for (int i = 0; i < items.size(); i++) robots.setItem(i, items.get(i));
    }

    /** Only the animation state (door, charging robots, power); the GUI syncs the rest through its menu. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_ANIM_DOOR, doorOpen);
        tag.putInt(KEY_ANIM_CHARGING, charging);
        tag.putBoolean(KEY_ANIM_POWERED, powered);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.minetorio.roboport");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RoboportMenu(id, inventory, robots, data, p -> !isRemoved()
                && p.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 100, worldPosition);
    }

    /** Render bounds helper for the client. */
    public AABB renderBox() {
        return ElectricConsumerHelper.box(shape().footprint(worldPosition, facing())).expandTowards(0, 1, 0);
    }
}
