package dev.minetorio.core.belt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** One lane of a belt block. Items are kept sorted front-first (highest pos first). */
public final class BeltLane {
    public static final double SPACING = 0.25;
    private static final double EPS = 1e-9;

    private final double length;
    private final List<LaneItem> items = new ArrayList<>();

    public BeltLane(double length) {
        this.length = length;
    }

    public double length() { return length; }

    public boolean canInsertAt(double pos) {
        if (pos < 0 || pos > length) return false;
        for (LaneItem it : items) {
            if (Math.abs(it.pos() - pos) < SPACING - EPS) return false;
        }
        return true;
    }

    public boolean insertAt(String item, double pos) {
        if (!canInsertAt(pos)) return false;
        int i = 0;
        while (i < items.size() && items.get(i).pos() > pos) i++;
        items.add(i, new LaneItem(item, pos));
        return true;
    }

    /** @param next downstream receiver; null rejects everything. */
    public void tick(double speed, LaneSink next) {
        double limit = Double.POSITIVE_INFINITY;
        int i = 0;
        while (i < items.size()) {
            LaneItem it = items.get(i);
            double np = it.pos() + speed;
            if (i == 0 && np >= length) {
                if (next != null && next.offer(it.item(), np - length)) {
                    items.remove(0);
                    continue; // the following item is now the front
                }
                np = length;
            }
            if (i > 0) np = Math.min(np, limit);
            np = Math.max(np, it.pos());
            items.set(i, new LaneItem(it.item(), np));
            limit = np - SPACING;
            i++;
        }
    }

    /** Items front-first. */
    public List<LaneItem> items() { return List.copyOf(items); }

    public Optional<LaneItem> takeFirstMatching(Predicate<String> p) {
        for (int i = 0; i < items.size(); i++) {
            if (p.test(items.get(i).item())) return Optional.of(items.remove(i));
        }
        return Optional.empty();
    }

    public boolean isEmpty() { return items.isEmpty(); }

    /** A sink that inserts offered items at their overflow position (clamped to >= 0). */
    public LaneSink asSink() {
        return (item, overflow) -> insertAt(item, Math.max(0, overflow));
    }
}
