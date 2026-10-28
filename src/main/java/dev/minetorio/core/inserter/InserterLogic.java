package dev.minetorio.core.inserter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Inserter arm state machine. The hand holds up to {@link InserterType#handSize} items of one kind
 * (one for every inserter but the stack inserter). Fuel handling is the caller's job.
 *
 * <p>At the pickup the arm takes the first item the target wants (and the filter, if any, passes),
 * then as many more of the same item as the hand holds. From a {@linkplain PickupSource#streaming
 * streaming} source (a belt) a hand that is not full yet waits ({@code FILLING}) for more, and
 * swings once it is full or after {@link InserterType#fillTimeoutTicks} ticks without a new item.
 * At the target it drops as many items per tick as the target accepts and waits holding the rest.
 */
public final class InserterLogic {
    private enum Phase { AT_PICKUP, FILLING, TO_DROP, WAITING, RETURNING }

    private static final double EPS = 1e-9;

    private final InserterType type;
    /** Filter of a filter inserter; null for the others. */
    private final ItemFilter filter;
    private Phase phase = Phase.AT_PICKUP;
    private String held;
    private int count;
    private double legProgress;
    /** FILLING: ticks since the last item was added to the hand. */
    private int idle;

    public InserterLogic(InserterType type) {
        this.type = type;
        this.filter = type.filterSlots > 0 ? new ItemFilter(type.filterSlots) : null;
    }

    public InserterType type() { return type; }

    /** The item filter (filter inserter), or null. Changes apply from the next pickup on. */
    public ItemFilter filter() { return filter; }

    /** Whether this arm would pick {@code item} up for {@code dst} (target wants it, filter passes it). */
    private Predicate<String> pickable(DropTarget dst) {
        return filter == null ? dst::wants : item -> filter.matches(item) && dst.wants(item);
    }

    /** Whether there is something to pick up right now for {@code dst}. */
    public boolean canPickUp(PickupSource src, DropTarget dst) {
        return src.peek(pickable(dst)).isPresent();
    }

    public void tick(PickupSource src, DropTarget dst, double satisfaction) {
        if (phase == Phase.AT_PICKUP) {
            if (satisfaction <= 0) return;
            var item = src.peek(pickable(dst));
            if (item.isEmpty() || !src.take(item.get())) return;
            held = item.get();
            count = 1;
            fill(src);
            if (count < type.handSize && type.fillTimeoutTicks > 0 && src.streaming()) {
                phase = Phase.FILLING;
                idle = 0;
                return;
            }
            phase = Phase.TO_DROP;
            legProgress = 0;
        }
        if (phase == Phase.FILLING) {
            if (satisfaction <= 0) return;
            int before = count;
            fill(src);
            idle = count > before ? 0 : idle + 1;
            if (count < type.handSize && idle < type.fillTimeoutTicks) return;
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

    /** Takes more of the held item until the hand is full or the source has none. */
    private void fill(PickupSource src) {
        while (count < type.handSize && src.take(held)) count++;
    }

    private void tryDrop(DropTarget dst) {
        while (count > 0 && dst.accept(held)) count--;
        if (count == 0) {
            held = null;
            phase = Phase.RETURNING;
            legProgress = 0;
        }
    }

    /** Item currently in hand, or null. */
    public String held() { return held; }

    /** Number of items in hand (0 when empty). */
    public int heldCount() { return held == null ? 0 : count; }

    /** 0 = at pickup, 1 = at drop. */
    public double armProgress() {
        return switch (phase) {
            case AT_PICKUP, FILLING -> 0;
            case TO_DROP -> Math.min(1, legProgress);
            case WAITING -> 1;
            case RETURNING -> Math.max(0, 1 - legProgress);
        };
    }

    public boolean isMoving() { return phase == Phase.TO_DROP || phase == Phase.RETURNING; }

    /** Whether the arm waits at a belt for its hand to fill. */
    public boolean isFilling() { return phase == Phase.FILLING; }

    public long powerDemand() { return isMoving() ? type.powerPerTickJ : 0; }

    /**
     * Empties the hand (e.g. a burner inserter keeping fuel for itself, or the inserter broken); the
     * arm returns to the pickup from where it is now. Returns the item ({@link #heldCount()} of them
     * were in the hand, read it first), or null if the hand was empty.
     */
    public String takeHeld() {
        if (held == null) return null;
        String item = held;
        double arm = armProgress();
        held = null;
        count = 0;
        if (arm <= EPS) {
            phase = Phase.AT_PICKUP;
            legProgress = 0;
        } else {
            phase = Phase.RETURNING;
            legProgress = 1 - arm;
        }
        return item;
    }

    /**
     * State for saving: {@code phase} (String), {@code leg} (Double), {@code held} (String, absent if
     * empty), {@code count} (Integer, with {@code held}) and {@code idle} (Integer, while filling).
     */
    public Map<String, Object> save() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phase", phase.name());
        out.put("leg", legProgress);
        if (held != null) {
            out.put("held", held);
            out.put("count", count);
        }
        if (phase == Phase.FILLING) out.put("idle", idle);
        return out;
    }

    /**
     * Restores {@link #save()} output. Unknown phases become "at pickup"; inconsistent data is
     * repaired without losing an item: a held item is always carried to the target (or keeps
     * filling), and a carrying phase without an item becomes a return. A missing count is 1.
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
        int n = data.get("count") instanceof Number c ? Math.max(1, c.intValue()) : 1;
        boolean carrying = p == Phase.TO_DROP || p == Phase.WAITING || p == Phase.FILLING;
        if (item != null && !carrying) {
            p = Phase.TO_DROP;
            leg = 0;
        } else if (item == null && p == Phase.FILLING) {
            p = Phase.AT_PICKUP;
            leg = 0;
        } else if (item == null && carrying) {
            leg = p == Phase.WAITING ? 0 : 1 - leg;
            p = Phase.RETURNING;
        }
        phase = p;
        legProgress = p == Phase.FILLING ? 0 : leg;
        held = item;
        count = item == null ? 0 : n;
        idle = p == Phase.FILLING && data.get("idle") instanceof Number i ? Math.max(0, i.intValue()) : 0;
    }
}
