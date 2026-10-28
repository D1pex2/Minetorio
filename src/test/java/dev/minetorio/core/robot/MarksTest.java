package dev.minetorio.core.robot;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.data.UpgradeGroups;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarksTest {
    private static final Cell A = new Cell(1, 64, 1), B = new Cell(17, 64, -1), C = new Cell(3, 70, 15);

    @Test
    void markUnmarkAndQuery() {
        DeconstructionMarks m = new DeconstructionMarks();
        assertTrue(m.mark(A, "minetorio:transport_belt"));
        assertFalse(m.mark(A, "minetorio:transport_belt"), "same mark again changes nothing");
        assertTrue(m.mark(B));
        assertTrue(m.isMarked(A) && m.isMarked(B) && !m.isMarked(C));
        assertEquals("minetorio:transport_belt", m.block(A));
        assertEquals("", m.block(B));
        assertEquals(List.of(A, B), m.cells(), "marking order");
        assertTrue(m.unmark(A));
        assertFalse(m.unmark(A));
        assertFalse(m.isMarked(A));
        assertEquals(1, m.size());
    }

    @Test
    void validityFollowsTheMarkedBlock() {
        DeconstructionMarks m = new DeconstructionMarks();
        m.mark(A, "minetorio:inserter");
        m.mark(B);
        assertTrue(m.stillValid(A, "minetorio:inserter"));
        assertFalse(m.stillValid(A, "minecraft:stone"), "block changed");
        assertTrue(m.stillValid(B, "minecraft:oak_log"), "an id-less mark fits anything");
        assertFalse(m.stillValid(C, "minecraft:oak_log"), "no mark");
    }

    @Test
    void chunkIndex() {
        DeconstructionMarks m = new DeconstructionMarks();
        m.mark(A);
        m.mark(B);
        m.mark(C);
        assertEquals(List.of(A, C), m.inChunk(0, 0));
        assertEquals(List.of(B), m.inChunk(1, -1));
        assertEquals(3 - 1, m.chunks().size());
        m.unmark(B);
        assertTrue(m.inChunk(1, -1).isEmpty());
        assertEquals(1, m.chunks().size());
        assertEquals(CellMarks.chunkKey(0, 0), CellMarks.chunkKey(A));
        assertNotEquals(CellMarks.chunkKey(-1, 0), CellMarks.chunkKey(0, -1));
    }

    @Test
    void listenersHearChanges() {
        DeconstructionMarks m = new DeconstructionMarks();
        List<String> heard = new ArrayList<>();
        CellMarks.Listener<String> l = (cell, v) -> heard.add(cell.x() + ":" + v);
        m.addListener(l);
        m.mark(A, "x");
        m.mark(A, "x"); // no change, no event
        m.unmark(A);
        m.unmark(A);
        m.removeListener(l);
        m.mark(B);
        assertEquals(List.of("1:x", "1:null"), heard);
    }

    @Test
    void upgradeOrders() {
        UpgradeMarks u = new UpgradeMarks();
        assertTrue(u.mark(A, "minetorio:transport_belt", "minetorio:fast_transport_belt", 1));
        assertFalse(u.mark(A, "minetorio:transport_belt", "minetorio:fast_transport_belt", 1));
        assertTrue(u.mark(A, "minetorio:transport_belt", "minetorio:express_transport_belt", 1), "new target replaces");
        assertEquals("minetorio:express_transport_belt", u.get(A).toItem());
        assertTrue(u.stillValid(A, "minetorio:transport_belt"));
        assertFalse(u.stillValid(A, "minetorio:express_transport_belt"), "already upgraded");
        assertThrows(IllegalArgumentException.class, () -> u.mark(B, "", "", 1));
        assertThrows(IllegalArgumentException.class, () -> u.mark(B, "", "minetorio:inserter", 0));
    }

    @Test
    void queueMirrorsKindsAndDeconstructionWins() {
        ConstructionQueue q = new ConstructionQueue();
        q.markForUpgrade(A, "minetorio:fast_transport_belt", 1);
        assertTrue(q.isMarkedForUpgrade(A));
        q.markForDeconstruction(A);
        assertTrue(q.isMarkedForDeconstruction(A) && !q.isMarkedForUpgrade(A), "deconstruction replaces an upgrade");
        q.markForUpgrade(A, "minetorio:fast_transport_belt", 1);
        assertTrue(q.isMarkedForDeconstruction(A), "an upgrade does not replace deconstruction");
        q.markForUpgrade(B, "minetorio:fast_underground_belt", 2);
        assertEquals(2, q.get(B).orElseThrow().count());
        assertEquals(1, q.pending(ConstructionQueue.Kind.UPGRADE, c -> true).size());
        assertTrue(q.unmarkUpgrade(B));
        assertFalse(q.unmarkUpgrade(B));
    }

    @Test
    void dispatcherHandsOutAllKinds() {
        Cell chest = new Cell(0, 64, 0);
        ConstructionQueue q = new ConstructionQueue();
        q.markForDeconstruction(A);
        q.markForUpgrade(B, "minetorio:fast_underground_belt", 2);
        q.markForUpgrade(C, "minetorio:fast_inserter", 1);
        ProviderLedger ledger = new ProviderLedger();
        // one fast underground belt only: the pair upgrade (needs 2) waits, deconstruction needs storage
        List<Dispatcher.Assignment> none = Dispatcher.plan(q.pending(c -> true), 5, List.of(chest),
                (p, item) -> item.equals("minetorio:fast_underground_belt") ? 1 : 0, ledger, j -> false);
        assertTrue(none.isEmpty());
        List<Dispatcher.Assignment> plan = Dispatcher.plan(q.pending(c -> true), 5, List.of(chest),
                (p, item) -> item.equals("minetorio:fast_underground_belt") ? 2 : 1, ledger, j -> true);
        assertEquals(3, plan.size());
        assertNull(plan.get(0).provider(), "deconstruction has no provider");
        assertEquals(2, ledger.reserved(chest, "minetorio:fast_underground_belt"), "a pair reserves two");
        assertEquals(1, ledger.reserved(chest, "minetorio:fast_inserter"));
    }

    @Test
    void upgradeGroupsAndRules() {
        assertTrue(UpgradeGroups.compatible("minetorio:transport_belt", "minetorio:fast_transport_belt"));
        assertTrue(UpgradeGroups.compatible("minetorio:inserter", "minetorio:fast_inserter"));
        assertTrue(UpgradeGroups.compatible("minetorio:assembling_machine_1", "minetorio:assembling_machine_2"));
        assertFalse(UpgradeGroups.compatible("minetorio:transport_belt", "minetorio:fast_underground_belt"), "other group");
        assertFalse(UpgradeGroups.compatible("minetorio:splitter", "minetorio:splitter"), "same item");
        assertFalse(UpgradeGroups.compatible("minetorio:stone_furnace", "minetorio:assembling_machine_1"), "no group");
        assertEquals(List.of("minetorio:fast_splitter", "minetorio:express_splitter"), UpgradeGroups.replacements("minetorio:splitter"));
        assertTrue(UpgradeGroups.replacements("minetorio:pipe").isEmpty());

        UpgradeRules r = new UpgradeRules(List.of(
                new UpgradeRules.Rule("minetorio:transport_belt", "minetorio:underground_belt"), // invalid: skipped
                new UpgradeRules.Rule("minetorio:transport_belt", "minetorio:fast_transport_belt"),
                new UpgradeRules.Rule("minetorio:transport_belt", "minetorio:express_transport_belt"), // first wins
                new UpgradeRules.Rule("minetorio:assembling_machine_1", ""))); // half filled
        assertEquals(Optional.of("minetorio:fast_transport_belt"), r.target("minetorio:transport_belt"));
        assertEquals(Optional.empty(), r.target("minetorio:assembling_machine_1"));
        assertEquals(Optional.empty(), r.target("minetorio:inserter"));
        assertTrue(r.hasValid());
        assertFalse(UpgradeRules.EMPTY.hasValid());
    }

    @Test
    void sanitizeCleansClientRules() {
        List<UpgradeRules.Rule> in = new ArrayList<>();
        in.add(new UpgradeRules.Rule("minecraft:dirt", "minetorio:inserter"));
        in.add(new UpgradeRules.Rule("minetorio:inserter", "minetorio:splitter"));
        in.add(new UpgradeRules.Rule("minetorio:inserter", "minetorio:stack_inserter"));
        for (int i = 0; i < 20; i++) in.add(new UpgradeRules.Rule("", ""));
        UpgradeRules r = UpgradeRules.sanitize(in);
        assertEquals(UpgradeRules.MAX_RULES, r.rules().size());
        assertEquals(new UpgradeRules.Rule("", "minetorio:inserter"), r.rules().get(0), "unknown item dropped");
        assertEquals(new UpgradeRules.Rule("minetorio:inserter", ""), r.rules().get(1), "incompatible target cleared");
        assertEquals(Optional.of("minetorio:stack_inserter"), r.target("minetorio:inserter"));
    }

    @Test
    void filterModes() {
        assertTrue(DeconstructionFilter.MACHINES.accepts(true, false));
        assertFalse(DeconstructionFilter.MACHINES.accepts(false, true));
        assertTrue(DeconstructionFilter.MACHINES_AND_NATURE.accepts(false, true));
        assertTrue(DeconstructionFilter.MACHINES_AND_NATURE.accepts(true, false));
        assertFalse(DeconstructionFilter.NATURE_ONLY.accepts(true, false));
        assertTrue(DeconstructionFilter.NATURE_ONLY.accepts(false, true));
        assertFalse(DeconstructionFilter.MACHINES_AND_NATURE.accepts(false, false), "terrain never");
        assertEquals(DeconstructionFilter.MACHINES, DeconstructionFilter.NATURE_ONLY.next());
        assertEquals(DeconstructionFilter.MACHINES, DeconstructionFilter.byId(99));
    }
}
