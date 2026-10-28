package dev.minetorio.core.map;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WatchCoverageTest {
    private static final int R = RadarLogic.NEAR_RADIUS;

    @Test
    void oneWatcherCoversItsSevenBySevenSquare() {
        WatchCoverage c = new WatchCoverage();
        assertTrue(c.put(1, ChunkKeys.of(10, -4), R));
        assertEquals(49, c.size());
        assertTrue(c.isWatched(ChunkKeys.of(7, -7)));
        assertTrue(c.isWatched(ChunkKeys.of(13, -1)));
        assertFalse(c.isWatched(ChunkKeys.of(14, -4)));
        assertFalse(c.isWatched(ChunkKeys.of(10, -8)));
        assertEquals(Set.of(1L), c.watchedBy(ChunkKeys.of(10, -4)));
        assertEquals(Set.of(), c.watchedBy(ChunkKeys.of(100, 0)));
        assertEquals(ChunkKeys.of(10, -4), c.centerOf(1));
        assertEquals(WatchCoverage.NONE, c.centerOf(2));
        assertFalse(c.put(1, ChunkKeys.of(10, -4), R), "same area again is no change");
    }

    @Test
    void overlappingWatchersShareChunksAndRemovalKeepsTheOverlap() {
        WatchCoverage c = new WatchCoverage();
        c.put(1, ChunkKeys.of(0, 0), R);
        c.put(2, ChunkKeys.of(4, 0), R); // columns 1..3 overlap: 3×7 = 21 shared
        assertEquals(49 + 49 - 21, c.size());
        assertEquals(Set.of(1L, 2L), c.watchedBy(ChunkKeys.of(2, 3)));
        c.drain();
        assertTrue(c.remove(1));
        assertFalse(c.remove(1));
        assertEquals(49, c.size());
        for (int z = -3; z <= 3; z++) {
            for (int x = 1; x <= 3; x++) {
                assertTrue(c.isWatched(ChunkKeys.of(x, z)), "overlap chunk lost");
                assertEquals(Set.of(2L), c.watchedBy(ChunkKeys.of(x, z)));
            }
            for (int x = -3; x <= 0; x++) assertFalse(c.isWatched(ChunkKeys.of(x, z)));
        }
        WatchCoverage.Diff d = c.drain();
        assertEquals(28, d.removed().size(), "only the chunks no one covers any more");
        assertTrue(d.added().isEmpty());
    }

    @Test
    void diffsAreNetSinceTheLastDrain() {
        WatchCoverage c = new WatchCoverage();
        c.put(1, ChunkKeys.of(0, 0), R);
        WatchCoverage.Diff d = c.drain();
        assertEquals(49, d.added().size());
        assertTrue(d.removed().isEmpty());
        assertFalse(c.hasChanges());
        assertTrue(c.drain().isEmpty());

        // stop and start again before the next drain: nothing to send
        c.remove(1);
        c.put(1, ChunkKeys.of(0, 0), R);
        assertTrue(c.drain().isEmpty());

        // a second radar overlapping fully adds nothing
        c.put(2, ChunkKeys.of(0, 0), R);
        assertTrue(c.drain().isEmpty());

        // moving one: only the new chunks show up
        c.put(2, ChunkKeys.of(1, 0), R);
        d = c.drain();
        assertEquals(7, d.added().size());
        assertTrue(d.removed().isEmpty());

        // a new watcher appears and goes before the drain: nothing
        c.put(3, ChunkKeys.of(50, 50), R);
        c.remove(3);
        assertTrue(c.drain().isEmpty());

        c.clear();
        d = c.drain();
        assertEquals(56, d.removed().size());
        assertEquals(0, c.size());
        assertEquals(0, c.watcherCount());
    }

    @Test
    void applyingDiffsReproducesTheSet() {
        WatchCoverage c = new WatchCoverage();
        Set<Long> mirror = new HashSet<>();
        long[][] ops = {{1, 0, 0}, {2, 3, 3}, {3, -5, 2}, {-1}, {2, 9, 9}, {-3}, {4, 0, 1}, {-2}, {-4}};
        for (long[] op : ops) {
            if (op.length == 1) c.remove(-op[0]);
            else c.put(op[0], ChunkKeys.of((int) op[1], (int) op[2]), R);
            WatchCoverage.Diff d = c.drain();
            mirror.removeAll(d.removed());
            mirror.addAll(d.added());
            assertEquals(new HashSet<>(c.chunks()), mirror);
        }
        assertTrue(mirror.isEmpty());
    }

    @Test
    void splitBoundsEachPiece() {
        List<Long> keys = new java.util.ArrayList<>();
        for (long i = 0; i < 10; i++) keys.add(i);
        List<long[]> pieces = WatchCoverage.split(keys, 4);
        assertEquals(3, pieces.size());
        assertArrayEquals(new long[] {0, 1, 2, 3}, pieces.get(0));
        assertArrayEquals(new long[] {8, 9}, pieces.get(2));
        assertEquals(1, WatchCoverage.split(List.of(), 4).size());
        assertEquals(0, WatchCoverage.split(List.of(), 4).get(0).length);
        assertEquals(2, WatchCoverage.split(keys.subList(0, 8), 4).size());
    }
}
