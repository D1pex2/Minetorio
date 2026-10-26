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
        if (count <= 0) return 0;
        int n = Math.min(count, packRoom(item, automated));
        if (n > 0) packs.merge(item, n, Integer::sum);
        return n;
    }

    /** How many more of {@code item} {@link #insertPack} would accept now (0 for anything but a pack). */
    public int packRoom(String item, boolean automated) {
        if (!ACCEPTED.contains(item)) return 0;
        int have = packs.getOrDefault(item, 0);
        return Math.max(0, (automated ? AUTOMATED_LIMIT : MANUAL_LIMIT) - have);
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

    /** Whether a unit is in progress (its packs already deducted). */
    public boolean isWorking() { return working; }

    /** Technology of the unit in progress, or null. */
    public String unitTech() { return working ? unitTech : null; }

    /** Progress (0..1) of the unit in progress; 0 when idle. */
    public double progress() { return working ? progress : 0; }

    /**
     * Restores saved state: the stored packs (non-packs and counts &lt;= 0 skipped) and the unit in
     * progress ({@code unitTech} null = idle).
     */
    public void load(Map<String, Integer> savedPacks, String savedUnitTech, double savedProgress) {
        packs.clear();
        savedPacks.forEach((k, v) -> { if (ACCEPTED.contains(k) && v != null && v > 0) packs.put(k, v); });
        working = savedUnitTech != null;
        unitTech = savedUnitTech;
        progress = working ? savedProgress : 0;
    }
}
