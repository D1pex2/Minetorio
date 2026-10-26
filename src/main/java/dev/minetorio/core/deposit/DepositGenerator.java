package dev.minetorio.core.deposit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Deterministic deposit generation: each 64x64 region holds at most one deposit, derived only
 * from (worldSeed, rx, rz), so results never depend on query order.
 */
public final class DepositGenerator {
    public static final int REGION = 64;
    public static final double CHANCE = 0.35;
    public static final int MIN_RADIUS = 6;
    public static final int MAX_RADIUS = 14;
    public static final int SPAWN_EXCLUSION = 80;
    public static final int STARTING_RADIUS = 10;

    private final long worldSeed;
    private final int spawnX;
    private final int spawnZ;
    private final List<Deposit> starting;

    public DepositGenerator(long worldSeed, int spawnX, int spawnZ) {
        this.worldSeed = worldSeed;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.starting = List.of(
                startingDeposit(-1, OreType.IRON, 48, 0),
                startingDeposit(-2, OreType.COPPER, -48, 0),
                startingDeposit(-3, OreType.COAL, 0, 48),
                startingDeposit(-4, OreType.STONE, 0, -48));
    }

    private Deposit startingDeposit(long id, OreType type, int dx, int dz) {
        return new Deposit(id, type, spawnX + dx, spawnZ + dz, STARTING_RADIUS, type.baseAmount());
    }

    public Optional<Deposit> depositAt(int x, int z) {
        for (Deposit d : starting) {
            if (d.contains(x, z)) return Optional.of(d);
        }
        int rx = Math.floorDiv(x, REGION);
        int rz = Math.floorDiv(z, REGION);
        for (int i = rx - 1; i <= rx + 1; i++) {
            for (int j = rz - 1; j <= rz + 1; j++) {
                Deposit d = generate(i, j);
                if (d != null && d.contains(x, z)) return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    /** Deposits whose center lies in the region (starting deposits included). */
    public List<Deposit> inRegion(int rx, int rz) {
        List<Deposit> out = new ArrayList<>(2);
        for (Deposit d : starting) {
            if (Math.floorDiv(d.centerX(), REGION) == rx && Math.floorDiv(d.centerZ(), REGION) == rz) {
                out.add(d);
            }
        }
        Deposit g = generate(rx, rz);
        if (g != null) out.add(g);
        return out;
    }

    /** Deposit with the closest center to (x, z) whose center is within maxDistance, if any. */
    public Optional<Deposit> nearest(int x, int z, int maxDistance) {
        long maxSq = (long) maxDistance * maxDistance;
        Deposit best = null;
        long bestSq = Long.MAX_VALUE;
        int minRx = Math.floorDiv(x - maxDistance, REGION);
        int maxRx = Math.floorDiv(x + maxDistance, REGION);
        int minRz = Math.floorDiv(z - maxDistance, REGION);
        int maxRz = Math.floorDiv(z + maxDistance, REGION);
        for (int rx = minRx; rx <= maxRx; rx++) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                Deposit g = generate(rx, rz);
                if (g == null) continue;
                long sq = distSq(g, x, z);
                if (sq <= maxSq && sq < bestSq) {
                    best = g;
                    bestSq = sq;
                }
            }
        }
        for (Deposit d : starting) {
            long sq = distSq(d, x, z);
            if (sq <= maxSq && sq < bestSq) {
                best = d;
                bestSq = sq;
            }
        }
        return Optional.ofNullable(best);
    }

    private static long distSq(Deposit d, int x, int z) {
        long dx = (long) d.centerX() - x;
        long dz = (long) d.centerZ() - z;
        return dx * dx + dz * dz;
    }

    /**
     * Regular deposit id: positive and unique per region. 31 bits of each coordinate packed as
     * ((rx & 0x7fffffff) << 31 | (rz & 0x7fffffff)) + 1; collision-free for |rx|,|rz| < 2^30.
     */
    private static long regionId(int rx, int rz) {
        return ((((long) rx & 0x7fffffffL) << 31) | ((long) rz & 0x7fffffffL)) + 1;
    }

    private Deposit generate(int rx, int rz) {
        SplittableRandom rnd = new SplittableRandom(mix(worldSeed, rx, rz));
        if (rnd.nextDouble() >= CHANCE) return null;
        int span = REGION - 2 * MAX_RADIUS;
        int cx = rx * REGION + MAX_RADIUS + rnd.nextInt(span + 1);
        int cz = rz * REGION + MAX_RADIUS + rnd.nextInt(span + 1);
        OreType type = pickType(rnd.nextInt(totalWeight()));
        int radius = MIN_RADIUS + rnd.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        double dist = Math.hypot((double) cx - spawnX, (double) cz - spawnZ);
        if (dist - radius < SPAWN_EXCLUSION) return null;
        long amount = Math.round(type.baseAmount() * (1 + dist / 500.0));
        return new Deposit(regionId(rx, rz), type, cx, cz, radius, amount);
    }

    private static int totalWeight() {
        int sum = 0;
        for (OreType t : OreType.values()) sum += t.weight();
        return sum;
    }

    private static OreType pickType(int roll) {
        for (OreType t : OreType.values()) {
            if (roll < t.weight()) return t;
            roll -= t.weight();
        }
        throw new IllegalStateException();
    }

    private static long mix(long seed, int rx, int rz) {
        long h = splitMix(seed ^ (rx * 0x9E3779B97F4A7C15L));
        return splitMix(h ^ (rz * 0xC2B2AE3D27D4EB4FL));
    }

    private static long splitMix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
