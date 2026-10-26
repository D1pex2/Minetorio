package dev.minetorio.core.deposit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Deposits placed at fixed spots by a command (the demo showcase), on top of the generated ones.
 * Ids run down from {@value #FIRST_ID} to {@value #LAST_ID}: below the starting deposits' -1..-4
 * and above the GameTest overrides' ids (below -1 000 000). A new deposit replaces placed deposits
 * whose circle intersects its own, so placing again at the same spot does not pile them up.
 */
public final class PlacedDeposits {
    public static final long FIRST_ID = -1000;
    public static final long LAST_ID = -999_999;

    /** The deposit just placed and the placed deposits it replaced. */
    public record Placement(Deposit added, List<Deposit> removed) {}

    private final List<Deposit> deposits = new ArrayList<>();

    public List<Deposit> all() { return List.copyOf(deposits); }

    public Placement place(OreType type, int centerX, int centerZ, int radius, long amount) {
        Deposit probe = new Deposit(0, type, centerX, centerZ, radius, amount);
        List<Deposit> removed = new ArrayList<>();
        deposits.removeIf(d -> {
            boolean hit = intersects(d, probe);
            if (hit) removed.add(d);
            return hit;
        });
        long id = FIRST_ID;
        for (Deposit d : deposits) id = Math.min(id, d.id() - 1);
        if (id < LAST_ID) throw new IllegalStateException("no placed deposit ids left");
        Deposit added = new Deposit(id, type, centerX, centerZ, radius, amount);
        deposits.add(added);
        return new Placement(added, List.copyOf(removed));
    }

    public boolean remove(Deposit d) { return deposits.remove(d); }

    /** The placed deposit covering column (x, z), if any. */
    public Optional<Deposit> at(int x, int z) {
        for (Deposit d : deposits) {
            if (d.contains(x, z)) return Optional.of(d);
        }
        return Optional.empty();
    }

    /** The accepted placed deposit with the closest center within {@code maxDistance} of (x, z), if any. */
    public Optional<Deposit> nearest(int x, int z, int maxDistance, Predicate<Deposit> accept) {
        long maxSq = (long) maxDistance * maxDistance;
        Deposit best = null;
        for (Deposit d : deposits) {
            long sq = distSq(d, x, z);
            if (sq <= maxSq && (best == null || sq < distSq(best, x, z)) && accept.test(d)) best = d;
        }
        return Optional.ofNullable(best);
    }

    /** Replaces the contents with saved deposits; ids outside the placed range are dropped. */
    public void load(List<Deposit> saved) {
        deposits.clear();
        for (Deposit d : saved) {
            if (d.id() <= FIRST_ID && d.id() >= LAST_ID) deposits.add(d);
        }
    }

    public static long distSq(Deposit d, int x, int z) {
        long dx = (long) x - d.centerX();
        long dz = (long) z - d.centerZ();
        return dx * dx + dz * dz;
    }

    private static boolean intersects(Deposit a, Deposit b) {
        long r = (long) a.radius() + b.radius();
        return distSq(a, b.centerX(), b.centerZ()) <= r * r;
    }
}
