package dev.minetorio.content.robot;

import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.robot.ConstructionArea;
import dev.minetorio.core.robot.Flight;
import dev.minetorio.core.robot.RobotBattery;
import dev.minetorio.core.robot.RobotStats;
import dev.minetorio.core.robot.SmoothFlight;
import dev.minetorio.core.robot.Cell;
import dev.minetorio.core.robot.ConstructionQueue;
import dev.minetorio.core.robot.UpgradeMarks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * A construction robot in flight: no gravity, no collision, no AI goals; a server-side state
 * machine flying straight lines {@link RobotStats#CRUISE_HEIGHT} blocks above its targets at
 * Factorio speed (20 % once its battery is empty):
 * <ol>
 *   <li>{@link Phase#TO_PROVIDER}: to the chest, takes one item of the job's ghost;</li>
 *   <li>{@link Phase#TO_GHOST}: to the ghost, builds it through the normal placement path;</li>
 *   <li>{@link Phase#TO_STORE}: when the build failed (ghost gone or blocked), back to a provider
 *       with room to put the item away (dropped where it is if none);</li>
 *   <li>{@link Phase#RETURNING}: to its home roboport (the nearest loaded one if home is gone; it
 *       drops as an item if there is none), then {@link Phase#CHARGING} at the dock until full and
 *       back into the roboport's slots.</li>
 * </ol>
 * The job and the reserved provider item are saved and re-claimed when the robot loads; losing the
 * robot (killed, unloaded) frees them.
 *
 * <p>Clients do not get a position every tick: the server syncs only the current flight target and
 * speed (re-sent when they change), each client flies the same straight lines itself with
 * {@link SmoothFlight} (smooth heading and banking included), and the server's periodic position
 * packets only correct real drift. Vanilla's base {@link Entity#lerpTo} would instead snap the robot
 * to each packet whenever it arrives, which is what made the flight choppy.
 */
public class ConstructionRobotEntity extends Entity {
    public enum Phase { TO_PROVIDER, TO_GHOST, TO_STORE, RETURNING, CHARGING }

    private static final EntityDataAccessor<ItemStack> DATA_CARRIED =
            SynchedEntityData.defineId(ConstructionRobotEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Flight target, split into a block and a float offset inside it so it stays exact far from 0,0. */
    private static final EntityDataAccessor<BlockPos> DATA_TARGET_CELL =
            SynchedEntityData.defineId(ConstructionRobotEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> DATA_TARGET_OFFSET =
            SynchedEntityData.defineId(ConstructionRobotEntity.class, EntityDataSerializers.VECTOR3);
    /** Blocks per tick toward the target; 0 = hold position. */
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(ConstructionRobotEntity.class, EntityDataSerializers.FLOAT);
    private static final double ARRIVED = 1e-3;

    private Phase phase = Phase.RETURNING;
    @Nullable private BlockPos home;
    @Nullable private BlockPos job;
    @Nullable private BlockPos provider;
    @Nullable private String item;
    /** What the job is: build a ghost, deconstruct or upgrade a marked block. */
    private ConstructionQueue.Kind kind = ConstructionQueue.Kind.BUILD;
    /** Items the job takes from the provider (2 for a paired underground belt upgrade). */
    private int count = 1;
    /**
     * Everything the robot carries: the item fetched for its job, or what a deconstruction or
     * upgrade gave (a machine's item and its contents ride along, as in Factorio). The first stack
     * is shown in its claws ({@link #carried()}).
     */
    private final List<ItemStack> cargo = new ArrayList<>();
    private RobotBattery battery = RobotBattery.full();
    /** Job and reservation registered with the manager since the robot was (re)loaded. */
    private boolean registered;

    /** Client only: the locally simulated flight, and the bank angle (degrees) last tick and now. */
    @Nullable private SmoothFlight clientFlight;
    private float bankO;
    private float bank;

    public ConstructionRobotEntity(EntityType<? extends ConstructionRobotEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CARRIED, ItemStack.EMPTY);
        builder.define(DATA_TARGET_CELL, BlockPos.ZERO);
        builder.define(DATA_TARGET_OFFSET, new Vector3f());
        builder.define(DATA_SPEED, 0f);
    }

    /** Non-zero id used for job claims. */
    public long robotId() {
        UUID u = getUUID();
        long id = u.getMostSignificantBits() ^ u.getLeastSignificantBits();
        return id == 0 ? 1 : id;
    }

    /** The stack shown in the robot's claws (the first of its cargo). */
    public ItemStack carried() { return entityData.get(DATA_CARRIED); }

    /** Everything the robot carries (a copy). */
    public List<ItemStack> cargo() { return cargo.stream().map(ItemStack::copy).toList(); }

    public ConstructionQueue.Kind jobKind() { return kind; }

    private void setCargo(List<ItemStack> stacks) {
        cargo.clear();
        for (ItemStack s : stacks) if (!s.isEmpty()) cargo.add(s.copy());
        entityData.set(DATA_CARRIED, cargo.isEmpty() ? ItemStack.EMPTY : cargo.get(0).copyWithCount(1));
    }

    private void setCarried(ItemStack s) { setCargo(s.isEmpty() ? List.of() : List.of(s)); }

    public Phase phase() { return phase; }

    public RobotBattery battery() { return battery; }

    @Nullable
    public BlockPos home() { return home; }

    /** Sends a freshly launched robot (job claimed, provider item reserved by the roboport). */
    void startJob(BlockPos home, BlockPos job, BlockPos provider, String item) {
        startJob(home, job, provider, item, ConstructionQueue.Kind.BUILD, 1);
    }

    /**
     * Sends a freshly launched robot on a job of {@code kind} (claimed; for build and upgrade jobs
     * {@code count} × {@code item} reserved at {@code provider} by the roboport). A deconstruction
     * job has no provider: the robot flies straight to the block.
     */
    void startJob(BlockPos home, BlockPos job, @Nullable BlockPos provider, @Nullable String item, ConstructionQueue.Kind kind, int count) {
        this.home = home.immutable();
        this.job = job.immutable();
        this.provider = provider == null ? null : provider.immutable();
        this.item = item;
        this.kind = kind;
        this.count = Math.max(1, count);
        this.phase = kind == ConstructionQueue.Kind.DECONSTRUCT ? Phase.TO_GHOST : Phase.TO_PROVIDER;
        this.registered = true;
    }

    // --- ticking ---------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFlightTick();
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        ConstructionManager m = ConstructionManager.get(server);
        if (!registered) reRegister(m);
        switch (phase) {
            case TO_PROVIDER -> { if (fly(hover(provider))) pickUp(server, m); }
            case TO_GHOST -> {
                if (fly(hover(job))) {
                    switch (kind) {
                        case BUILD -> buildGhost(server, m);
                        case DECONSTRUCT -> deconstructBlock(server, m);
                        case UPGRADE -> upgradeBlock(server, m);
                    }
                }
            }
            case TO_STORE -> { if (fly(hover(provider))) putAway(server); }
            case RETURNING -> returning(server);
            case CHARGING -> charging(server);
        }
    }

    // --- client flight ----------------------------------------------------------------------------

    private void clientFlightTick() {
        if (clientFlight == null) clientFlight = new SmoothFlight(new Flight.Vec(getX(), getY(), getZ()), getYRot());
        BlockPos cell = entityData.get(DATA_TARGET_CELL);
        Vector3f off = entityData.get(DATA_TARGET_OFFSET);
        clientFlight.setTarget(new Flight.Vec(cell.getX() + (double) off.x(), cell.getY() + (double) off.y(), cell.getZ() + (double) off.z()),
                entityData.get(DATA_SPEED));
        clientFlight.tick();
        Flight.Vec p = clientFlight.pos();
        setPos(p.x(), p.y(), p.z());
        setYRot(clientFlight.yaw());
        bankO = bank;
        bank = clientFlight.bank();
    }

    /**
     * Server position packets land here on the client. The base entity snaps to them the moment they
     * arrive (between ticks, so the render interpolation jumps); the robot hands them to its flight
     * simulation as drift samples instead, and keeps its own smoothed heading.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (clientFlight == null) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        clientFlight.sample(new Flight.Vec(x, y, z));
    }

    /** Bank angle in degrees (+ = right side down), interpolated for rendering. */
    public float bank(float partialTick) {
        return Mth.lerp(partialTick, bankO, bank);
    }

    private void reRegister(ConstructionManager m) {
        registered = true;
        if (phase == Phase.TO_PROVIDER || phase == Phase.TO_GHOST) {
            if (job == null || !m.queue().claim(ConstructionManager.cell(job), robotId())) {
                // someone else took it (or it is gone): give up the job
                job = null;
                phase = cargo.isEmpty() ? Phase.RETURNING : storePhase((ServerLevel) level());
                return;
            }
            if (phase == Phase.TO_PROVIDER && provider != null && item != null) {
                for (int i = 0; i < count; i++) m.ledger().reserve(ConstructionManager.cell(provider), item);
            }
        }
    }

    private static Vec3 hover(@Nullable BlockPos p) {
        if (p == null) return Vec3.ZERO;
        return new Vec3(p.getX() + 0.5, p.getY() + 1 + RobotStats.CRUISE_HEIGHT, p.getZ() + 0.5);
    }

    /** One tick toward {@code target}; true once there. */
    private boolean fly(Vec3 target) {
        float speed = (float) (RobotStats.SPEED_BLOCKS_PER_TICK * battery.speedFactor());
        syncTarget(target, speed);
        Flight.Vec from = new Flight.Vec(getX(), getY(), getZ());
        Flight.Vec to = new Flight.Vec(target.x, target.y, target.z);
        Flight.Vec next = Flight.step(from, to, speed);
        double moved = Flight.distance(from, next);
        battery.fly(moved);
        double dx = next.x() - from.x(), dz = next.z() - from.z();
        if (dx * dx + dz * dz > 1e-6) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
        }
        setPos(next.x(), next.y(), next.z());
        return Flight.distance(next, to) < ARRIVED;
    }

    /** Tells clients where the robot is flying and how fast (only sent when it changes). */
    private void syncTarget(Vec3 target, float speed) {
        BlockPos cell = BlockPos.containing(target);
        entityData.set(DATA_TARGET_CELL, cell);
        entityData.set(DATA_TARGET_OFFSET, new Vector3f((float) (target.x - cell.getX()), (float) (target.y - cell.getY()),
                (float) (target.z - cell.getZ())));
        entityData.set(DATA_SPEED, speed);
    }

    private void pickUp(ServerLevel level, ConstructionManager m) {
        for (int i = 0; i < count; i++) m.ledger().release(ConstructionManager.cell(provider), item);
        Item wanted = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
        int need = count;
        ItemStack got = ItemStack.EMPTY;
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, provider, null);
        if (h != null) {
            for (int i = 0; i < h.getSlots() && need > 0; i++) {
                if (!h.getStackInSlot(i).is(wanted)) continue;
                ItemStack s = h.extractItem(i, need, false);
                if (s.isEmpty()) continue;
                need -= s.getCount();
                if (got.isEmpty()) got = s;
                else got.grow(s.getCount());
            }
        }
        if (got.isEmpty()) {
            releaseJob(m);
            phase = Phase.RETURNING;
            return;
        }
        setCarried(got);
        phase = Phase.TO_GHOST;
    }

    private void buildGhost(ServerLevel level, ConstructionManager m) {
        ItemStack held = cargo.isEmpty() ? ItemStack.EMPTY : cargo.get(0);
        boolean built = Ghosts.build(level, job, null, held);
        if (built) {
            beam(level, job);
            held.shrink(1);
            setCargo(new ArrayList<>(cargo));
        }
        releaseJob(m);
        job = null;
        phase = cargo.isEmpty() ? Phase.RETURNING : storePhase(level);
    }

    /**
     * At a block marked for deconstruction: breaks it (a machine whole) and takes everything it
     * gave as cargo to a storage chest. Nothing happens if the mark is gone meanwhile.
     */
    private void deconstructBlock(ServerLevel level, ConstructionManager m) {
        Cell cell = ConstructionManager.cell(job);
        ConstructionMarks marks = ConstructionMarks.get(level);
        BlockPos at = job;
        releaseJob(m);
        job = null;
        if (!marks.deconstruction().isMarked(cell) || !marks.deconstruction().stillValid(cell,
                ConstructionMarks.blockId(level.getBlockState(at)))) {
            marks.deconstruction().unmark(cell);
            phase = Phase.RETURNING;
            return;
        }
        List<ItemStack> got = RobotWork.deconstruct(level, at);
        marks.deconstruction().unmark(cell);
        if (got != null) beam(level, at);
        setCargo(got == null ? List.of() : got);
        phase = cargo.isEmpty() ? Phase.RETURNING : storePhase(level);
    }

    /**
     * At a block marked for upgrade, with the new item(s): swaps the block (fast-replace semantics)
     * and takes the old item(s) to a storage chest. If the order changed or the swap fails, the new
     * items go back to storage instead.
     */
    private void upgradeBlock(ServerLevel level, ConstructionManager m) {
        Cell cell = ConstructionManager.cell(job);
        ConstructionMarks marks = ConstructionMarks.get(level);
        BlockPos at = job;
        releaseJob(m);
        job = null;
        UpgradeMarks.Order order = marks.upgrades().get(cell);
        ItemStack held = cargo.isEmpty() ? ItemStack.EMPTY : cargo.get(0).copy();
        RobotWork.Upgraded done = null;
        if (order != null && item != null && order.toItem().equals(item) && !held.isEmpty()
                && marks.upgrades().stillValid(cell, ConstructionMarks.blockId(level.getBlockState(at)))) {
            done = RobotWork.upgrade(level, at, held.getItem(), held.getCount());
        }
        if (done != null) {
            marks.upgrades().unmark(cell);
            beam(level, at);
            held.shrink(done.used());
            List<ItemStack> next = new ArrayList<>(done.old());
            if (!held.isEmpty()) next.add(held);
            setCargo(next);
        }
        phase = cargo.isEmpty() ? Phase.RETURNING : storePhase(level);
    }

    /** Short spark beam from the robot down to the built block. */
    private void beam(ServerLevel level, BlockPos target) {
        Vec3 from = position();
        Vec3 to = Vec3.atCenterOf(target);
        for (int i = 0; i <= 8; i++) {
            Vec3 p = from.lerp(to, i / 8.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 10, 0.4, 0.4, 0.4, 0.05);
    }

    /**
     * {@link Phase#TO_STORE} with a chest in the home area that takes the first cargo stack: the
     * nearest one already holding that item, else the nearest with room (Factorio's storage
     * preference). No such chest: the cargo is dropped where the robot is and it returns.
     */
    private Phase storePhase(ServerLevel level) {
        if (cargo.isEmpty()) return Phase.RETURNING;
        ItemStack c = cargo.get(0);
        RoboportBlockEntity port = roboport(level, home);
        if (port == null) port = nearestRoboport(level);
        if (port != null) {
            BlockPos best = RoboportBlockEntity.storageFor(level, port.area(), c, position());
            if (best != null) {
                provider = best;
                return Phase.TO_STORE;
            }
        }
        for (ItemStack s : cargo) drop(level, s);
        setCargo(List.of());
        return Phase.RETURNING;
    }

    /** At the chest: puts in all the cargo it takes; what is left goes to another chest (or is dropped). */
    private void putAway(ServerLevel level) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, provider, null);
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack s : cargo) {
            ItemStack left = h == null ? s.copy() : ItemHandlerHelper.insertItemStacked(h, s.copy(), false);
            if (!left.isEmpty()) rest.add(left);
        }
        setCargo(rest);
        phase = rest.isEmpty() ? Phase.RETURNING : storePhase(level);
    }

    private void returning(ServerLevel level) {
        RoboportBlockEntity port = roboport(level, home);
        if (port == null) {
            port = nearestRoboport(level);
            if (port == null) {
                lose(level);
                return;
            }
            RoboportBlockEntity old = roboport(level, home);
            if (old != null) old.robotReturned();
            home = port.getBlockPos();
        }
        if (fly(port.dock())) phase = Phase.CHARGING;
    }

    private void charging(ServerLevel level) {
        RoboportBlockEntity port = roboport(level, home);
        if (port == null) {
            phase = Phase.RETURNING;
            return;
        }
        if (!battery.isFull()) {
            if (port.charge(battery) > 0 && tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 1, 0.15, 0.1, 0.15, 0);
            }
            return;
        }
        if (port.store()) {
            port.robotReturned();
            discard();
        }
    }

    /** No roboport to go to: drops as an item (and what it carries). */
    private void lose(ServerLevel level) {
        for (ItemStack s : cargo) drop(level, s);
        setCargo(List.of());
        drop(level, new ItemStack(ModItems.CONSTRUCTION_ROBOT.get()));
        discard();
    }

    private void drop(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) return;
        level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), stack.copy()));
    }

    @Nullable
    private static RoboportBlockEntity roboport(Level level, @Nullable BlockPos pos) {
        return pos != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RoboportBlockEntity be ? be : null;
    }

    @Nullable
    private RoboportBlockEntity nearestRoboport(ServerLevel level) {
        RoboportBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : ConstructionManager.get(level).roboports()) {
            RoboportBlockEntity be = roboport(level, p);
            if (be == null) continue;
            ConstructionArea a = be.area();
            double d = be.center().distanceToSqr(position());
            // any roboport whose area we are in, or within twice its radius
            if (d < bestD && d <= 4.0 * a.radius() * a.radius()) {
                bestD = d;
                best = be;
            }
        }
        return best;
    }

    private void releaseJob(ConstructionManager m) {
        if (job != null) m.queue().release(ConstructionManager.cell(job), robotId());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server && registered) {
            ConstructionManager m = ConstructionManager.getIfPresent(server);
            if (m != null) {
                releaseJob(m);
                if (phase == Phase.TO_PROVIDER && provider != null && item != null) {
                    for (int i = 0; i < count; i++) m.ledger().release(ConstructionManager.cell(provider), item);
                }
            }
            if (reason == RemovalReason.KILLED) {
                RoboportBlockEntity port = roboport(server, home);
                if (port != null) port.robotReturned();
            }
        }
        super.remove(reason);
    }

    // --- entity basics ---------------------------------------------------------------------------

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("phase", phase.name());
        if (home != null) tag.put("home", NbtUtils.writeBlockPos(home));
        if (job != null) tag.put("job", NbtUtils.writeBlockPos(job));
        if (provider != null) tag.put("provider", NbtUtils.writeBlockPos(provider));
        if (item != null) tag.putString("item", item);
        tag.putLong("energy", battery.energy());
        tag.putString("kind", kind.name());
        tag.putInt("count", count);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ItemStack s : cargo) list.add(s.save(registryAccess()));
        if (!list.isEmpty()) tag.put("cargo", list);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.RETURNING;
        }
        home = NbtUtils.readBlockPos(tag, "home").orElse(null);
        job = NbtUtils.readBlockPos(tag, "job").orElse(null);
        provider = NbtUtils.readBlockPos(tag, "provider").orElse(null);
        item = tag.contains("item") ? tag.getString("item") : null;
        battery = new RobotBattery(tag.contains("energy") ? tag.getLong("energy") : RobotStats.BATTERY_J);
        try {
            kind = tag.contains("kind") ? ConstructionQueue.Kind.valueOf(tag.getString("kind")) : ConstructionQueue.Kind.BUILD;
        } catch (IllegalArgumentException e) {
            kind = ConstructionQueue.Kind.BUILD;
        }
        count = Math.max(1, tag.contains("count") ? tag.getInt("count") : 1);
        List<ItemStack> loaded = new ArrayList<>();
        if (tag.contains("carried")) loaded.add(ItemStack.parseOptional(registryAccess(), tag.getCompound("carried"))); // older saves
        for (net.minecraft.nbt.Tag t : tag.getList("cargo", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            loaded.add(ItemStack.parseOptional(registryAccess(), (CompoundTag) t));
        }
        setCargo(loaded);
        if ((phase == Phase.TO_PROVIDER && (provider == null || item == null)) || (phase == Phase.TO_GHOST && job == null)
                || (phase == Phase.TO_STORE && provider == null)) {
            phase = Phase.RETURNING;
        }
        registered = false;
    }
}
