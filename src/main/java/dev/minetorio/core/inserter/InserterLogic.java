package dev.minetorio.core.inserter;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Removes the item in hand (e.g. a burner inserter keeping fuel for itself); the arm returns
     * to the pickup from where it is now. Returns the item, or null if the hand was empty.
     */
    public String takeHeld() {
        if (held == null) return null;
        String item = held;
        double arm = armProgress();
        held = null;
        if (arm <= EPS) {
            phase = Phase.AT_PICKUP;
            legProgress = 0;
        } else {
            phase = Phase.RETURNING;
            legProgress = 1 - arm;
        }
        return item;
    }

    /** State for saving: {@code phase} (String), {@code leg} (Double), {@code held} (String, absent if empty). */
    public Map<String, Object> save() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phase", phase.name());
        out.put("leg", legProgress);
        if (held != null) out.put("held", held);
        return out;
    }

    /**
     * Restores {@link #save()} output. Unknown phases become "at pickup"; inconsistent data is
     * repaired without losing an item: a held item is always carried to the target, and a
     * carrying phase without an item becomes a return.
     */
    public void load(Map<String, ?> data) {
        Phase p;
        try {
            p = Phase.valueOf(String.valueOf(data.get("phase")));
        } catch (IllegalArgumentException e) {
            p = Phase.AT_PICKUP;
        }
        double leg = data.get("leg") instanceof Number n ? Math.max(0, Math.min(1, n.doubleValue())) : 0;
        String item = data.get("held") instanceof String s ? s : null;
        boolean carrying = p == Phase.TO_DROP || p == Phase.WAITING;
        if (item != null && !carrying) {
            p = Phase.TO_DROP;
            leg = 0;
        } else if (item == null && carrying) {
            leg = p == Phase.WAITING ? 0 : 1 - leg;
            p = Phase.RETURNING;
        }
        phase = p;
        legProgress = leg;
        held = item;
    }
}
