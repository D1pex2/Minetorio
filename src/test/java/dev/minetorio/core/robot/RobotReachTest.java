package dev.minetorio.core.robot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Robots only take and keep jobs where they tick; claims of robots that cannot go on are released. */
class RobotReachTest {
    /** Ticking chunks: chunk x 0 only (cells x 0..15). */
    private static final Predicate<Cell> TICKING = c -> (c.x() >> 4) == 0;

    @Test
    void onlyJobsInTickingChunksAreDispatched() {
        ConstructionQueue q = new ConstructionQueue();
        Cell near = new Cell(3, 0, 0), far = new Cell(20, 0, 0), edge = new Cell(15, 0, 0);
        q.addBuild(far, "belt");
        q.addBuild(near, "belt");
        q.markForDeconstruction(edge);
        q.markForDeconstruction(new Cell(-1, 0, 0));
        List<ConstructionQueue.Job> jobs = RobotReach.dispatchable(q.pending(c -> true), TICKING);
        assertEquals(List.of(near, edge), jobs.stream().map(ConstructionQueue.Job::cell).toList());

        // providers in non-ticking chunks are not used: the ghost waits until stock is reachable
        Cell chestOut = new Cell(40, 0, 0), chestIn = new Cell(8, 0, 0);
        List<Cell> providers = RobotReach.reachable(List.of(chestOut), TICKING);
        assertTrue(Dispatcher.plan(RobotReach.dispatchable(q.pending(c -> true), TICKING), 5, providers,
                (c, i) -> 10, new ProviderLedger()).isEmpty());
        providers = RobotReach.reachable(List.of(chestOut, chestIn), TICKING);
        List<Dispatcher.Assignment> plan = Dispatcher.plan(RobotReach.dispatchable(q.pending(c -> true), TICKING), 5, providers,
                (c, i) -> 10, new ProviderLedger());
        assertEquals(1, plan.size());
        assertEquals(near, plan.get(0).job().cell());
        assertEquals(chestIn, plan.get(0).provider());
    }

    @Test
    void aJobIsKeptOnlyWhileEverythingIsReachable() {
        assertTrue(RobotReach.keepJob(true, true, true));
        assertFalse(RobotReach.keepJob(false, true, true), "target chunk stopped ticking");
        assertFalse(RobotReach.keepJob(true, false, true), "next step enters a non-ticking chunk");
        assertFalse(RobotReach.keepJob(true, true, false), "outside the home area");
    }

    @Test
    void insideHorizontallyAllowsASmallMargin() {
        ConstructionArea a = new ConstructionArea(0, 64, 0, 27);
        assertTrue(RobotReach.insideHorizontally(a, 26.9, -26.9));
        assertTrue(RobotReach.insideHorizontally(a, 28.5, 0)); // within the margin
        assertFalse(RobotReach.insideHorizontally(a, 29.5, 0));
        assertFalse(RobotReach.insideHorizontally(a, 0, -40));
    }

    @Test
    void releasingAStuckRobotsClaimsLetsAnotherRobotTakeTheJob() {
        ConstructionQueue q = new ConstructionQueue();
        Cell a = new Cell(1, 0, 0), b = new Cell(2, 0, 0), c = new Cell(3, 0, 0);
        q.addBuild(a, "belt");
        q.addBuild(b, "belt");
        q.addBuild(c, "belt");
        assertTrue(q.claim(a, 7));
        assertTrue(q.claim(b, 7));
        assertTrue(q.claim(c, 8));
        assertFalse(q.claim(a, 9), "claimed by the stuck robot");
        assertEquals(List.of(), q.pending(x -> true));

        assertEquals(2, q.releaseAll(7));
        assertEquals(List.of(a, b), q.pending(x -> true).stream().map(ConstructionQueue.Job::cell).toList());
        assertTrue(q.get(c).orElseThrow().isClaimed(), "other robots keep their claims");
        assertTrue(q.claim(a, 9), "another robot takes the released job");
        assertFalse(q.release(a, 7), "the stuck robot no longer holds it");
        assertEquals(0, q.releaseAll(7));
        assertEquals(0, q.releaseAll(0), "id 0 (free) is never a claimant");
    }

    @Test
    void releasingAReservationMakesTheProviderStockAvailableAgain() {
        ProviderLedger ledger = new ProviderLedger();
        Cell chest = new Cell(8, 0, 0);
        ledger.reserve(chest, "belt");
        assertTrue(ledger.pick(List.of(chest), new Cell(0, 0, 0), "belt", (p, i) -> 1).isEmpty());
        ledger.release(chest, "belt");
        assertEquals(chest, ledger.pick(List.of(chest), new Cell(0, 0, 0), "belt", (p, i) -> 1).orElseThrow());
    }
}
