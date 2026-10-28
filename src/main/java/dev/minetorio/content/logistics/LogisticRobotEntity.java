package dev.minetorio.content.logistics;

import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.ConstructionManager;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.robot.RoboportBlockEntity;
import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.robot.Flight;
import dev.minetorio.core.robot.RobotBattery;
import dev.minetorio.core.robot.RobotStats;
import dev.minetorio.core.robot.SmoothFlight;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.joml.Vector3f;

/**
 * A logistic robot on a trip (or a construction robot sent out by a player's personal roboport; see
 * {@link Kind}). Flies like the construction robot (straight lines {@link RobotStats#CRUISE_HEIGHT}
 * above its targets, the same battery, clients simulate the flight with {@link SmoothFlight}):
 * <ol>
 *   <li>{@link Phase#TO_SOURCE}: to the chest (or player, for trash) and takes the items;</li>
 *   <li>{@link Phase#TO_TARGET}: to the requester / storage chest or the player (followed as they
 *       move) and puts them in; a personal construction robot builds its ghost here instead;</li>
 *   <li>{@link Phase#TO_STORE}: what could not be delivered goes to a storage chest of the network
 *       (dropped if there is none);</li>
 *   <li>{@link Phase#RETURNING} to its roboport (the nearest one if home is gone) and
 *       {@link Phase#CHARGING} there until full, then back into the roboport's slots; a personal
 *       robot returns into its player's inventory.</li>
 * </ol>
 * The trip's promises in the {@link LogisticsManager}'s ledger are kept up to date and re-entered
 * when the robot loads; removing the robot releases them.
 */
public class LogisticRobotEntity extends Entity {
    public enum Phase { TO_SOURCE, TO_TARGET, TO_STORE, RETURNING, CHARGING }

    /** A network logistic robot, or a construction robot launched from a player (personal roboport). */
    public enum Kind { LOGISTIC, PERSONAL_CONSTRUCTION }

