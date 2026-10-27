package dev.minetorio.core.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HoverQueryTest {
    // --- server: per-player budget ------------------------------------------------------------------

    @Test void budgetAllowsABurstThenRefillsSlowly() {
        RequestBudget b = new RequestBudget(3, 2);
        assertTrue(b.tryTake(100));
        assertTrue(b.tryTake(100));
        assertTrue(b.tryTake(100));
        assertFalse(b.tryTake(100), "burst exceeded");
        assertFalse(b.tryTake(101), "one tick refills nothing");
        assertTrue(b.tryTake(102), "two ticks refill one");
        assertFalse(b.tryTake(102));
    }

    @Test void budgetNeverExceedsItsCapacity() {
        RequestBudget b = new RequestBudget(3, 2);
        b.tryTake(0);
        int taken = 0;
        while (b.tryTake(10_000)) taken++;
        assertEquals(3, taken);
    }

    @Test void budgetIgnoresTimeGoingBackwards() {
        RequestBudget b = new RequestBudget(1, 2);
        assertTrue(b.tryTake(50));
        assertFalse(b.tryTake(10));
        assertFalse(b.tryTake(51));
        assertTrue(b.tryTake(52));
    }

    // --- client: when to ask --------------------------------------------------------------------------

    @Test void asksForANewColumnAfterTheMinimumInterval() {
        HoverThrottle t = new HoverThrottle(100, 1000);
        assertTrue(t.shouldAsk(5, 7, 0));
        assertFalse(t.shouldAsk(5, 7, 50), "same column, too soon");
        assertFalse(t.shouldAsk(6, 7, 50), "new column, too soon");
        assertTrue(t.shouldAsk(6, 7, 100));
        assertFalse(t.shouldAsk(6, 7, 500), "same column before the refresh");
        assertTrue(t.shouldAsk(6, 7, 1100), "same column refreshed");
    }

    @Test void resetAsksAgainAtOnce() {
        HoverThrottle t = new HoverThrottle(100, 1000);
        assertTrue(t.shouldAsk(1, 1, 0));
        t.reset();
        assertTrue(t.shouldAsk(1, 1, 10));
    }
}
