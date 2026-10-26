package dev.minetorio.core.handcraft;

import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.item.Inventory;
import dev.minetorio.core.recipe.RecipeBook;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class HandcraftQueueTest {
    private static final String IRON = "minetorio:iron_plate";
    private static final String COPPER = "minetorio:copper_plate";
    private static final String GEAR = "minetorio:iron_gear_wheel";
    private static final String CABLE = "minetorio:copper_cable";
    private static final String INSERTER = "minetorio:inserter";

    private final RecipeBook book = Phase1Data.recipes();
    private final Predicate<String> all = id -> true;

    private static final class Inv implements Inventory {
        final Map<String, Integer> m = new HashMap<>();
        int cap = Integer.MAX_VALUE;
        Inv with(String i, int n) { m.put(i, n); return this; }
        public int count(String i) { return m.getOrDefault(i, 0); }
        public int remove(String i, int n) {
            int r = Math.min(n, count(i));
            if (r > 0) m.merge(i, -r, Integer::sum);
            if (count(i) == 0) m.remove(i);
            return r;
        }
        public int add(String i, int n) {
            int r = Math.max(0, Math.min(n, cap));
            if (r > 0) m.merge(i, r, Integer::sum);
            return r;
        }
    }

    private void tick(HandcraftQueue q, Inv inv, int n) { for (int i = 0; i < n; i++) q.tick(inv); }

    @Test void craftsGearFromPlates() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 2);
        assertTrue(q.enqueue("iron_gear_wheel", 1, inv, all));
        assertEquals(0, inv.count(IRON));
        tick(q, inv, 9);
        assertEquals(0, inv.count(GEAR));
        tick(q, inv, 1);
        assertEquals(1, inv.count(GEAR));
        assertTrue(q.entries().isEmpty());
    }

    @Test void autoCraftsIntermediates() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 4).with(COPPER, 2);
        assertEquals(1, q.maxCraftable("inserter", inv, all));
        assertTrue(q.enqueue("inserter", 1, inv, all));
        assertTrue(inv.m.isEmpty());
        tick(q, inv, 49);
        assertEquals(0, inv.count(INSERTER));
        tick(q, inv, 1);
        assertEquals(1, inv.count(INSERTER));
        assertEquals(1, inv.count(CABLE));
        assertEquals(2, inv.m.size());
    }

    @Test void failsAtomically() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 3).with(COPPER, 1);
        assertFalse(q.enqueue("inserter", 1, inv, all));
        assertEquals(3, inv.count(IRON));
        assertEquals(1, inv.count(COPPER));
        assertTrue(q.entries().isEmpty());
    }

    @Test void lockedRecipeRejected() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 2);
        assertFalse(q.enqueue("iron_gear_wheel", 1, inv, id -> false));
        assertEquals(0, q.maxCraftable("iron_gear_wheel", inv, id -> false));
        assertEquals(2, inv.count(IRON));
    }

    @Test void smeltingRejected() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with("minetorio:iron_ore", 5);
        assertFalse(q.enqueue("iron_plate", 1, inv, all));
        assertEquals(5, inv.count("minetorio:iron_ore"));
    }

    @Test void cancelRefundsEverything() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 4).with(COPPER, 2);
        assertTrue(q.enqueue("inserter", 1, inv, all));
        tick(q, inv, 3);
        q.cancel(0, inv);
        assertEquals(Map.of(IRON, 4, COPPER, 2), inv.m);
        assertTrue(q.entries().isEmpty());
    }

    @Test void saveLoadRoundTrip() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 4).with(COPPER, 2);
        assertTrue(q.enqueue("inserter", 1, inv, all));
        tick(q, inv, 23);
        HandcraftQueue q2 = HandcraftQueue.load(book, q.save());
        assertEquals(q.entries(), q2.entries());
        Inv inv2 = new Inv();
        tick(q2, inv2, 26);
        assertEquals(0, inv2.count(INSERTER));
        tick(q2, inv2, 1);
        assertEquals(1, inv2.count(INSERTER));
        assertEquals(1, inv2.count(CABLE));
    }

    @Test void undeliveredOutputIsKeptWhenInventoryFull() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 2);
        q.enqueue("iron_gear_wheel", 1, inv, all);
        inv.cap = 0;
        tick(q, inv, 12);
        assertEquals(0, inv.count(GEAR));
        assertEquals(1, q.entries().size());
        inv.cap = 10;
        tick(q, inv, 1);
        assertEquals(1, inv.count(GEAR));
        assertTrue(q.entries().isEmpty());
    }

    @Test void multipleTimesAndMaxCraftable() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 6);
        assertEquals(3, q.maxCraftable("iron_gear_wheel", inv, all));
        assertTrue(q.enqueue("iron_gear_wheel", 3, inv, all));
        tick(q, inv, 30);
        assertEquals(3, inv.count(GEAR));
    }

    @Test void cancelAfterPartialDeliveryKeepsDeliveredAndRefundsRest() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 6);
        assertTrue(q.enqueue("iron_gear_wheel", 3, inv, all));
        tick(q, inv, 20);
        assertTrue(q.cancel(0, inv).isEmpty());
        assertEquals(Map.of(GEAR, 2, IRON, 2), inv.m);
    }

    @Test void cancelThreeInsertersAtTick75ConservesValue() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 10).with(COPPER, 4).with(CABLE, 2).with(GEAR, 1);
        assertTrue(q.enqueue("inserter", 3, inv, all));
        tick(q, inv, 75);
        assertEquals(2, inv.count(INSERTER));
        assertTrue(q.cancel(0, inv).isEmpty());
        assertEquals(2, inv.count(INSERTER));
        // craft 1 takes 2 iron, 2 cable, 1 gear, 1 copper; craft 2 takes 4 iron, 1 copper (spare cable reused);
        // craft 3 (refunded) is planned with 4 iron and 2 copper
        assertEquals(Map.of(INSERTER, 2, IRON, 4, COPPER, 2), inv.m);
    }

    @Test void cancelWithFullInventoryReturnsEverything() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 4).with(COPPER, 2);
        q.enqueue("inserter", 1, inv, all);
        inv.cap = 0;
        Map<String, Integer> rest = q.cancel(0, inv);
        assertEquals(Map.of(IRON, 4, COPPER, 2), rest);
        assertTrue(inv.m.isEmpty());
    }

    @Test void cancelNonFrontEntry() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 4);
        q.enqueue("iron_gear_wheel", 1, inv, all);
        q.enqueue("iron_gear_wheel", 1, inv, all);
        tick(q, inv, 3);
        assertTrue(q.cancel(1, inv).isEmpty());
        assertEquals(Map.of(IRON, 2), inv.m);
        assertEquals(1, q.entries().size());
        tick(q, inv, 7);
        assertEquals(1, inv.count(GEAR));
    }

    @Test void twoInsertersReuseSpareCable() {
        HandcraftQueue q = new HandcraftQueue(book);
        Inv inv = new Inv().with(IRON, 8).with(COPPER, 4);
        assertEquals(2, q.maxCraftable("inserter", inv, all));
        assertTrue(q.enqueue("inserter", 2, inv, all));
        assertEquals(Map.of(COPPER, 1), inv.m); // only 3 copper needed: the spare cable is reused
        tick(q, inv, 200);
        assertEquals(Map.of(INSERTER, 2, COPPER, 1), inv.m);
        assertTrue(q.entries().isEmpty());
    }
}
