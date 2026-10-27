package dev.minetorio.core.map;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RadarTest {
    @Test
    void spiralGoesRingByRingAndCoversEachCellOnce() {
        Set<Long> seen = new HashSet<>();
        int r = 7;
        int n = RadarFrontier.cellCount(r);
        assertEquals(15 * 15, n);
        int lastRing = 0;
        for (int i = 0; i < n; i++) {
            int[] o = RadarFrontier.offset(i);
            int ring = Math.max(Math.abs(o[0]), Math.abs(o[1]));
            assertTrue(ring >= lastRing, "ring went back at " + i);
            assertTrue(ring <= r);
            lastRing = ring;
            assertTrue(seen.add(ChunkKeys.of(o[0], o[1])), "cell twice: " + o[0] + "," + o[1]);
        }
        assertArrayEquals(new int[] {0, 0}, RadarFrontier.offset(0));
        assertEquals(1, Math.max(Math.abs(RadarFrontier.offset(1)[0]), Math.abs(RadarFrontier.offset(1)[1])));
        assertEquals(2, Math.max(Math.abs(RadarFrontier.offset(9)[0]), Math.abs(RadarFrontier.offset(9)[1])));
    }

    @Test
    void neighboursInTheSpiralAreAdjacentWithinARing() {
        for (int i = 2; i < RadarFrontier.cellCount(10) - 1; i++) {
            int[] a = RadarFrontier.offset(i), b = RadarFrontier.offset(i + 1);
            int ra = Math.max(Math.abs(a[0]), Math.abs(a[1])), rb = Math.max(Math.abs(b[0]), Math.abs(b[1]));
            if (ra == rb) assertEquals(1, Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]), "jump at " + i);
        }
    }

    @Test
    void frontierSkipsExploredAndStopsAtItsRadius() {
        RadarFrontier f = new RadarFrontier(2);
        Set<Long> explored = new HashSet<>();
        explored.add(ChunkKeys.of(10, 10));          // the radar's own chunk
        explored.add(ChunkKeys.of(10 + RadarFrontier.offset(1)[0], 10 + RadarFrontier.offset(1)[1]));
        List<Long> got = new ArrayList<>();
        long k;
        while ((k = f.next(explored::contains, 10, 10, 1000)) != RadarFrontier.NONE) {
            got.add(k);
            explored.add(k);
        }
        assertEquals(25 - 2, got.size());
        assertEquals(ChunkKeys.of(10 + RadarFrontier.offset(2)[0], 10 + RadarFrontier.offset(2)[1]), got.get(0));
        assertTrue(f.isExhausted());
        assertEquals(RadarFrontier.NONE, f.next(explored::contains, 10, 10, 1000));
    }

    @Test
    void frontierSkipBudgetBoundsWorkPerCall() {
        RadarFrontier f = new RadarFrontier(5);
        long k = f.next(x -> true, 0, 0, 10);
        assertEquals(RadarFrontier.BUSY, k);
        assertEquals(10, f.index());
        assertFalse(f.isExhausted());
    }

    @Test
    void frontierIndexPersists() {
        RadarFrontier f = new RadarFrontier(50);
        f.setIndex(30);
        assertEquals(30, f.index());
        f.setIndex(-4);
        assertEquals(0, f.index());
        f.setIndex(Integer.MAX_VALUE);
        assertTrue(f.isExhausted());
    }

    @Test
    void defaultFrontierReachesFiftyChunks() {
        assertEquals(50, RadarLogic.FAR_RADIUS);
        assertEquals(101 * 101, RadarFrontier.cellCount(RadarLogic.FAR_RADIUS));
    }

    @Test
    void revealTakesThirtySecondsAtFullPower() {
        RadarLogic l = new RadarLogic();
        int t = 0;
        while (!l.revealDue()) {
            l.tick(1.0);
            t++;
            assertTrue(t < 10_000);
        }
        assertEquals(600, t);
        l.revealed();
        assertFalse(l.revealDue());
    }

    @Test
    void halfPowerRevealsHalfAsFastAndNoPowerNever() {
        RadarLogic half = new RadarLogic();
        for (int i = 0; i < 1199; i++) half.tick(0.5);
        assertFalse(half.revealDue());
        half.tick(0.5);
        assertTrue(half.revealDue());
        RadarLogic none = new RadarLogic();
        for (int i = 0; i < 5000; i++) assertFalse(none.tick(0));
        assertFalse(none.revealDue());
        assertEquals(0, none.progress());
    }

    @Test
    void nearScanEveryFiveSecondsWhilePowered() {
        RadarLogic l = new RadarLogic();
        List<Integer> scans = new ArrayList<>();
        for (int i = 0; i < 301; i++) if (l.tick(i < 250 ? 0.3 : 0)) scans.add(i);
        assertEquals(List.of(0, 100, 200), scans);
    }

    @Test
    void progressSaturatesUntilConsumed() {
        RadarLogic l = new RadarLogic();
        for (int i = 0; i < 5000; i++) l.tick(1);
        assertEquals(1.0, l.progress(), 1e-9);
        l.load(0.25);
        assertEquals(0.25, l.progress(), 1e-9);
    }

    @Test
    void powerIs300kW() {
        assertEquals(15_000L, RadarLogic.POWER);
    }
}
