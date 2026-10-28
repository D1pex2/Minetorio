package dev.minetorio.core.inserter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StackInserterTest {
    /** A belt: streaming, items are added by the test as they "arrive". */
    static final class Belt extends InserterLogicTest.Src {
        Belt(String... i) { super(i); }

        @Override
        public boolean streaming() { return true; }
    }

    /** A target that accepts at most {@code room} more items. */
    static final class Limited extends InserterLogicTest.Dst {
        int room;
        Limited(int room) { this.room = room; }

        @Override
        public boolean accept(String item) {
            if (room <= 0) return false;
            room--;
            return super.accept(item);
        }
    }

    @Test void baseHandSizeIsFactorio11StackInserter() {
        assertEquals(2, InserterType.STACK.handSize);
        assertEquals(InserterType.FAST.cycleTicks, InserterType.STACK.cycleTicks);
        assertEquals(InserterType.FAST.cycleTicks, InserterType.FILTER.cycleTicks);
        for (InserterType t : InserterType.values()) {
            if (t != InserterType.STACK) assertEquals(1, t.handSize, t.name());
        }
    }

    @Test void takesAFullHandFromAChestInOneTick() {
        var src = new InserterLogicTest.Src("a", "a", "a", "a", "a");
        var dst = new InserterLogicTest.Dst();
        var ins = new InserterLogic(InserterType.STACK);
        ins.tick(src, dst, 1);
        assertEquals("a", ins.held());
        assertEquals(2, ins.heldCount());
        assertTrue(ins.isMoving());
        assertEquals(3, src.items.size());
    }

    @Test void movesAFullHandPerSwing() {
        var src = new InserterLogicTest.Src("a", "a", "a", "a", "a", "a");
        var dst = new InserterLogicTest.Dst();
        var ins = new InserterLogic(InserterType.STACK);
        int swings = 0;
        for (int t = 0; t < 100 && !src.items.isEmpty() || ins.held() != null; t++) {
            int before = dst.got.size();
            ins.tick(src, dst, 1);
            if (dst.got.size() > before) {
                swings++;
                assertEquals(2, dst.got.size() - before, "items per drop");
            }
        }
        assertEquals(3, swings);
        assertEquals(6, dst.got.size());
    }

    @Test void neverMixesItemsInTheHand() {
        var src = new InserterLogicTest.Src("a", "b", "b");
        var ins = new InserterLogic(InserterType.STACK);
        ins.tick(src, new InserterLogicTest.Dst(), 1);
        assertEquals("a", ins.held());
        assertEquals(1, ins.heldCount());
    }

    @Test void leavesAChestWithAPartialHandAtOnce() {
        var src = new InserterLogicTest.Src("a");
        var ins = new InserterLogic(InserterType.STACK);
        ins.tick(src, new InserterLogicTest.Dst(), 1);
        assertEquals(1, ins.heldCount());
        assertTrue(ins.isMoving());
        assertFalse(ins.isFilling());
    }

    @Test void waitsAtABeltForTheHandToFill() {
        var belt = new Belt("a");
        var dst = new InserterLogicTest.Dst();
        var ins = new InserterLogic(InserterType.STACK);
        ins.tick(belt, dst, 1);
        assertTrue(ins.isFilling());
        assertEquals(0, ins.armProgress());
        assertEquals(0, ins.powerDemand());
        for (int t = 0; t < 10; t++) ins.tick(belt, dst, 1);
        assertTrue(ins.isFilling());
        belt.items.add("a");
        ins.tick(belt, dst, 1);
        assertEquals(2, ins.heldCount());
        assertTrue(ins.isMoving());
    }

    @Test void leavesABeltAfterTheTimeout() {
        var belt = new Belt("a");
        var dst = new InserterLogicTest.Dst();
        var ins = new InserterLogic(InserterType.STACK);
        ins.tick(belt, dst, 1);
        int timeout = InserterType.STACK.fillTimeoutTicks;
        for (int t = 0; t < timeout - 1; t++) ins.tick(belt, dst, 1);
        assertTrue(ins.isFilling());
        ins.tick(belt, dst, 1);
        assertFalse(ins.isFilling());
        assertTrue(ins.isMoving());
        assertEquals(1, ins.heldCount());
    }

    @Test void eachNewItemRestartsTheTimeout() {
        var belt = new Belt("a");
        var ins = new InserterLogic(new InserterLogic(InserterType.STACK).type());
        var dst = new InserterLogicTest.Dst();
        ins.tick(belt, dst, 1);
        int timeout = InserterType.STACK.fillTimeoutTicks;
        for (int t = 0; t < timeout - 1; t++) ins.tick(belt, dst, 1);
        // a different item does not count, the hand holds one kind
        belt.items.add("b");
        ins.tick(belt, dst, 1);
        assertFalse(ins.isFilling());
        assertEquals(1, ins.heldCount());
    }

    @Test void singleItemInsertersNeverWaitAtABelt() {
        var belt = new Belt("a");
        var ins = new InserterLogic(InserterType.FAST);
        ins.tick(belt, new InserterLogicTest.Dst(), 1);
        assertFalse(ins.isFilling());
        assertTrue(ins.isMoving());
    }

    @Test void dropsWhatTheTargetTakesAndWaitsWithTheRest() {
        var src = new InserterLogicTest.Src("a", "a");
        var dst = new Limited(1);
        var ins = new InserterLogic(InserterType.STACK);
        for (int t = 0; t < 20; t++) ins.tick(src, dst, 1);
        assertEquals(1, dst.got.size());
        assertEquals(1, ins.heldCount());
        assertEquals(1.0, ins.armProgress());
        dst.room = 5;
        ins.tick(src, dst, 1);
        assertEquals(2, dst.got.size());
        assertNull(ins.held());
        assertEquals(0, ins.heldCount());
    }

    @Test void saveLoadKeepsCountAndFilling() {
        var belt = new Belt("a");
        var ins = new InserterLogic(InserterType.STACK);
        var dst = new InserterLogicTest.Dst();
        for (int t = 0; t < 5; t++) ins.tick(belt, dst, 1);
        var copy = new InserterLogic(InserterType.STACK);
        copy.load(ins.save());
        assertTrue(copy.isFilling());
        assertEquals(1, copy.heldCount());
        assertEquals(ins.save(), copy.save());

        var carrying = new InserterLogic(InserterType.STACK);
        carrying.load(Map.of("phase", "TO_DROP", "held", "a", "count", 2, "leg", 0.5));
        assertEquals(2, carrying.heldCount());
        // filling without an item is repaired to "at pickup"
        var broken = new InserterLogic(InserterType.STACK);
        broken.load(Map.of("phase", "FILLING", "leg", 0.0));
        assertFalse(broken.isFilling());
        assertNull(broken.held());
    }

    @Test void takeHeldEmptiesTheWholeHand() {
        var src = new InserterLogicTest.Src("a", "a");
        var ins = new InserterLogic(InserterType.STACK);
        ins.tick(src, new InserterLogicTest.Dst(), 1);
        assertEquals(2, ins.heldCount());
        assertEquals("a", ins.takeHeld());
        assertEquals(0, ins.heldCount());
        assertEquals(List.of(), src.items);
    }
}
