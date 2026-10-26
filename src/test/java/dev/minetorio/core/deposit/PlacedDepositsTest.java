package dev.minetorio.core.deposit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlacedDepositsTest {
    @Test void firstIdIsMinus1000AndIdsGoDown() {
        PlacedDeposits p = new PlacedDeposits();
        Deposit a = p.place(OreType.IRON, 0, 0, 5, 100).added();
        Deposit b = p.place(OreType.COAL, 100, 0, 5, 100).added();
        assertEquals(PlacedDeposits.FIRST_ID, a.id());
        assertEquals(-1000, a.id());
        assertEquals(-1001, b.id());
        assertEquals(List.of(a, b), p.all());
    }

    @Test void foundInsideItsCircleOnly() {
        PlacedDeposits p = new PlacedDeposits();
        Deposit d = p.place(OreType.IRON, 10, 20, 4, 1000).added();
        assertEquals(d, p.at(14, 20).orElseThrow());
        assertEquals(d, p.at(10, 16).orElseThrow());
        assertTrue(p.at(15, 20).isEmpty());
        assertEquals(1000, d.initialAmount());
        assertEquals(OreType.IRON, d.type());
    }

    @Test void placingOverAnotherReplacesIt() {
        PlacedDeposits p = new PlacedDeposits();
        Deposit old = p.place(OreType.IRON, 0, 0, 5, 100).added();
        Deposit far = p.place(OreType.COPPER, 50, 0, 5, 100).added();
        PlacedDeposits.Placement again = p.place(OreType.IRON, 3, 0, 5, 200);
        assertEquals(List.of(old), again.removed());
        assertEquals(List.of(far, again.added()), p.all());
        assertNotEquals(far.id(), again.added().id());
    }

    @Test void nearestByCenterWithinDistance() {
        PlacedDeposits p = new PlacedDeposits();
        Deposit a = p.place(OreType.IRON, 0, 0, 5, 100).added();
        Deposit b = p.place(OreType.COAL, 30, 0, 5, 100).added();
        assertEquals(b, p.nearest(20, 0, 50, d -> true).orElseThrow());
        assertEquals(a, p.nearest(20, 0, 50, d -> d.type() == OreType.IRON).orElseThrow());
        assertTrue(p.nearest(100, 0, 50, d -> true).isEmpty());
    }

    @Test void removeAndLoad() {
        PlacedDeposits p = new PlacedDeposits();
        Deposit a = p.place(OreType.IRON, 0, 0, 5, 100).added();
        assertTrue(p.remove(a));
        assertFalse(p.remove(a));
        assertTrue(p.all().isEmpty());
        PlacedDeposits q = new PlacedDeposits();
        Deposit saved = new Deposit(-1005, OreType.STONE, 1, 2, 3, 4);
        q.load(List.of(saved));
        assertEquals(List.of(saved), q.all());
        assertEquals(-1006, q.place(OreType.IRON, 500, 500, 2, 1).added().id());
    }

    @Test void loadSkipsIdsOutsideTheRange() {
        PlacedDeposits q = new PlacedDeposits();
        q.load(List.of(new Deposit(-1, OreType.IRON, 0, 0, 3, 4), new Deposit(-2_000_000, OreType.IRON, 0, 0, 3, 4),
                new Deposit(7, OreType.IRON, 0, 0, 3, 4)));
        assertTrue(q.all().isEmpty());
    }

    @Test void ledgerForgetsADeposit() {
        Deposit d = new Deposit(-1000, OreType.IRON, 0, 0, 5, 100);
        DepositLedger l = new DepositLedger();
        l.mine(d, 30);
        l.forget(d.id());
        assertEquals(100, l.remaining(d));
        assertTrue(l.minedById().isEmpty());
    }
}
