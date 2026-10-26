package dev.minetorio.core.inserter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class InserterLogicTest {
    static final class Src implements PickupSource {
        final List<String> items = new ArrayList<>();
        Src(String... i) { items.addAll(List.of(i)); }
        public Optional<String> peek(Predicate<String> w) { return items.stream().filter(w).findFirst(); }
        public boolean take(String item) { return items.remove(item); }
    }

    static final class Dst implements DropTarget {
        final List<String> got = new ArrayList<>();
        boolean open = true;
        String only;
        public boolean wants(String item) { return only == null || only.equals(item); }
        public boolean accept(String item) { if (!open) return false; got.add(item); return true; }
    }

    @Test void basicMovesOneItemPerCycle() {
        var src = new Src("a", "a", "a", "a", "a");
        var dst = new Dst();
        var ins = new InserterLogic(InserterType.BASIC);
        for (int t = 0; t < 24 * 5 - 13; t++) ins.tick(src, dst, 1);
        assertEquals(4, dst.got.size());
        for (int t = 0; t < 13; t++) ins.tick(src, dst, 1);
        assertEquals(5, dst.got.size());
    }

    @Test void waitsHoldingWhenTargetFull() {
        var src = new Src("a");
        var dst = new Dst();
        dst.open = false;
        var ins = new InserterLogic(InserterType.BASIC);
        for (int t = 0; t < 100; t++) ins.tick(src, dst, 1);
        assertNotNull(ins.held());
        assertTrue(src.items.isEmpty());
        assertEquals(0, ins.powerDemand());
        dst.open = true;
        ins.tick(src, dst, 1);
        assertEquals(1, dst.got.size());
        assertNull(ins.held());
    }

    @Test void onlyPicksWanted() {
        var src = new Src("stone", "iron_plate");
        var dst = new Dst();
        dst.only = "iron_plate";
        var ins = new InserterLogic(InserterType.FAST);
        for (int t = 0; t < 20; t++) ins.tick(src, dst, 1);
        assertEquals(List.of("iron_plate"), dst.got);
        assertEquals(List.of("stone"), src.items);
    }

    @Test void noPowerNoMove() {
        var src = new Src("a");
        var dst = new Dst();
        var ins = new InserterLogic(InserterType.BASIC);
        for (int t = 0; t < 10; t++) ins.tick(src, dst, 0);
        assertEquals(0, ins.armProgress());
        ins.tick(src, dst, 1);
        double p = ins.armProgress();
        assertTrue(p > 0);
        ins.tick(src, dst, 0);
        assertEquals(p, ins.armProgress());
    }

    @Test void idleDrawsNoPower() {
        var ins = new InserterLogic(InserterType.BASIC);
        ins.tick(new Src(), new Dst(), 1);
        assertEquals(0, ins.powerDemand());
        assertFalse(ins.isMoving());
    }

    @Test void movingDrawsPower() {
        var ins = new InserterLogic(InserterType.BASIC);
        ins.tick(new Src("a"), new Dst(), 1);
        assertEquals(InserterType.BASIC.powerPerTickJ, ins.powerDemand());
    }

    @Test void saveLoadMidCarryContinuesIdentically() {
        var src = new Src("a", "b");
        var dst = new Dst();
        var ins = new InserterLogic(InserterType.BASIC);
        for (int t = 0; t < 5; t++) ins.tick(src, dst, 1);
        var copy = new InserterLogic(InserterType.BASIC);
        copy.load(ins.save());
        assertEquals("a", copy.held());
        assertEquals(ins.armProgress(), copy.armProgress());
        assertTrue(copy.isMoving());
        var dst2 = new Dst();
        var src2 = new Src("b");
        for (int t = 0; t < 30; t++) {
            ins.tick(src, dst, 1);
            copy.tick(src2, dst2, 1);
            assertEquals(ins.armProgress(), copy.armProgress(), "tick " + t);
        }
        assertEquals(dst.got, dst2.got);
    }

    @Test void loadWaitingAndEmptyStates() {
        var src = new Src("a");
        var dst = new Dst();
        dst.open = false;
        var ins = new InserterLogic(InserterType.FAST);
        for (int t = 0; t < 20; t++) ins.tick(src, dst, 1);
        var copy = new InserterLogic(InserterType.FAST);
        copy.load(ins.save());
        assertEquals("a", copy.held());
        assertEquals(1.0, copy.armProgress());
        assertFalse(copy.isMoving());

        var idle = new InserterLogic(InserterType.FAST);
        idle.load(new InserterLogic(InserterType.FAST).save());
        assertNull(idle.held());
        assertEquals(0.0, idle.armProgress());
    }

    @Test void loadNeverDropsAHeldItem() {
        var ins = new InserterLogic(InserterType.BASIC);
        // Inconsistent data (item in hand while "at pickup"): the item is kept and carried.
        ins.load(java.util.Map.of("phase", "AT_PICKUP", "held", "a", "leg", 0.0));
        assertEquals("a", ins.held());
        var dst = new Dst();
        for (int t = 0; t < 12; t++) ins.tick(new Src("b"), dst, 1);
        assertEquals(List.of("a"), dst.got);
    }

    @Test void takeHeldSendsArmBackFromWhereItIs() {
        var src = new Src("a", "b");
        var dst = new Dst();
        var ins = new InserterLogic(InserterType.BASIC);
        for (int t = 0; t < 4; t++) ins.tick(src, dst, 1);
        double p = ins.armProgress();
        assertTrue(p > 0 && p < 1);
        assertEquals("a", ins.takeHeld());
        assertNull(ins.held());
        assertEquals(p, ins.armProgress(), 1e-9);
        assertTrue(ins.isMoving());
        for (int t = 0; t < 4; t++) ins.tick(src, dst, 1);
        assertEquals(0.0, ins.armProgress(), 1e-9);
        ins.tick(src, dst, 1);
        assertEquals("b", ins.held());
        assertTrue(dst.got.isEmpty());
        assertNull(new InserterLogic(InserterType.BASIC).takeHeld());
    }
}
