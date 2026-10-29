package dev.minetorio.core.machine;

import dev.minetorio.core.item.ItemStackSizes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fuel slot that supplies energy to a burner machine, burning fuel as needed. */
public final class Burner {
    private static final int AUTOMATED_FUEL_LIMIT = 5;

    private final long powerPerTickJ;
    private final Map<String, Long> fuelValues;
    private final Map<String, Integer> fuel = new LinkedHashMap<>();
    private long remainingJ;
    private java.util.function.Consumer<String> fuelBurned = item -> {};

    public void onFuelBurned(java.util.function.Consumer<String> listener) { fuelBurned = listener == null ? item -> {} : listener; }

    public Burner(long powerPerTickJ, Map<String, Long> fuelValues) {
        this.powerPerTickJ = powerPerTickJ;
        this.fuelValues = Map.copyOf(fuelValues);
    }

    /** Inserts fuel; returns the amount accepted. Automated insertion keeps the slot under 5 items. */
    public int insertFuel(String item, int count, boolean automated) {
        if (count <= 0) return 0;
        int accepted = Math.min(count, fuelRoom(item, automated));
        if (accepted > 0) fuel.merge(item, accepted, Integer::sum);
        return accepted;
    }

    /** How many of {@code item} {@link #insertFuel} would accept right now (0 if it is not a fuel). */
    public int fuelRoom(String item, boolean automated) {
        if (!fuelValues.containsKey(item)) return 0;
        int limit = automated ? AUTOMATED_FUEL_LIMIT : ItemStackSizes.limit(item);
        return Math.max(0, limit - fuel.getOrDefault(item, 0));
    }

    /** Takes up to {@code max} unburnt fuel items back; returns the amount taken. */
    public int extractFuel(String item, int max) {
        int have = fuel.getOrDefault(item, 0);
        int taken = Math.max(0, Math.min(have, max));
        if (taken == have) fuel.remove(item);
        else if (taken > 0) fuel.put(item, have - taken);
        return taken;
    }

    /** Fraction (0..1) of this tick's energy demand that was obtained, burning fuel as needed. */
    public double draw() { return powerPerTickJ <= 0 ? 1.0 : draw(powerPerTickJ); }

    /** Burns only up to {@code requestJ} this tick; returns the fraction of it delivered (0 and nothing burnt if requestJ <= 0). */
    public double draw(long requestJ) {
        long need = requestJ;
        if (need <= 0) return 0.0;
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
            String burnedItem = e.getKey();
            if (e.getValue() == 1) it.remove(); else e.setValue(e.getValue() - 1);
            fuelBurned.accept(burnedItem);
            return true;
        }
        return false;
    }

    public long remainingJ() { return remainingJ; }

    public Map<String, Integer> fuel() { return Map.copyOf(fuel); }

    /** Restores saved state ({@link #fuel()}, {@link #remainingJ()}); unknown fuels and counts &lt;= 0 are skipped. */
    public void load(Map<String, Integer> savedFuel, long savedRemainingJ) {
        fuel.clear();
        savedFuel.forEach((item, count) -> {
            if (count != null && count > 0 && fuelValues.containsKey(item)) fuel.put(item, Math.min(count, ItemStackSizes.limit(item)));
        });
        remainingJ = Math.max(0, savedRemainingJ);
    }
}
