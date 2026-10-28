package dev.minetorio.core.robot;

import java.util.List;
import java.util.function.Predicate;

/**
 * Where construction robots may go. An entity in a chunk that is loaded but not entity-ticking does
 * not tick: a robot flying into one freezes there and keeps holding its job claim and provider
 * reservation, so the job never completes and the robot never returns. Robots therefore only take
 * jobs whose cell (and provider) is entity-ticking, and drop a job as soon as its target, their next
 * step or their position outside the home area makes it unreachable.
 *
 * <p>The {@code ticking} predicates are supplied by the content side (entity-ticking chunks).
 */
public final class RobotReach {
    /** Horizontal slack around the home area before a robot holding a job is called back. */
    public static final double AREA_MARGIN = 2.0;

    private RobotReach() {}

    /** The jobs of {@code pending} (order kept) whose cell is in a ticking chunk. */
    public static List<ConstructionQueue.Job> dispatchable(List<ConstructionQueue.Job> pending, Predicate<Cell> ticking) {
        return pending.stream().filter(j -> ticking.test(j.cell())).toList();
    }

    /** The providers (or storage chests) of {@code cells} (order kept) in ticking chunks. */
    public static List<Cell> reachable(List<Cell> cells, Predicate<Cell> ticking) {
        return cells.stream().filter(ticking).toList();
    }

    /**
     * Whether a robot holding a job keeps it this tick: its target and the cell of its next step are
     * ticking, and it is inside its home roboport's area. Otherwise it drops the job (releases the
     * claim and any reservation) and heads home.
     */
    public static boolean keepJob(boolean targetTicking, boolean nextStepTicking, boolean insideHomeArea) {
        return targetTicking && nextStepTicking && insideHomeArea;
    }

    /** Whether the point is inside {@code area} horizontally, with {@link #AREA_MARGIN} to spare. */
    public static boolean insideHorizontally(ConstructionArea area, double x, double z) {
        return Math.abs(x - area.centerX()) < area.radius() + AREA_MARGIN
                && Math.abs(z - area.centerZ()) < area.radius() + AREA_MARGIN;
    }
}
