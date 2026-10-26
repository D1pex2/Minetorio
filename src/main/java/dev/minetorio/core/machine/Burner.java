package dev.minetorio.core.machine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fuel slot that supplies energy to a burner machine, burning fuel as needed. */
public final class Burner {
    private static final int AUTOMATED_FUEL_LIMIT = 5;
    private static final int STACK_LIMIT = 64;

    private final long powerPerTickJ;
    private final Map<String, Long> fuelValues;
    private final Map<String, Integer> fuel = new LinkedHashMap<>();
    private long remainingJ;

    public Burner(long powerPerTickJ, Map<String, Long> fuelValues) {
        this.powerPerTickJ = powerPerTickJ;
        this.fuelValues = Map.copyOf(fuelValues);
    }

    /** Inserts fuel; returns the amount accepted. Automated insertion keeps the slot under 5 items. */
    public int insertFuel(String item, int count, boolean automated) {
        if (count <= 0 || !fuelValues.containsKey(item)) return 0;
        int have = fuel.getOrDefault(item, 0);
        int limit = automated ? AUTOMATED_FUEL_LIMIT : STACK_LIMIT;
        int accepted = Math.max(0, Math.min(count, limit - have));
        if (accepted > 0) fuel.merge(item, accepted, Integer::sum);
        return accepted;
    }

    /** Fraction (0..1) of this tick's energy demand that was obtained, burning fuel as needed. */
    public double draw() {
        long need = powerPerTickJ;
        if (need <= 0) return 1.0;
        long got = 0;
        while (got < need) {
            if (remainingJ <= 0 && !burnNext()) break;
            long take = Math.min(remainingJ, need - got);
            remainingJ -= take;
            got += take;
        }
        return (double) got / need;
    }

    private boolean burnNext() {
        for (var it = fuel.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            remainingJ += fuelValues.get(e.getKey());
            if (e.getValue() == 1) it.remove(); else e.setValue(e.getValue() - 1);
            return true;
        }
        return false;
    }

    public long remainingJ() { return remainingJ; }

    public Map<String, Integer> fuel() { return Map.copyOf(fuel); }
}
