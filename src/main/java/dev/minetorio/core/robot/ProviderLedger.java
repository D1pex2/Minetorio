package dev.minetorio.core.robot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntBiFunction;

/**
 * Items promised to robots on their way to a provider (a chest). A provider can hand out an item
 * only while its stock exceeds what is already reserved there.
 */
public final class ProviderLedger {
    private final Map<Cell, Map<String, Integer>> reserved = new HashMap<>();

    public void reserve(Cell provider, String item) {
        reserved.computeIfAbsent(provider, k -> new HashMap<>()).merge(item, 1, Integer::sum);
    }

    public void release(Cell provider, String item) {
        Map<String, Integer> m = reserved.get(provider);
        if (m == null) return;
        m.computeIfPresent(item, (k, v) -> v <= 1 ? null : v - 1);
        if (m.isEmpty()) reserved.remove(provider);
    }

    public int reserved(Cell provider, String item) {
        Map<String, Integer> m = reserved.get(provider);
        return m == null ? 0 : m.getOrDefault(item, 0);
    }

    /** The provider nearest to {@code to} holding {@code item} beyond its reservations. */
    public Optional<Cell> pick(List<Cell> providers, Cell to, String item, ToIntBiFunction<Cell, String> stock) {
        Cell best = null;
        long bestD = Long.MAX_VALUE;
        for (Cell p : providers) {
            if (stock.applyAsInt(p, item) - reserved(p, item) <= 0) continue;
            long d = p.distSq(to);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }
}
