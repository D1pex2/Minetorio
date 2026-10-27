package dev.minetorio.core.robot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RobotCoreTest {
    private static final Cell O = new Cell(0, 0, 0);

    // --- battery -------------------------------------------------------------------------------

    @Test
    void fullBatteryIsFactorioCapacity() {
        RobotBattery b = RobotBattery.full();
        assertEquals(1_500_000L, b.energy());
        assertTrue(b.isFull());
        assertEquals(1.0, b.speedFactor());
    }

    @Test
    void flightDrainsPerBlockPlusIdle() {
        RobotBattery b = RobotBattery.full();
        b.fly(1.0); // 5 kJ per block + 3 kW for one tick (150 J)
        assertEquals(1_500_000L - 5_000L - 150L, b.energy());
        assertFalse(b.isFull());
    }

    @Test
    void emptyBatteryFliesAtOneFifthSpeedAndNeverGoesNegative() {
        RobotBattery b = new RobotBattery(100);
        b.fly(10);
        assertEquals(0, b.energy());
        assertTrue(b.isEmpty());
        assertEquals(RobotStats.LOW_ENERGY_SPEED_FACTOR, b.speedFactor());
        assertEquals(0.2, RobotStats.LOW_ENERGY_SPEED_FACTOR);
    }

    @Test
    void chargeAcceptsOnlyWhatIsMissing() {
        RobotBattery b = new RobotBattery(RobotStats.BATTERY_J - 1000);
        assertEquals(1000, b.charge(50_000));
        assertTrue(b.isFull());
        assertEquals(0, b.charge(50_000));
    }

    @Test
    void lowBatteryWantsCharge() {
        assertTrue(new RobotBattery(RobotStats.BATTERY_J / 10).needsCharge());
        assertFalse(new RobotBattery(RobotStats.BATTERY_J / 2).needsCharge());
    }

    @Test
    void factorioSpeedScaledTo20Tps() {
        // 0.06 tiles per tick at 60 UPS = 3.6 tiles/s = 0.18 blocks per tick at 20 TPS
        assertEquals(0.18, RobotStats.SPEED_BLOCKS_PER_TICK, 1e-9);
        assertEquals(50_000L, RobotStats.CHARGE_PER_TICK_J); // 1 MW
        assertEquals(250_000L, RobotStats.ROBOPORT_INPUT_PER_TICK_J); // 5 MW
    }

    // --- flight --------------------------------------------------------------------------------

    @Test
    void stepMovesAtMostTheStepAndLandsExactlyOnTarget() {
        Flight.Vec a = new Flight.Vec(0, 0, 0);
        Flight.Vec b = new Flight.Vec(3, 4, 0);
        Flight.Vec s = Flight.step(a, b, 1);
        assertEquals(1.0, Flight.distance(a, s), 1e-9);
        assertEquals(0.6, s.x(), 1e-9);
        assertEquals(0.8, s.y(), 1e-9);
        assertEquals(b, Flight.step(new Flight.Vec(2.9, 3.9, 0), b, 1));
        assertEquals(b, Flight.step(b, b, 1));
    }

    @Test
    void hoverIsAboveTheBlockTop() {
        Flight.Vec h = Flight.hover(new Cell(2, 64, -3), 2.0);
        assertEquals(new Flight.Vec(2.5, 67.0, -2.5), h);
    }

    @Test
    void ticksToFlyRoundsUp() {
        assertEquals(6, Flight.ticks(1.0, 0.18));
        assertEquals(0, Flight.ticks(0, 0.18));
    }

    // --- area ----------------------------------------------------------------------------------

    @Test
    void areaIsASquareAroundTheCenter() {
        ConstructionArea area = new ConstructionArea(10, 64, 10, 27);
        assertTrue(area.contains(new Cell(10, 64, 10)));
        assertTrue(area.contains(new Cell(36, 64, -16)));  // center 36.5 is 26.5 off
        assertFalse(area.contains(new Cell(37, 64, 10)));  // 27.5 off
        assertTrue(area.contains(new Cell(-17, 64, 10)));  // center -16.5 is 26.5 off
        assertFalse(area.contains(new Cell(-18, 64, 10))); // 27.5 off
        assertFalse(area.contains(new Cell(10, 100, 10)));
        assertEquals(54, area.width());
    }

    // --- queue ---------------------------------------------------------------------------------

    @Test
    void pendingJobsComeInPlacementOrderAndSkipClaimed() {
        ConstructionQueue q = new ConstructionQueue();
        Cell a = new Cell(5, 0, 0), b = new Cell(1, 0, 0), c = new Cell(3, 0, 0);
        q.addBuild(a, "minetorio:transport_belt");
        q.addBuild(b, "minetorio:inserter");
        q.addBuild(c, "minetorio:transport_belt");
        assertEquals(List.of(a, b, c), q.pending(x -> true).stream().map(ConstructionQueue.Job::cell).toList());
        assertTrue(q.claim(b, 7));
        assertFalse(q.claim(b, 8), "already claimed");
        assertEquals(List.of(a, c), q.pending(x -> true).stream().map(ConstructionQueue.Job::cell).toList());
        assertFalse(q.release(b, 8), "only the claimant releases");
        assertTrue(q.release(b, 7));
        assertEquals(3, q.pending(x -> true).size());
        assertEquals(List.of(a), q.pending(x -> x.x() > 4).stream().map(ConstructionQueue.Job::cell).toList());
    }

    @Test
    void savedSequenceKeepsOrderAcrossReload() {
        ConstructionQueue q = new ConstructionQueue();
        q.addBuild(new Cell(0, 0, 0), "x", 10);
        q.addBuild(new Cell(1, 0, 0), "x", 3);
        ConstructionQueue.Job fresh = q.addBuild(new Cell(2, 0, 0), "x");
        assertTrue(fresh.seq() > 10);
        assertEquals(List.of(new Cell(1, 0, 0), new Cell(0, 0, 0), new Cell(2, 0, 0)),
                q.pending(x -> true).stream().map(ConstructionQueue.Job::cell).toList());
    }

    @Test
    void removingAJobDropsItsClaim() {
        ConstructionQueue q = new ConstructionQueue();
        q.addBuild(O, "x");
        q.claim(O, 1);
        q.remove(O);
        assertTrue(q.get(O).isEmpty());
        assertFalse(q.claim(O, 2));
        assertEquals(0, q.size());
    }

    @Test
    void readdingAGhostKeepsAnExistingClaim() {
        ConstructionQueue q = new ConstructionQueue();
        q.addBuild(O, "x", 4);
        q.claim(O, 9);
        q.addBuild(O, "x", 4); // chunk reload re-registers the ghost
        assertEquals(9, q.get(O).orElseThrow().claimant());
    }

    @Test
    void deconstructionMarks() {
        ConstructionQueue q = new ConstructionQueue();
        Cell c = new Cell(4, 1, 4);
        assertFalse(q.isMarkedForDeconstruction(c));
        q.markForDeconstruction(c);
        assertTrue(q.isMarkedForDeconstruction(c));
        ConstructionQueue.Job j = q.get(c).orElseThrow();
        assertEquals(ConstructionQueue.Kind.DECONSTRUCT, j.kind());
        assertNull(j.item());
        assertTrue(q.unmarkDeconstruction(c));
        assertFalse(q.isMarkedForDeconstruction(c));
        assertFalse(q.unmarkDeconstruction(c));
        q.addBuild(c, "x");
        assertFalse(q.unmarkDeconstruction(c), "a ghost is not a deconstruction mark");
    }

    @Test
    void pendingOfKind() {
        ConstructionQueue q = new ConstructionQueue();
        q.addBuild(O, "x");
        q.markForDeconstruction(new Cell(1, 0, 0));
        assertEquals(1, q.pending(ConstructionQueue.Kind.BUILD, x -> true).size());
        assertEquals(1, q.pending(ConstructionQueue.Kind.DECONSTRUCT, x -> true).size());
        assertEquals(2, q.pending(x -> true).size());
    }

    // --- providers and dispatch ------------------------------------------------------------------

    private static final Cell NEAR = new Cell(2, 0, 0);
    private static final Cell FAR = new Cell(20, 0, 0);

    private static Map<Cell, Map<String, Integer>> stock() {
        Map<Cell, Map<String, Integer>> s = new HashMap<>();
        s.put(NEAR, new HashMap<>(Map.of("belt", 1)));
        s.put(FAR, new HashMap<>(Map.of("belt", 5, "inserter", 1)));
        return s;
    }

    private static int count(Map<Cell, Map<String, Integer>> s, Cell c, String item) {
        return s.getOrDefault(c, Map.of()).getOrDefault(item, 0);
    }

    @Test
    void pickTakesTheNearestProviderWithUnreservedStock() {
        var s = stock();
        ProviderLedger ledger = new ProviderLedger();
        List<Cell> providers = List.of(FAR, NEAR);
        assertEquals(Optional.of(NEAR), ledger.pick(providers, O, "belt", (c, i) -> count(s, c, i)));
        ledger.reserve(NEAR, "belt");
        assertEquals(Optional.of(FAR), ledger.pick(providers, O, "belt", (c, i) -> count(s, c, i)));
        assertEquals(Optional.empty(), ledger.pick(providers, O, "furnace", (c, i) -> count(s, c, i)));
        ledger.release(NEAR, "belt");
        assertEquals(0, ledger.reserved(NEAR, "belt"));
        ledger.release(NEAR, "belt"); // never below zero
        assertEquals(0, ledger.reserved(NEAR, "belt"));
    }

    @Test
    void dispatchFollowsQueueOrderRobotCountAndStock() {
        var s = stock();
        ConstructionQueue q = new ConstructionQueue();
        Cell g1 = new Cell(0, 0, 1), g2 = new Cell(0, 0, 2), g3 = new Cell(0, 0, 3), g4 = new Cell(0, 0, 4);
        q.addBuild(g1, "furnace");  // nothing provides it: stays pending, skipped
        q.addBuild(g2, "inserter");
        q.addBuild(g3, "inserter"); // only one inserter in stock
        q.addBuild(g4, "belt");
        ProviderLedger ledger = new ProviderLedger();
        List<Dispatcher.Assignment> plan = Dispatcher.plan(q.pending(x -> true), 5, List.of(NEAR, FAR),
                (c, i) -> count(s, c, i), ledger);
        assertEquals(List.of(g2, g4), plan.stream().map(a -> a.job().cell()).toList());
        assertEquals(FAR, plan.get(0).provider());
        assertEquals(NEAR, plan.get(1).provider());
        assertEquals(1, ledger.reserved(FAR, "inserter"));

        ProviderLedger fresh = new ProviderLedger();
        assertEquals(1, Dispatcher.plan(q.pending(x -> true), 1, List.of(NEAR, FAR), (c, i) -> count(s, c, i), fresh).size());
        assertEquals(0, Dispatcher.plan(q.pending(x -> true), 0, List.of(NEAR, FAR), (c, i) -> count(s, c, i), fresh).size());
    }

    @Test
    void dispatchIgnoresDeconstructionJobs() {
        ConstructionQueue q = new ConstructionQueue();
        q.markForDeconstruction(O);
        assertTrue(Dispatcher.plan(q.pending(x -> true), 5, List.of(NEAR), (c, i) -> 99, new ProviderLedger()).isEmpty());
    }
}
