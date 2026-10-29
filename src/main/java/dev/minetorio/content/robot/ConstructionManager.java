package dev.minetorio.content.robot;

import dev.minetorio.core.robot.Cell;
import dev.minetorio.core.robot.ConstructionQueue;
import dev.minetorio.core.robot.ProviderLedger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Construction state of one server level, in memory only: the {@link ConstructionQueue} (loaded
 * ghosts register and unregister themselves; their order number is saved with the ghost), the
 * {@link ProviderLedger} of items promised to robots, and the loaded roboports. Robots save their
 * own job and re-claim it when they load.
 *
 * <p>Deconstruction and upgrade jobs mirror the saved marks of {@link ConstructionMarks}, which
 * attaches itself to the queue when the manager is created.
 */
public final class ConstructionManager {
    private static final Map<ServerLevel, ConstructionManager> MANAGERS = new WeakHashMap<>();

    private final ConstructionQueue queue = new ConstructionQueue();
    private final ProviderLedger ledger = new ProviderLedger();
    private final Set<BlockPos> roboports = new HashSet<>();
    /** Loaded robots holding a job claim. */
    private final Set<ConstructionRobotEntity> working = new HashSet<>();
    static final int SWEEP_INTERVAL = 10;

    private ConstructionManager() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel server) MANAGERS.remove(server);
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel server && server.getGameTime() % SWEEP_INTERVAL == 0) {
                ConstructionManager m = MANAGERS.get(server);
                if (m != null) m.sweep(server);
            }
        });
    }

    /**
     * Robots holding a job whose chunk stopped entity-ticking (the player walked away) do not tick
     * and cannot let go themselves: their claim and reservations are released here, and they are
     * set to head home (with what they carry) once their chunk ticks again. The robot's saved state
     * then holds no job either, so an unload does not bring the claim back.
     */
    void sweep(ServerLevel level) {
        for (ConstructionRobotEntity r : new ArrayList<>(working)) {
            if (r.isRemoved()) working.remove(r);
            else if (!level.isPositionEntityTicking(r.blockPosition())) r.dropJob(this);
        }
    }

    /** A robot took a job (its claim is in the queue). */
    void working(ConstructionRobotEntity robot) { working.add(robot); }

    /** A robot no longer holds a job, or left the level. */
    void idle(ConstructionRobotEntity robot) { working.remove(robot); }

    public static ConstructionManager get(ServerLevel level) {
        ConstructionManager m = MANAGERS.get(level);
        if (m == null) {
            m = new ConstructionManager();
            MANAGERS.put(level, m);
            ConstructionMarks marks = ConstructionMarks.get(level);
            marks.sweep(level);
            marks.attach(m.queue);
        }
        return m;
    }

    @Nullable
    public static ConstructionManager getIfPresent(ServerLevel level) {
        return MANAGERS.get(level);
    }

    public static Cell cell(BlockPos p) { return new Cell(p.getX(), p.getY(), p.getZ()); }

    public static BlockPos pos(Cell c) { return new BlockPos(c.x(), c.y(), c.z()); }

    public ConstructionQueue queue() { return queue; }

    public ProviderLedger ledger() { return ledger; }

    void addRoboport(BlockPos controller) { roboports.add(controller.immutable()); }

    void removeRoboport(BlockPos controller) { roboports.remove(controller); }

    /** Loaded roboport controllers. */
    public List<BlockPos> roboports() { return new ArrayList<>(roboports); }
}
