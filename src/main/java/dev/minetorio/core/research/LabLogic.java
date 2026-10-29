package dev.minetorio.core.research;

import java.util.*;

/**
 * A research lab. A unit starts when a technology is current and the lab holds one unit's worth of every
 * pack; the packs are deducted at the start and held by the unit. If the current technology changes
 * (or another lab completes it) mid-unit, the unit is aborted and its packs go back to the stored packs
 * (ignoring the insertion limits), so no pack is lost (ruling R23).
 */
public final class LabLogic {
    /** Implemented science types, in lab slot/display order; shared by the content layer. */
    public static final List<String> SCIENCE_PACKS = List.of(
            "minetorio:automation_science_pack", "minetorio:logistic_science_pack",
            "minetorio:chemical_science_pack", "minetorio:production_science_pack", "minetorio:utility_science_pack");
    private static final Set<String> ACCEPTED = Set.copyOf(SCIENCE_PACKS);
    private static final int AUTOMATED_LIMIT = 2;
    private static final double EPS = 1e-9;

    private java.util.function.Consumer<Map<String,Integer>> completed=packs->{};
    public void onUnitCompleted(java.util.function.Consumer<Map<String,Integer>> listener) {completed=Objects.requireNonNull(listener);}
    private final double speed;
    private final long powerPerTickJ;
    private final Map<String, Integer> packs = new LinkedHashMap<>();
    private boolean working;
    private double progress;
    private String unitTech;
    /** Packs deducted for the unit in progress (refunded on abort). */
    private final Map<String, Integer> unitPacks = new LinkedHashMap<>();

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
        return Math.max(0, (automated ? AUTOMATED_LIMIT : dev.minetorio.core.item.ItemStackSizes.limit(item)) - have);
    }

    /** Removes only stored packs; packs already committed to a running unit remain held. */
    public int extractPack(String item,int maximum) {
        int n=Math.max(0,Math.min(maximum,packs.getOrDefault(item,0)));
        if(n>0) {int left=packs.get(item)-n;if(left==0)packs.remove(item);else packs.put(item,left);}
        return n;
    }

    public void tick(ResearchState s, double satisfaction) {
        Optional<Technology> cur = s.current();
        if (working && (cur.isEmpty() || !cur.get().id().equals(unitTech))) abort();
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
            unitPacks.clear();
            unitPacks.putAll(t.packsPerUnit());
        }
        progress += satisfaction * speed / cur.get().ticksPerUnit();
        if (progress >= 1 - EPS) {
            completed.accept(Map.copyOf(unitPacks));
            working = false;
            progress = 0;
            unitPacks.clear();
            s.completeUnit();
        }
    }

    /** Ends the unit in progress and puts its packs back into the stored packs. */
    private void abort() {
        unitPacks.forEach((k, v) -> packs.merge(k, v, Integer::sum));
        unitPacks.clear();
        working = false;
        progress = 0;
    }

    public long powerDemand() { return working ? powerPerTickJ : 0; }

    public Map<String, Integer> packs() { return Collections.unmodifiableMap(packs); }

    /** Whether a unit is in progress (its packs already deducted). */
    public boolean isWorking() { return working; }

    /** Technology of the unit in progress, or null. */
    public String unitTech() { return working ? unitTech : null; }

    /** Packs held by the unit in progress (empty when idle). */
    public Map<String, Integer> unitPacks() { return Collections.unmodifiableMap(unitPacks); }

    /** Progress (0..1) of the unit in progress; 0 when idle. */
    public double progress() { return working ? progress : 0; }

    /**
     * Restores saved state: the stored packs and the packs held by the unit in progress (non-packs and
     * counts &lt;= 0 skipped), and the unit in progress ({@code unitTech} null = idle; its packs then go
     * back to the stored packs).
     */
    public void load(Map<String, Integer> savedPacks, String savedUnitTech, Map<String, Integer> savedUnitPacks,
                     double savedProgress) {
        packs.clear();
        unitPacks.clear();
        savedPacks.forEach((k, v) -> { if (ACCEPTED.contains(k) && v != null && v > 0) packs.put(k, v); });
        Map<String, Integer> into = savedUnitTech != null ? unitPacks : packs;
        savedUnitPacks.forEach((k, v) -> { if (ACCEPTED.contains(k) && v != null && v > 0) into.merge(k, v, Integer::sum); });
        working = savedUnitTech != null;
        unitTech = savedUnitTech;
        progress = working ? savedProgress : 0;
    }
}
