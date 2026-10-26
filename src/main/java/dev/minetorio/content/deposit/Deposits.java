package dev.minetorio.content.deposit;

import dev.minetorio.core.deposit.Deposit;
import dev.minetorio.core.deposit.DepositGenerator;
import dev.minetorio.core.deposit.DepositLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * The ore deposits of a world: a {@link DepositGenerator} (world seed, spawn point pinned in
 * {@link DepositSavedData}) plus the saved {@link DepositLedger}. Deposits exist only in the
 * Overworld; for every other dimension {@link #get} returns an instance without any.
 *
 * <p>{@link DepositGenerator#depositAt} builds RNGs for 9 regions per call: callers that need a
 * deposit every tick (drills) look it up once and cache it.
 */
public final class Deposits {
    /** Scanner search radius, in blocks. */
    public static final int SCAN_RADIUS = 256;

    /** Every dimension other than the Overworld. */
    private static final Deposits NONE = new Deposits(null, null);

    @Nullable
    private final DepositGenerator generator;
    @Nullable
    private final DepositSavedData data;
    /** GameTest overrides (see {@link #overrideForTest}); in memory only. */
    private final List<TestOverride> testOverrides = new ArrayList<>();

    private Deposits(@Nullable DepositGenerator generator, @Nullable DepositSavedData data) {
        this.generator = generator;
        this.data = data;
    }

    /** Pins the deposit layout (spawn point) as soon as the server has started, not at the first deposit lookup. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> get(e.getServer().overworld()));
    }

    public static Deposits get(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return NONE;
        DepositSavedData data = level.getDataStorage().computeIfAbsent(DepositSavedData.factory(level.getSharedSpawnPos()), DepositSavedData.NAME);
        if (data.deposits == null) {
            data.deposits = new Deposits(new DepositGenerator(level.getSeed(), data.spawnX(), data.spawnZ()), data);
        }
        return data.deposits;
    }

    /** The deposit covering column (x, z), if any. */
    public Optional<Deposit> depositAt(int x, int z) {
        for (TestOverride o : testOverrides) {
            if (o.covers(x, z)) return o.deposit != null && o.deposit.contains(x, z) ? Optional.of(o.deposit) : Optional.empty();
        }
        return generator == null ? Optional.empty() : generator.depositAt(x, z);
    }

    /**
     * The deposit under the area [minX..maxX] × [minZ..maxZ] (inclusive): the one containing the
     * area's center column if any, otherwise the first one found containing any column of the area.
     */
    public Optional<Deposit> depositUnder(int minX, int minZ, int maxX, int maxZ) {
        Optional<Deposit> center = depositAt(Math.floorDiv(minX + maxX, 2), Math.floorDiv(minZ + maxZ, 2));
        if (center.isPresent()) return center;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Optional<Deposit> d = depositAt(x, z);
                if (d.isPresent()) return d;
            }
        }
        return Optional.empty();
    }

    /** The not yet depleted deposit with the closest center within {@code maxDistance} of (x, z), if any. */
    public Optional<Deposit> nearest(int x, int z, int maxDistance) {
        return generator == null ? Optional.empty() : generator.nearest(x, z, maxDistance, d -> remaining(d) > 0);
    }

    public long remaining(Deposit d) {
        TestOverride o = overrideOf(d);
        if (o != null) return o.ledger.remaining(d);
        return data == null ? 0 : data.ledger().remaining(d);
    }

    /** Removes up to {@code amount} from {@code d}; returns the amount actually mined. */
    public long mine(Deposit d, long amount) {
        TestOverride o = overrideOf(d);
        if (o != null) return o.ledger.mine(d, amount);
        if (data == null) return 0;
        long taken = data.ledger().mine(d, amount);
        if (taken > 0) data.setDirty();
        return taken;
    }

    @Nullable
    private TestOverride overrideOf(Deposit d) {
        for (TestOverride o : testOverrides) {
            if (d.equals(o.deposit)) return o;
        }
        return null;
    }

    // --- GameTest hook -----------------------------------------------------------------------------

    /**
     * GameTests only: inside {@code area} (a test's bounds; only its X/Z extent matters) the
     * Overworld has exactly the deposit {@code d} — or none if {@code d} is null — with
     * {@code remaining} ore left, instead of the generated deposits. Kept in memory only (never
     * saved, mined amounts go to a private ledger) and limited to the area, so it cannot reach
     * the rest of a world. A new override replaces older ones whose area intersects it (tests
     * reusing a position). Throws unless the GameTest system is enabled (never in production).
     */
    public static void overrideForTest(ServerLevel level, AABB area, @Nullable Deposit d, long remaining) {
        if (!GameTestHooks.isGametestEnabled()) throw new IllegalStateException("Deposits.overrideForTest is for GameTests only");
        Deposits deposits = get(level);
        if (deposits == NONE) throw new IllegalArgumentException("deposits exist only in the Overworld");
        TestOverride o = new TestOverride((int) Math.floor(area.minX), (int) Math.floor(area.minZ),
                (int) Math.ceil(area.maxX) - 1, (int) Math.ceil(area.maxZ) - 1, d, new DepositLedger());
        if (d != null) o.ledger.load(Map.of(d.id(), Math.max(0, d.initialAmount() - remaining)));
        deposits.testOverrides.removeIf(old -> old.intersects(o));
        deposits.testOverrides.add(o);
    }

    private record TestOverride(int minX, int minZ, int maxX, int maxZ, @Nullable Deposit deposit, DepositLedger ledger) {
        boolean covers(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        boolean intersects(TestOverride o) {
            return minX <= o.maxX && o.minX <= maxX && minZ <= o.maxZ && o.minZ <= maxZ;
        }
    }
}
