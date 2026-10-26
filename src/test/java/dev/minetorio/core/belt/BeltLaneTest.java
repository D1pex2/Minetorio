package dev.minetorio.core.belt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BeltLaneTest {
    private static final double V = BeltTier.YELLOW.speed();

    @Test void yellowItemCrossesOneBlockIn11Ticks() {
        BeltLane lane = new BeltLane(1.0);
        assertTrue(lane.insertAt("a", 0));
        List<String> got = new ArrayList<>();
        LaneSink sink = (i, o) -> got.add(i);
        for (int t = 0; t < 10; t++) lane.tick(V, sink);
        assertTrue(got.isEmpty());
        assertEquals(0.9375, lane.items().get(0).pos(), 1e-9);
        lane.tick(V, sink);
        assertEquals(1, got.size());
        assertTrue(lane.isEmpty());
    }

    @Test void spacingRespected() {
        BeltLane lane = new BeltLane(1.0);
        assertTrue(lane.insertAt("a", 0.0));
        assertFalse(lane.insertAt("b", 0.1));
        assertTrue(lane.insertAt("b", 0.25));
        assertEquals("b", lane.items().get(0).item());
    }

    @Test void blockedLaneCompresses() {
        BeltLane lane = new BeltLane(1.0);
        int inserted = 0;
        for (int t = 0; t < 120; t++) {
            if (t % 3 == 0 && lane.insertAt("x", 0)) inserted++;
            lane.tick(V, (i, o) -> false);
            List<LaneItem> its = lane.items();
            for (int k = 1; k < its.size(); k++) {
                assertTrue(its.get(k - 1).pos() - its.get(k).pos() >= BeltLane.SPACING - 1e-9);
            }
        }
        assertEquals(4, lane.items().size());
        assertEquals(inserted, lane.items().size());
        assertEquals(1.0, lane.items().get(0).pos(), 1e-9);
    }

    @Test void nullSinkRejects() {
        BeltLane lane = new BeltLane(1.0);
        lane.insertAt("a", 0.95);
        lane.tick(V, null);
        assertEquals(1.0, lane.items().get(0).pos(), 1e-9);
    }

    @Test void throughputYellowLane() {
        BeltLane lane = new BeltLane(1.0);
        int[] count = {0};
        // start in steady state: a saturated lane, so the pipeline latency does not skew the count
        for (double pos : new double[] {0.9375, 0.6875, 0.4375, 0.1875}) lane.insertAt("x", pos);
        double credit = 0.1875;
        LaneSink sink = (i, o) -> { count[0]++; return true; };
        for (int t = 0; t < 200; t++) {
            // saturated upstream moving at belt speed: an item is due every SPACING of travel,
            // entering with its sub-tick overflow position
            credit += V;
            if (credit >= BeltLane.SPACING && lane.insertAt("x", credit - BeltLane.SPACING)) credit -= BeltLane.SPACING;
            lane.tick(V, sink);
        }
        assertTrue(Math.abs(count[0] - 75) <= 1, "accepted " + count[0]);
    }

    @Test void chainedLanesPassItems() {
        BeltLane a = new BeltLane(1.0);
        BeltLane b = new BeltLane(1.0);
        a.insertAt("x", 0);
        for (int t = 0; t < 11; t++) a.tick(V, b.asSink());
        assertTrue(a.isEmpty());
        assertEquals(1, b.items().size());
        assertEquals(1.03125 - 1.0, b.items().get(0).pos(), 1e-9);
    }

    @Test void takeFirstMatchingRemoves() {
        BeltLane lane = new BeltLane(1.0);
        lane.insertAt("a", 0.0);
        lane.insertAt("b", 0.5);
        assertEquals("a", lane.takeFirstMatching(s -> s.equals("a")).orElseThrow().item());
        assertTrue(lane.takeFirstMatching(s -> s.equals("a")).isEmpty());
        assertEquals(1, lane.items().size());
    }

    @Test void innerCurveCapacityIsTwo() {
        BeltLane lane = new BeltLane(0.5);
        assertTrue(lane.insertAt("a", 0.0));
        assertTrue(lane.insertAt("b", 0.25));
        assertFalse(lane.insertAt("c", 0.5));
    }

    @Test void blockedChainHoldsFourPerLaneWithSpacing() {
        int n = 4;
        BeltLane[] lanes = new BeltLane[n];
        for (int i = 0; i < n; i++) lanes[i] = new BeltLane(1.0);
        for (int t = 0; t < 400; t++) {
            for (int i = 0; i < n; i++) {
                LaneSink sink = i == n - 1 ? (x, o) -> false : lanes[i + 1].asSink();
                lanes[i].tick(V, sink);
            }
            lanes[0].insertAt("x", 0);
        }
        int total = 0;
        List<Double> world = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            total += lanes[i].items().size();
            for (LaneItem it : lanes[i].items()) world.add(i + it.pos());
        }
        assertEquals(4 * n, total);
        world.sort(null);
        for (int k = 1; k < world.size(); k++) {
            assertTrue(world.get(k) - world.get(k - 1) >= BeltLane.SPACING - 1e-9, "gap at " + k);
        }
    }

    @Test void chainedThroughputYellow() {
        BeltLane a = new BeltLane(1.0);
        BeltLane b = new BeltLane(1.0);
        int[] count = {0};
        LaneSink end = (i, o) -> { count[0]++; return true; };
        for (double pos : new double[] {0.9375, 0.6875, 0.4375, 0.1875}) a.insertAt("x", pos);
        for (double pos : new double[] {0.9375, 0.6875, 0.4375, 0.1875}) b.insertAt("x", pos);
        double credit = 0.1875;
        for (int t = 0; t < 200; t++) {
            b.tick(V, end);
            a.tick(V, b.asSink());
            credit += V;
            if (credit >= BeltLane.SPACING && a.insertAt("x", credit - BeltLane.SPACING)) credit -= BeltLane.SPACING;
        }
        assertTrue(Math.abs(count[0] - 75) <= 1, "accepted " + count[0]);
    }
}
