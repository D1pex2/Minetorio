package dev.minetorio.core.inserter;

/** Inserter arm state machine. Holds at most one item. Fuel handling is the caller's job. */
public final class InserterLogic {
    private enum Phase { AT_PICKUP, TO_DROP, WAITING, RETURNING }

    private static final double EPS = 1e-9;

    private final InserterType type;
    private Phase phase = Phase.AT_PICKUP;
    private String held;
    private double legProgress;

    public InserterLogic(InserterType type) { this.type = type; }

    public InserterType type() { return type; }

    public void tick(PickupSource src, DropTarget dst, double satisfaction) {
        if (phase == Phase.AT_PICKUP) {
            if (satisfaction <= 0) return;
            var item = src.peek(dst::wants);
            if (item.isEmpty() || !src.take(item.get())) return;
            held = item.get();
            phase = Phase.TO_DROP;
            legProgress = 0;
        }
        if (phase == Phase.WAITING) {
            tryDrop(dst);
            return;
        }
        if (satisfaction <= 0) return;
        legProgress += satisfaction / (type.cycleTicks / 2.0);
        if (legProgress < 1 - EPS) return;
        if (phase == Phase.TO_DROP) {
            phase = Phase.WAITING;
            tryDrop(dst);
        } else {
            phase = Phase.AT_PICKUP;
            legProgress = 0;
        }
    }

    private void tryDrop(DropTarget dst) {
        if (dst.accept(held)) {
            held = null;
            phase = Phase.RETURNING;
            legProgress = 0;
        }
    }

    /** Item currently in hand, or null. */
    public String held() { return held; }

    /** 0 = at pickup, 1 = at drop. */
    public double armProgress() {
        return switch (phase) {
            case AT_PICKUP -> 0;
            case TO_DROP -> Math.min(1, legProgress);
            case WAITING -> 1;
            case RETURNING -> Math.max(0, 1 - legProgress);
        };
    }

    public boolean isMoving() { return phase == Phase.TO_DROP || phase == Phase.RETURNING; }

    public long powerDemand() { return isMoving() ? type.powerPerTickJ : 0; }
}
