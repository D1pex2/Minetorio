package dev.minetorio.core.research;

import java.util.*;

/**
 * A research lab. A unit starts when a technology is current and the lab holds one unit's worth of every
 * pack; the packs are deducted at the start. If the current technology changes mid-unit, the unit in
 * progress is aborted without refund.
 */
public final class LabLogic {
    private static final Set<String> ACCEPTED = Set.of(
            "minetorio:automation_science_pack", "minetorio:logistic_science_pack");
    private static final int AUTOMATED_LIMIT = 2;
    private static final int MANUAL_LIMIT = 64;
    private static final double EPS = 1e-9;

    private final double speed;
    private final long powerPerTickJ;
    private final Map<String, Integer> packs = new LinkedHashMap<>();
    private boolean working;
    private double progress;
    private String unitTech;

    public LabLogic(double speed, long powerPerTickJ) {
        this.speed = speed;
        this.powerPerTickJ = powerPerTickJ;
    }

    /** Returns the number of packs accepted. */
    public int insertPack(String item, int count, boolean automated) {
        if (!ACCEPTED.contains(item) || count <= 0) return 0;
        int have = packs.getOrDefault(item, 0);
        int room = automated ? (have < AUTOMATED_LIMIT ? AUTOMATED_LIMIT - have : 0) : MANUAL_LIMIT - have;
        int n = Math.max(0, Math.min(count, room));
        if (n > 0) packs.put(item, have + n);
        return n;
    }

    public void tick(ResearchState s, double satisfaction) {
        Optional<Technology> cur = s.current();
        if (working && (cur.isEmpty() || !cur.get().id().equals(unitTech))) working = false;
        if (!working) {
            if (cur.isEmpty()) return;
            Technology t = cur.get();
            for (Map.Entry<String, Integer> e : t.packsPerUnit().entrySet())
                if (packs.getOrDefault(e.getKey(), 0) < e.getValue()) return;
            for (Map.Entry<String, Integer> e : t.packsPerUnit().entrySet()) {
                int left = packs.get(e.getKey()) - e.getValue();
                if (left == 0) packs.remove(e.getKey()); else packs.put(e.getKey(), left);
            }
            working = true;
            progress = 0;
            unitTech = t.id();
        }
        progress += satisfaction * speed / cur.get().ticksPerUnit();
        if (progress >= 1 - EPS) {
            working = false;
            progress = 0;
            s.completeUnit();
        }
    }

    public long powerDemand() { return working ? powerPerTickJ : 0; }

    public Map<String, Integer> packs() { return Collections.unmodifiableMap(packs); }
}
