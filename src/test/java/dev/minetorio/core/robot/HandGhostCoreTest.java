package dev.minetorio.core.robot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class HandGhostCoreTest {
    private static final Cell A = new Cell(0, 0, 0), B = new Cell(5, 0, 0);

    @Test
    void reservedCellsBelongToTheirOwner() {
        Reservations r = new Reservations();
        r.reserve(A, List.of(A, new Cell(1, 0, 0), new Cell(0, 1, 0)));
        assertEquals(A, r.owner(new Cell(1, 0, 0)));
        assertTrue(r.freeFor(new Cell(1, 0, 0), A));
        assertFalse(r.freeFor(new Cell(1, 0, 0), B));
        assertFalse(r.freeFor(new Cell(0, 1, 0), null));
        assertTrue(r.freeFor(new Cell(2, 0, 0), B));
        assertNull(r.owner(new Cell(2, 0, 0)));
    }

    @Test
    void releaseFreesOnlyTheOwnersCells() {
        Reservations r = new Reservations();
        r.reserve(A, List.of(A, new Cell(1, 0, 0)));
        r.reserve(B, List.of(B, new Cell(6, 0, 0)));
        r.release(A);
        assertNull(r.owner(new Cell(1, 0, 0)));
        assertEquals(B, r.owner(new Cell(6, 0, 0)));
        r.release(B);
        assertTrue(r.isEmpty());
        r.release(B); // twice is fine
    }

    @Test
    void reserveAgainReplacesTheOldFootprint() {
        Reservations r = new Reservations();
        r.reserve(A, List.of(A, new Cell(1, 0, 0)));
        r.reserve(A, List.of(A, new Cell(0, 0, 1)));
        assertNull(r.owner(new Cell(1, 0, 0)));
        assertEquals(A, r.owner(new Cell(0, 0, 1)));
        assertEquals(2, r.cells(A).size());
    }

    @Test
    void takingOverACellKeepsTheOtherOwnersRest() {
        Reservations r = new Reservations();
        Cell shared = new Cell(1, 0, 0);
        r.reserve(A, List.of(A, shared));
        r.reserve(B, List.of(B, shared));
        assertEquals(B, r.owner(shared));
        assertEquals(List.of(A), r.cells(A));
        r.release(A); // must not free B's cell
        assertEquals(B, r.owner(shared));
    }

    @Test
    void budgetAllowsABurstThenRefills() {
        RateBudget b = new RateBudget(3, 0.5);
        assertTrue(b.take(100, 1));
        assertTrue(b.take(100, 1));
        assertTrue(b.take(100, 1));
        assertFalse(b.take(100, 1));
        assertFalse(b.take(101, 1)); // half a token
        assertTrue(b.take(102, 1));  // one more after two ticks
        assertTrue(b.take(1000, 3)); // refilled to the burst, never above
        assertFalse(b.take(1000, 1));
    }

    @Test
    void budgetRefusesMoreThanTheBurstAtOnce() {
        RateBudget b = new RateBudget(4, 1);
        assertFalse(b.take(0, 5));
        assertEquals(4, b.tokens());
    }

    @Test
    void budgetRejectsBadParameters() {
        assertThrows(IllegalArgumentException.class, () -> new RateBudget(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RateBudget(1, -1));
    }
}