    private static final EntityDataAccessor<ItemStack> DATA_CARRIED =
            SynchedEntityData.defineId(LogisticRobotEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<BlockPos> DATA_TARGET_CELL =
            SynchedEntityData.defineId(LogisticRobotEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> DATA_TARGET_OFFSET =
            SynchedEntityData.defineId(LogisticRobotEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(LogisticRobotEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_KIND =
            SynchedEntityData.defineId(LogisticRobotEntity.class, EntityDataSerializers.BYTE);
    private static final double ARRIVED = 1e-3;

    private Phase phase = Phase.RETURNING;
    @Nullable private BlockPos home;
    @Nullable private UUID homePlayer;
    @Nullable private Endpoint source;
    @Nullable private Endpoint target;
    @Nullable private BlockPos store;
    @Nullable private String item;
    /** Items promised for this trip (before pickup) or carried (after). */
    private int count;
    private RobotBattery battery = RobotBattery.full();
    private boolean registered;

    @Nullable private SmoothFlight clientFlight;
    private float bankO;
    private float bank;

    public LogisticRobotEntity(EntityType<? extends LogisticRobotEntity> type, Level level) {
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
        builder.define(DATA_KIND, (byte) 0);
    }

    public Kind kind() { return Kind.values()[Mth.clamp(entityData.get(DATA_KIND), 0, Kind.values().length - 1)]; }

    private void setKind(Kind k) { entityData.set(DATA_KIND, (byte) k.ordinal()); }

    public long robotId() {
        UUID u = getUUID();
        long id = u.getMostSignificantBits() ^ u.getLeastSignificantBits();
        return id == 0 ? 1 : id;
    }

    public ItemStack carried() { return entityData.get(DATA_CARRIED); }

    private void setCarried(ItemStack s) { entityData.set(DATA_CARRIED, s); }

    public Phase phase() { return phase; }

    @Nullable
    public BlockPos home() { return home; }

    @Nullable
    public UUID homePlayer() { return homePlayer; }

    public RobotBattery battery() { return battery; }

    /** The robot item it turns back into. */
    public Item robotItem() {
        return kind() == Kind.LOGISTIC ? LogisticsRegistry.LOGISTIC_ROBOT.get() : ModItems.CONSTRUCTION_ROBOT.get();
    }

    /** A network trip from roboport {@code home} (ledger entries already made by the planner). */
    void startDelivery(BlockPos home, Endpoint source, Endpoint target, String item, int count) {
        setKind(Kind.LOGISTIC);
        this.home = home.immutable();
        this.source = source;
        this.target = target;
        this.item = item;
        this.count = count;
        this.phase = Phase.TO_SOURCE;
        this.registered = true;
    }

    /** A personal roboport robot carrying {@code stack} to the ghost at {@code ghost} (job already claimed). */
    void startPersonalBuild(UUID player, BlockPos ghost, ItemStack stack) {
        setKind(Kind.PERSONAL_CONSTRUCTION);
        this.homePlayer = player;
        this.target = Endpoint.ghost(ghost);
        this.item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        this.count = stack.getCount();
        setCarried(stack);
        this.phase = Phase.TO_TARGET;
        this.registered = true;
    }

    private double speed() {
        double base = kind() == Kind.LOGISTIC ? LogisticStats.LOGISTIC_SPEED_BLOCKS_PER_TICK : RobotStats.SPEED_BLOCKS_PER_TICK;
        return base * battery.speedFactor();
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
        LogisticsManager m = LogisticsManager.get(server);
        m.track(this);
        if (!registered) reRegister(m);
        switch (phase) {
            case TO_SOURCE -> toSource(server, m);
            case TO_TARGET -> toTarget(server, m);
            case TO_STORE -> toStore(server);
            case RETURNING -> returning(server);
            case CHARGING -> charging(server);
        }
    }

    private void toSource(ServerLevel level, LogisticsManager m) {
        Vec3 at = position(level, source);
        if (at == null || !reachable(level, at)) {
            abortTrip(m);
            return;
        }
        if (!arrive(at, source)) return;
        m.ledger().removeOutgoing(source.nodeId(), item, count);
        int took = take(level, source, item, count);
        if (took < count) m.ledger().removeIncoming(target.nodeId(), item, count - took);
        count = took;
        if (took > 0) setCarried(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)), took));
        phase = took > 0 ? Phase.TO_TARGET : Phase.RETURNING;
    }

    private void toTarget(ServerLevel level, LogisticsManager m) {
        Vec3 at = position(level, target);
        if (at == null || (target.type() == Endpoint.Type.PLAYER && !m.inNetwork(level, target.player(), home))) {
            // the player left the network (or the chest is gone): put the items away
            releaseIncoming(m);
            phase = kind() == Kind.LOGISTIC ? storePhase(level, m) : Phase.RETURNING;
            return;
        }
        if (!reachable(level, at)) {
            dropTrip(level, m);
            return;
        }
        if (!arrive(at, target)) return;
        if (target.type() == Endpoint.Type.GHOST) {
            ConstructionManager cm = ConstructionManager.get(level);
            boolean built = Ghosts.build(level, target.pos(), null, carried());
            if (built) {
                beam(level, target.pos());
                setCarried(ItemStack.EMPTY);
                count = 0;
            }
            cm.queue().release(ConstructionManager.cell(target.pos()), robotId());
            target = null;
            phase = Phase.RETURNING;
            return;
        }
        releaseIncoming(m);
        ItemStack rest = give(level, target, carried());
        setCarried(rest);
        count = rest.getCount();
        target = null;
        phase = rest.isEmpty() ? Phase.RETURNING : storePhase(level, m);
    }

    private void releaseIncoming(LogisticsManager m) {
        if (target != null && target.type() != Endpoint.Type.GHOST && item != null && count > 0) {
            m.ledger().removeIncoming(target.nodeId(), item, count);
        }
    }

    /** {@link Phase#TO_STORE} with a storage chest of the network that takes the items; else drops them. */
    private Phase storePhase(ServerLevel level, LogisticsManager m) {
        if (carried().isEmpty()) return Phase.RETURNING;
        store = m.storageFor(level, carried(), blockPosition());
        if (store != null) return Phase.TO_STORE;
        drop(level, carried());
        setCarried(ItemStack.EMPTY);
        count = 0;
        return Phase.RETURNING;
    }

    private void toStore(ServerLevel level) {
        if (store == null || !reachable(level, hover(store))) {
            store = null;
            phase = Phase.RETURNING;
            return;
        }
        if (!fly(hover(store), speed())) return;
        IItemHandler h = LogisticsManager.handler(level, store);
        ItemStack rest = h == null ? carried() : ItemHandlerHelper.insertItemStacked(h, carried(), false);
        if (!rest.isEmpty()) drop(level, rest);
        setCarried(ItemStack.EMPTY);
        count = 0;
        store = null;
        phase = Phase.RETURNING;
    }

    private void returning(ServerLevel level) {
        if (kind() == Kind.PERSONAL_CONSTRUCTION) {
            Player p = LogisticsManager.player(level, homePlayer);
            if (p == null || !p.isAlive()) {
                lose(level);
                return;
            }
            if (!flyTo(playerHover(p), true)) return;
            if (!carried().isEmpty()) p.getInventory().placeItemBackInInventory(carried());
            setCarried(ItemStack.EMPTY);
            p.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CONSTRUCTION_ROBOT.get()));
            discard();
            return;
        }
        RoboportBlockEntity port = roboport(level, home);
        if (port == null) {
            port = LogisticsManager.nearestRoboport(level, position());
            if (port == null) {
                lose(level);
                return;
            }
            home = port.getBlockPos();
        }
        if (fly(port.dock(), speed())) phase = Phase.CHARGING;
    }

    private void charging(ServerLevel level) {
        RoboportBlockEntity port = roboport(level, home);
        if (port == null) {
            phase = Phase.RETURNING;
            return;
        }
        syncTarget(port.dock(), 0);
        if (!battery.isFull()) {
            if (port.charge(battery) > 0 && tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 1, 0.15, 0.1, 0.15, 0);
            }
            return;
        }
        if (!carried().isEmpty()) {
            phase = storePhase(level, LogisticsManager.get(level));
            return;
        }
        if (port.storeRobot(new ItemStack(robotItem()))) {
            port.robotReturned();
            discard();
        }
    }

    /**
     * Whether a leg toward {@code at} can go on ({@link dev.minetorio.core.robot.RobotReach}): the
     * target and this tick's next step lie in entity-ticking chunks. A robot flying into any other
     * chunk would freeze there holding its promises (or ghost claim) for good.
     */
    private boolean reachable(ServerLevel level, Vec3 at) {
        Flight.Vec next = Flight.step(new Flight.Vec(getX(), getY(), getZ()), new Flight.Vec(at.x, at.y, at.z), speed());
        return level.isPositionEntityTicking(BlockPos.containing(at))
                && level.isPositionEntityTicking(BlockPos.containing(next.x(), next.y(), next.z()));
    }

    /** Whether the robot holds promises or a ghost claim (on its way to a pickup, a delivery or a store). */
    boolean holdsTrip() {
        return phase == Phase.TO_SOURCE || phase == Phase.TO_TARGET || phase == Phase.TO_STORE;
    }

    /**
     * Gives up the trip: its ledger promises (or the ghost claim of a personal construction robot)
     * are released and the robot heads home with what it carries (a logistic robot stores it after
     * charging). Called by the robot when its way leads into a chunk that is not entity-ticking, and
     * by {@link LogisticsManager} for a robot whose own chunk stopped ticking (it resumes from here
     * once it ticks again).
     */
    void dropTrip(ServerLevel level, LogisticsManager m) {
        switch (phase) {
            case TO_SOURCE -> abortTrip(m);
            case TO_TARGET -> {
                if (target != null && target.type() == Endpoint.Type.GHOST) {
                    ConstructionManager.get(level).queue().release(ConstructionManager.cell(target.pos()), robotId());
                } else {
                    releaseIncoming(m);
                }
                target = null;
                phase = Phase.RETURNING;
            }
            case TO_STORE -> {
                store = null;
                phase = Phase.RETURNING;
            }
            default -> { }
        }
    }

    /** The trip cannot go on before pickup: release its promises and go home. */
    private void abortTrip(LogisticsManager m) {
        if (source != null && item != null) m.ledger().removeOutgoing(source.nodeId(), item, count);
        if (target != null && item != null) m.ledger().removeIncoming(target.nodeId(), item, count);
        count = 0;
        phase = Phase.RETURNING;
    }

    // --- endpoints ---------------------------------------------------------------------------------

    @Nullable
    private static Player player(ServerLevel level, Endpoint e) {
        Player p = LogisticsManager.player(level, e.player());
        return p != null && p.isAlive() && !p.isSpectator() ? p : null;
    }

    /** Where to fly for {@code e}, or null if it is gone. */
    @Nullable
    private static Vec3 position(ServerLevel level, @Nullable Endpoint e) {
        if (e == null) return null;
        if (e.type() == Endpoint.Type.PLAYER) {
            Player p = player(level, e);
            return p == null ? null : playerHover(p);
        }
        if (e.type() == Endpoint.Type.CHEST && LogisticsManager.handler(level, e.pos()) == null) return null;
        return hover(e.pos());
    }

    private static Vec3 playerHover(Player p) {
        return p.position().add(0, p.getBbHeight() + 0.4, 0);
    }

    private static Vec3 hover(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 1 + RobotStats.CRUISE_HEIGHT, p.getZ() + 0.5);
    }

    private boolean arrive(Vec3 at, Endpoint e) {
        return flyTo(at, e.type() == Endpoint.Type.PLAYER);
    }

    /** One tick toward {@code at}; a moving player counts as reached within {@link LogisticStats#PLAYER_REACH}. */
    private boolean flyTo(Vec3 at, boolean player) {
        boolean there = fly(at, speed());
        return there || (player && position().distanceTo(at) <= LogisticStats.PLAYER_REACH);
    }

    private static int take(ServerLevel level, Endpoint e, String itemId, int n) {
        Item wanted = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (e.type() == Endpoint.Type.PLAYER) {
            Player p = player(level, e);
            return p == null ? 0 : PlayerItems.take(p, wanted, n);
        }
        IItemHandler h = LogisticsManager.handler(level, e.pos());
        int got = 0;
        if (h != null) {
            for (int i = 0; i < h.getSlots() && got < n; i++) {
                if (h.getStackInSlot(i).is(wanted)) got += h.extractItem(i, n - got, false).getCount();
            }
        }
        return got;
    }

    private ItemStack give(ServerLevel level, Endpoint e, ItemStack stack) {
        if (e.type() == Endpoint.Type.PLAYER) {
            Player p = player(level, e);
            if (p == null) return stack;
            ItemStack copy = stack.copy();
            p.getInventory().add(copy);
            return copy;
        }
        IItemHandler h = LogisticsManager.handler(level, e.pos());
        return h == null ? stack : ItemHandlerHelper.insertItemStacked(h, stack, false);
    }

    private void beam(ServerLevel level, BlockPos to) {
        Vec3 from = position();
        Vec3 c = Vec3.atCenterOf(to);
        for (int i = 0; i <= 8; i++) {
            Vec3 p = from.lerp(c, i / 8.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void lose(ServerLevel level) {
        if (!carried().isEmpty()) drop(level, carried());
        setCarried(ItemStack.EMPTY);
        count = 0;
        drop(level, new ItemStack(robotItem()));
        discard();
    }

    private void drop(ServerLevel level, ItemStack stack) {
        if (!stack.isEmpty()) level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), stack.copy()));
    }

    @Nullable
    private static RoboportBlockEntity roboport(Level level, @Nullable BlockPos pos) {
        return pos != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RoboportBlockEntity be ? be : null;
    }

    // --- flight (as ConstructionRobotEntity) --------------------------------------------------------

    private boolean fly(Vec3 target, double speed) {
        syncTarget(target, (float) speed);
        Flight.Vec from = new Flight.Vec(getX(), getY(), getZ());
        Flight.Vec to = new Flight.Vec(target.x, target.y, target.z);
        Flight.Vec next = Flight.step(from, to, speed);
        battery.fly(Flight.distance(from, next));
        double dx = next.x() - from.x(), dz = next.z() - from.z();
        if (dx * dx + dz * dz > 1e-6) setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
        setPos(next.x(), next.y(), next.z());
        return Flight.distance(next, to) < ARRIVED;
    }

    private void syncTarget(Vec3 target, float speed) {
        BlockPos cell = BlockPos.containing(target);
        entityData.set(DATA_TARGET_CELL, cell);
        entityData.set(DATA_TARGET_OFFSET, new Vector3f((float) (target.x - cell.getX()), (float) (target.y - cell.getY()),
                (float) (target.z - cell.getZ())));
        entityData.set(DATA_SPEED, speed);
    }

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

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (clientFlight == null) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        clientFlight.sample(new Flight.Vec(x, y, z));
    }

    /** Bank angle in degrees (+ = right side down), interpolated for rendering. */
    public float bank(float partialTick) { return Mth.lerp(partialTick, bankO, bank); }

    // --- registration, removal, persistence ---------------------------------------------------------

    /** Re-enters this trip's promises after loading. */
    private void reRegister(LogisticsManager m) {
        registered = true;
        if (item == null || count <= 0) return;
        if (phase == Phase.TO_SOURCE && source != null && target != null) {
            m.ledger().addOutgoing(source.nodeId(), item, count);
            m.ledger().addIncoming(target.nodeId(), item, count);
        } else if (phase == Phase.TO_TARGET && target != null) {
            if (target.type() == Endpoint.Type.GHOST) {
                if (!ConstructionManager.get((ServerLevel) level()).queue().claim(ConstructionManager.cell(target.pos()), robotId())) {
                    target = null;
                    phase = Phase.RETURNING;
                }
            } else {
                m.ledger().addIncoming(target.nodeId(), item, count);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            LogisticsManager um = LogisticsManager.getIfPresent(server);
            if (um != null) um.untrack(this);
        }
        if (level() instanceof ServerLevel server && registered) {
            LogisticsManager m = LogisticsManager.getIfPresent(server);
            if (m != null) {
                if (item != null && count > 0) {
                    if (phase == Phase.TO_SOURCE && source != null && target != null) {
                        m.ledger().removeOutgoing(source.nodeId(), item, count);
                        m.ledger().removeIncoming(target.nodeId(), item, count);
                    } else if (phase == Phase.TO_TARGET && target != null && target.type() != Endpoint.Type.GHOST) {
                        m.ledger().removeIncoming(target.nodeId(), item, count);
                    }
                }
            }
            if (phase == Phase.TO_TARGET && target != null && target.type() == Endpoint.Type.GHOST) {
                ConstructionManager cm = ConstructionManager.getIfPresent(server);
                if (cm != null) cm.queue().release(ConstructionManager.cell(target.pos()), robotId());
            }
            if (reason == RemovalReason.KILLED) {
                RoboportBlockEntity port = roboport(server, home);
                if (port != null) port.robotReturned();
            }
        }
        super.remove(reason);
    }

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
        tag.putString("kind", kind().name());
        tag.putString("phase", phase.name());
        if (home != null) tag.put("home", NbtUtils.writeBlockPos(home));
        if (homePlayer != null) tag.putUUID("home_player", homePlayer);
        if (source != null) tag.put("source", source.save());
        if (target != null) tag.put("target", target.save());
        if (store != null) tag.put("store", NbtUtils.writeBlockPos(store));
        if (item != null) tag.putString("item", item);
        tag.putInt("count", count);
        tag.putLong("energy", battery.energy());
        if (!carried().isEmpty()) tag.put("carried", carried().save(registryAccess()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        try {
            setKind(Kind.valueOf(tag.getString("kind")));
        } catch (IllegalArgumentException e) {
            setKind(Kind.LOGISTIC);
        }
        try {
            phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.RETURNING;
        }
        home = NbtUtils.readBlockPos(tag, "home").orElse(null);
        homePlayer = tag.hasUUID("home_player") ? tag.getUUID("home_player") : null;
        source = tag.contains("source") ? Endpoint.load(tag.getCompound("source")) : null;
        target = tag.contains("target") ? Endpoint.load(tag.getCompound("target")) : null;
        store = NbtUtils.readBlockPos(tag, "store").orElse(null);
        item = tag.contains("item") ? tag.getString("item") : null;
        count = tag.getInt("count");
        battery = new RobotBattery(tag.contains("energy") ? tag.getLong("energy") : RobotStats.BATTERY_J);
        setCarried(tag.contains("carried") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("carried")) : ItemStack.EMPTY);
        if ((phase == Phase.TO_SOURCE && (source == null || target == null || item == null))
                || (phase == Phase.TO_TARGET && (target == null || item == null)) || (phase == Phase.TO_STORE && store == null)) {
            phase = Phase.RETURNING;
        }
        registered = false;
    }
}
