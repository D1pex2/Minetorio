package dev.minetorio.content.robot;

import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.robot.ConstructionArea;
import dev.minetorio.core.robot.Flight;
import dev.minetorio.core.robot.RobotBattery;
import dev.minetorio.core.robot.RobotStats;
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
 */
public class ConstructionRobotEntity extends Entity {
    public enum Phase { TO_PROVIDER, TO_GHOST, TO_STORE, RETURNING, CHARGING }

    private static final EntityDataAccessor<ItemStack> DATA_CARRIED =
            SynchedEntityData.defineId(ConstructionRobotEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final double ARRIVED = 1e-3;

    private Phase phase = Phase.RETURNING;
    @Nullable private BlockPos home;
    @Nullable private BlockPos job;
    @Nullable private BlockPos provider;
    @Nullable private String item;
    private RobotBattery battery = RobotBattery.full();
    /** Job and reservation registered with the manager since the robot was (re)loaded. */
    private boolean registered;

    public ConstructionRobotEntity(EntityType<? extends ConstructionRobotEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CARRIED, ItemStack.EMPTY);
    }

    /** Non-zero id used for job claims. */
    public long robotId() {
        UUID u = getUUID();
        long id = u.getMostSignificantBits() ^ u.getLeastSignificantBits();
        return id == 0 ? 1 : id;
    }

    public ItemStack carried() { return entityData.get(DATA_CARRIED); }

    private void setCarried(ItemStack s) { entityData.set(DATA_CARRIED, s); }

    public Phase phase() { return phase; }

    public RobotBattery battery() { return battery; }

    @Nullable
    public BlockPos home() { return home; }

    /** Sends a freshly launched robot (job claimed, provider item reserved by the roboport). */
    void startJob(BlockPos home, BlockPos job, BlockPos provider, String item) {
        this.home = home.immutable();
        this.job = job.immutable();
        this.provider = provider.immutable();
        this.item = item;
        this.phase = Phase.TO_PROVIDER;
        this.registered = true;
    }

    // --- ticking ---------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        ConstructionManager m = ConstructionManager.get(server);
        if (!registered) reRegister(m);
        switch (phase) {
            case TO_PROVIDER -> { if (fly(hover(provider))) pickUp(server, m); }
            case TO_GHOST -> { if (fly(hover(job))) buildGhost(server, m); }
            case TO_STORE -> { if (fly(hover(provider))) putAway(server); }
            case RETURNING -> returning(server);
            case CHARGING -> charging(server);
        }
    }

    private void reRegister(ConstructionManager m) {
        registered = true;
        if (phase == Phase.TO_PROVIDER || phase == Phase.TO_GHOST) {
            if (job == null || !m.queue().claim(ConstructionManager.cell(job), robotId())) {
                // someone else took it (or it is gone): give up the job
                job = null;
                phase = carried().isEmpty() ? Phase.RETURNING : storePhase((ServerLevel) level());
                return;
            }
            if (phase == Phase.TO_PROVIDER && provider != null && item != null) {
                m.ledger().reserve(ConstructionManager.cell(provider), item);
            }
        }
    }

    private static Vec3 hover(@Nullable BlockPos p) {
        if (p == null) return Vec3.ZERO;
        return new Vec3(p.getX() + 0.5, p.getY() + 1 + RobotStats.CRUISE_HEIGHT, p.getZ() + 0.5);
    }

    /** One tick toward {@code target}; true once there. */
    private boolean fly(Vec3 target) {
        Flight.Vec from = new Flight.Vec(getX(), getY(), getZ());
        Flight.Vec to = new Flight.Vec(target.x, target.y, target.z);
        Flight.Vec next = Flight.step(from, to, RobotStats.SPEED_BLOCKS_PER_TICK * battery.speedFactor());
        double moved = Flight.distance(from, next);
        battery.fly(moved);
        double dx = next.x() - from.x(), dz = next.z() - from.z();
        if (dx * dx + dz * dz > 1e-6) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            setYHeadRot(yaw);
        }
        setPos(next.x(), next.y(), next.z());
        return Flight.distance(next, to) < ARRIVED;
    }

    private void pickUp(ServerLevel level, ConstructionManager m) {
        m.ledger().release(ConstructionManager.cell(provider), item);
        ItemStack got = ItemStack.EMPTY;
        Item wanted = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, provider, null);
        if (h != null) {
            for (int i = 0; i < h.getSlots() && got.isEmpty(); i++) {
                if (h.getStackInSlot(i).is(wanted)) got = h.extractItem(i, 1, false);
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
        boolean built = Ghosts.build(level, job, null, carried());
        if (built) {
            beam(level, job);
            setCarried(ItemStack.EMPTY);
        }
        releaseJob(m);
        job = null;
        phase = built ? Phase.RETURNING : storePhase(level);
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

    /** {@link Phase#TO_STORE} with the nearest provider in the home area that takes the item; else drops it and returns. */
    private Phase storePhase(ServerLevel level) {
        ItemStack c = carried();
        RoboportBlockEntity port = roboport(level, home);
        if (port != null) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : RoboportBlockEntity.providers(level, port.area())) {
                IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
                if (h == null || !ItemHandlerHelper.insertItemStacked(h, c.copy(), true).isEmpty()) continue;
                double d = p.distToCenterSqr(position());
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            if (best != null) {
                provider = best;
                return Phase.TO_STORE;
            }
        }
        drop(level, c);
        setCarried(ItemStack.EMPTY);
        return Phase.RETURNING;
    }

    private void putAway(ServerLevel level) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, provider, null);
        ItemStack rest = h == null ? carried() : ItemHandlerHelper.insertItemStacked(h, carried(), false);
        if (!rest.isEmpty()) drop(level, rest);
        setCarried(ItemStack.EMPTY);
        phase = Phase.RETURNING;
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
        if (!carried().isEmpty()) drop(level, carried());
        setCarried(ItemStack.EMPTY);
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
                    m.ledger().release(ConstructionManager.cell(provider), item);
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
        if (!carried().isEmpty()) tag.put("carried", carried().save(registryAccess()));
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
        setCarried(tag.contains("carried") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("carried")) : ItemStack.EMPTY);
        if ((phase == Phase.TO_PROVIDER && (provider == null || item == null)) || (phase == Phase.TO_GHOST && job == null)
                || (phase == Phase.TO_STORE && provider == null)) {
            phase = Phase.RETURNING;
        }
        registered = false;
    }
}
