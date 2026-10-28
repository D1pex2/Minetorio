package dev.minetorio.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlueprintBooksTest {
    @Test
    void wheelCyclesAndWraps() {
        assertEquals(1, BlueprintBooks.cycle(0, 3, 1));
        assertEquals(0, BlueprintBooks.cycle(2, 3, 1));
        assertEquals(2, BlueprintBooks.cycle(0, 3, -1));
        assertEquals(0, BlueprintBooks.cycle(-1, 3, 0));
        assertEquals(-1, BlueprintBooks.cycle(0, 0, 1));
    }

    @Test
    void removingKeepsTheActiveEntry() {
        // [A B C D], C active
        assertEquals(1, BlueprintBooks.activeAfterRemove(2, 0, 4), "an entry before it goes");
        assertEquals(2, BlueprintBooks.activeAfterRemove(2, 3, 4), "an entry after it goes");
        assertEquals(2, BlueprintBooks.activeAfterRemove(2, 2, 4), "it goes: the next one");
        assertEquals(1, BlueprintBooks.activeAfterRemove(2, 2, 3), "the last goes: the one before");
        assertEquals(-1, BlueprintBooks.activeAfterRemove(0, 0, 1), "the book is empty");
    }

    @Test
    void insertingKeepsTheActiveEntry() {
        assertEquals(0, BlueprintBooks.activeAfterInsert(-1, 0, 0), "first entry becomes active");
        assertEquals(3, BlueprintBooks.activeAfterInsert(2, 1, 4));
        assertEquals(2, BlueprintBooks.activeAfterInsert(2, 3, 4));
    }

    @Test
    void movingFollowsTheActiveEntry() {
        List<String> l = List.of("A", "B", "C", "D");
        for (int from = 0; from < 4; from++) {
            for (int to = 0; to < 4; to++) {
                for (int active = 0; active < 4; active++) {
                    List<String> m = BlueprintBooks.moved(l, from, to);
                    int a = BlueprintBooks.activeAfterMove(active, from, to);
                    assertEquals(l.get(active), m.get(a), "move " + from + "->" + to + " active " + active);
                }
            }
        }
        assertEquals(List.of("B", "C", "A", "D"), BlueprintBooks.moved(l, 0, 2));
    }

    @Test
    void labelsAreCleaned() {
        assertEquals("Smelter", BlueprintBooks.cleanLabel("  Smel\nter\u0007 "));
        assertEquals("cred", BlueprintBooks.cleanLabel("§cred"));
        assertEquals(BlueprintBooks.MAX_LABEL, BlueprintBooks.cleanLabel("x".repeat(200)).length());
        assertTrue(BlueprintBooks.validLabel("Main bus"));
        assertFalse(BlueprintBooks.validLabel(" padded"));
        assertEquals("", BlueprintBooks.cleanLabel(null));
    }

    @Test
    void paths() {
        assertTrue(BlueprintBooks.validPath(List.of()));
        assertTrue(BlueprintBooks.validPath(List.of(3, 5)));
        assertFalse(BlueprintBooks.validPath(List.of(0, 0, 0)), "too deep");
        assertFalse(BlueprintBooks.validPath(List.of(-1)));
        assertFalse(BlueprintBooks.validPath(List.of(BlueprintBooks.MAX_ENTRIES)));
    }

    @Test
    void defaultIconsAreTheMostFrequent() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("belt", 10);
        counts.put("inserter", 4);
        counts.put("pole", 4);
        counts.put("assembler", 6);
        counts.put("pipe", 1);
        assertEquals(List.of("belt", "assembler", "inserter", "pole"), BlueprintBooks.defaultIcons(counts));
    }
}
