package dev.minetorio.core.inserter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemFilterTest {
    @Test void emptyWhitelistPassesNothingEmptyBlacklistPassesAll() {
        var f = new ItemFilter(5);
        assertFalse(f.matches("minetorio:iron_plate"));
        f.setBlacklist(true);
        assertTrue(f.matches("minetorio:iron_plate"));
    }

    @Test void whitelistPassesOnlyListedItems() {
        var f = new ItemFilter(5);
        f.set(0, "minetorio:iron_plate");
        f.set(3, "minecraft:coal");
        assertTrue(f.matches("minetorio:iron_plate"));
        assertTrue(f.matches("minecraft:coal"));
        assertFalse(f.matches("minetorio:copper_plate"));
    }

    @Test void blacklistPassesEverythingButListedItems() {
        var f = new ItemFilter(5);
        f.set(1, "minetorio:iron_plate");
        f.setBlacklist(true);
        assertFalse(f.matches("minetorio:iron_plate"));
        assertTrue(f.matches("minetorio:copper_plate"));
    }

    @Test void matchingIgnoresDataComponents() {
        var f = new ItemFilter(5);
        f.set(0, "minecraft:diamond_sword#{damage:3}");
        assertEquals("minecraft:diamond_sword", f.get(0));
        assertTrue(f.matches("minecraft:diamond_sword"));
        assertTrue(f.matches("minecraft:diamond_sword#{enchanted}"));
    }

    @Test void clearingASlot() {
        var f = new ItemFilter(5);
        f.set(2, "minecraft:coal");
        f.set(2, null);
        assertNull(f.get(2));
        f.set(2, "");
        assertNull(f.get(2));
        assertFalse(f.matches("minecraft:coal"));
    }

    @Test void saveLoadRoundTrip() {
        var f = new ItemFilter(5);
        f.set(0, "minetorio:iron_plate");
        f.set(4, "minecraft:coal");
        f.setBlacklist(true);
        var copy = new ItemFilter(5);
        copy.load(f.save());
        assertEquals(f, copy);
        assertEquals(List.of("minetorio:iron_plate", "", "", "", "minecraft:coal"), f.save().get("items"));
        assertEquals("blacklist", f.save().get("mode"));
    }

    @Test void loadToleratesMissingAndExtraData() {
        var f = new ItemFilter(2);
        f.set(1, "x:y");
        f.setBlacklist(true);
        f.load(Map.of("items", List.of("a:b", "c:d", "e:f")));
        assertEquals("a:b", f.get(0));
        assertEquals("c:d", f.get(1));
        assertFalse(f.isBlacklist());
        f.load(Map.of());
        assertNull(f.get(0));
    }

    @Test void filterInserterOnlyPicksMatchingItems() {
        var src = new InserterLogicTest.Src("minecraft:stone", "minetorio:iron_plate", "minecraft:stone");
        var dst = new InserterLogicTest.Dst();
        var ins = new InserterLogic(InserterType.FILTER);
        ins.filter().set(0, "minetorio:iron_plate");
        for (int t = 0; t < 40; t++) ins.tick(src, dst, 1);
        assertEquals(List.of("minetorio:iron_plate"), dst.got);
        assertEquals(List.of("minecraft:stone", "minecraft:stone"), src.items);
    }

    @Test void filterInserterBlacklist() {
        var src = new InserterLogicTest.Src("minecraft:stone", "minetorio:iron_plate", "minecraft:stone");
        var dst = new InserterLogicTest.Dst();
        var ins = new InserterLogic(InserterType.FILTER);
        ins.filter().set(0, "minetorio:iron_plate");
        ins.filter().setBlacklist(true);
        for (int t = 0; t < 40; t++) ins.tick(src, dst, 1);
        assertEquals(List.of("minecraft:stone", "minecraft:stone"), dst.got);
        assertEquals(List.of("minetorio:iron_plate"), src.items);
    }

    @Test void onlyTheFilterInserterHasAFilter() {
        for (InserterType t : InserterType.values()) {
            assertEquals(t == InserterType.FILTER, new InserterLogic(t).filter() != null, t.name());
        }
        assertEquals(5, InserterType.FILTER.filterSlots);
    }
}
