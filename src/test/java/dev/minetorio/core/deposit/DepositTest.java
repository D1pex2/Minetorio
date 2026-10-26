package dev.minetorio.core.deposit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DepositTest {
    private static final int SX = 100, SZ = -50;
    private final DepositGenerator gen = new DepositGenerator(12345L, SX, SZ);

    @Test void deterministic() {
        DepositGenerator a = new DepositGenerator(7L, SX, SZ);
        DepositGenerator b = new DepositGenerator(7L, SX, SZ);
        for (int i = 0; i < 100; i++) {
            int rx = i % 10 - 5, rz = i / 10 - 5;
            assertEquals(a.inRegion(rx, rz), b.inRegion(rx, rz));
        }
    }

    @Test void startingDepositsExist() {
        assertEquals(OreType.IRON, gen.depositAt(SX + 48, SZ).orElseThrow().type());
        assertEquals(OreType.COPPER, gen.depositAt(SX - 48, SZ).orElseThrow().type());
        assertEquals(OreType.COAL, gen.depositAt(SX, SZ + 48).orElseThrow().type());
        assertEquals(OreType.STONE, gen.depositAt(SX, SZ - 48).orElseThrow().type());
        Deposit iron = gen.depositAt(SX + 48, SZ).orElseThrow();
        assertEquals(-1, iron.id());
        assertEquals(20_000, iron.initialAmount());
        assertEquals(10, iron.radius());
    }

    @Test void spawnAreaHasOnlyStartingDeposits() {
        for (int x = SX - 80; x <= SX + 80; x += 3) {
            for (int z = SZ - 80; z <= SZ + 80; z += 3) {
                Optional<Deposit> d = gen.depositAt(x, z);
                if (d.isPresent()) assertTrue(d.get().id() < 0, "regular deposit near spawn at " + x + "," + z);
            }
        }
    }

    private double distToSpawn(Deposit d) {
        return Math.hypot(d.centerX() - SX, d.centerZ() - SZ);
    }

    private Deposit regularBetween(double min, double max) {
        for (int rx = -60; rx < 60; rx++)
            for (int rz = -60; rz < 60; rz++)
                for (Deposit d : gen.inRegion(rx, rz))
                    if (d.id() > 0 && distToSpawn(d) >= min && distToSpawn(d) <= max) return d;
        throw new AssertionError("none found");
    }

    @Test void richerFarAway() {
        Deposit near = regularBetween(150, 300);
        Deposit far = regularBetween(1800, 2500);
        assertEquals(Math.round(near.type().baseAmount() * (1 + distToSpawn(near) / 500.0)), near.initialAmount());
        assertEquals(Math.round(far.type().baseAmount() * (1 + distToSpawn(far) / 500.0)), far.initialAmount());
        assertTrue((double) far.initialAmount() / far.type().baseAmount()
                > (double) near.initialAmount() / near.type().baseAmount());
    }

    @Test void regularDepositsStayInsideTheirRegion() {
        int n = 0;
        for (int rx = -40; rx < 40; rx++) {
            for (int rz = -40; rz < 40; rz++) {
                for (Deposit d : gen.inRegion(rx, rz)) {
                    if (d.id() < 0) continue;
                    n++;
                    assertTrue(d.centerX() - d.radius() >= rx * 64 && d.centerX() + d.radius() <= rx * 64 + 63, "x " + d);
                    assertTrue(d.centerZ() - d.radius() >= rz * 64 && d.centerZ() + d.radius() <= rz * 64 + 63, "z " + d);
                }
            }
        }
        assertTrue(n > 1000);
    }


    @Test void regularIdsUniqueAndPositive() {
        List<Long> ids = new ArrayList<>();
        for (int rx = -20; rx < 20; rx++)
            for (int rz = -20; rz < 20; rz++)
                for (Deposit d : gen.inRegion(rx, rz)) ids.add(d.id());
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    @Test void densityReasonable() {
        int with = 0;
        for (int rx = 100; rx < 120; rx++)
            for (int rz = 100; rz < 120; rz++)
                if (!gen.inRegion(rx, rz).isEmpty()) with++;
        assertTrue(with >= 100 && with <= 180, "regions with deposit: " + with);
    }

    @Test void depositAtMatchesInRegion() {
        for (Deposit d : gen.inRegion(110, 105)) {
            assertEquals(d, gen.depositAt(d.centerX(), d.centerZ()).orElseThrow());
        }
    }

    @Test void ledgerNeverNegative() {
        Deposit d = new Deposit(5, OreType.IRON, 0, 0, 5, 3);
        DepositLedger l = new DepositLedger();
        assertEquals(3, l.mine(d, 5));
        assertEquals(0, l.mine(d, 1));
        assertEquals(0, l.remaining(d));
        assertEquals(0, l.mine(d, -4));
    }

    @Test void ledgerSaveLoad() {
        Deposit d = new Deposit(5, OreType.IRON, 0, 0, 5, 100);
        DepositLedger l = new DepositLedger();
        l.mine(d, 30);
        DepositLedger l2 = new DepositLedger();
        l2.load(l.minedById());
        assertEquals(70, l2.remaining(d));
    }

    @Test void nearestFindsStartingIron() {
        Deposit d = gen.nearest(SX + 30, SZ, 256).orElseThrow();
        assertEquals(OreType.IRON, d.type());
        assertTrue(gen.nearest(SX + 30, SZ, 5).isEmpty());
    }

    @Test void nearestSkipsRejected() {
        Deposit d = gen.nearest(SX + 30, SZ, 256, x -> x.type() != OreType.IRON).orElseThrow();
        assertNotEquals(OreType.IRON, d.type());
        assertTrue(gen.nearest(SX + 30, SZ, 256, x -> false).isEmpty());
    }
}
