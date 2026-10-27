package dev.minetorio.core.map;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapSchedulingTest {
    @Test
    void throttleDefersRepeatScansOfAChunk() {
        ScanThrottle t = new ScanThrottle(100);
        assertTrue(t.request(1, 0));
        t.scanned(1, 0);
        assertFalse(t.request(1, 50), "too soon");
        assertFalse(t.request(1, 60), "still too soon, not queued twice");
        assertTrue(t.due(99).isEmpty());
        assertEquals(List.of(1L), t.due(100));
        assertTrue(t.due(101).isEmpty(), "handed out once");
        assertTrue(t.request(2, 50), "other chunks are independent");
    }

    @Test
    void throttlePruneForgetsOldScans() {
        ScanThrottle t = new ScanThrottle(100);
        t.scanned(1, 0);
        t.scanned(2, 90);
        t.prune(150);
        assertEquals(1, t.tracked());
        assertTrue(t.request(1, 150));
    }

    @Test
    void sendQueueDedupesAndPollsInOrder() {
        SendQueue q = new SendQueue();
        q.add(5);
        q.add(6);
        q.add(5);
        assertEquals(2, q.size());
        assertEquals(List.of(5L), q.poll(1));
        assertEquals(List.of(6L), q.poll(10));
        assertTrue(q.poll(10).isEmpty());
    }

    @Test
    void sendQueueSortsBacklogByRegionNearestFirst() {
        SendQueue q = new SendQueue();
        // (-3,-3) is the nearest chunk but lies in region (-1,-1); the player's own region (0,0) goes first
        q.addAllByRegion(List.of(ChunkKeys.of(10, 0), ChunkKeys.of(0, 1), ChunkKeys.of(-3, -3), ChunkKeys.of(0, 0),
                ChunkKeys.of(70, 0)), 0, 0);
        assertEquals(List.of(ChunkKeys.of(0, 0), ChunkKeys.of(0, 1), ChunkKeys.of(10, 0), ChunkKeys.of(-3, -3),
                ChunkKeys.of(70, 0)), q.poll(10));
    }

    @Test
    void byteBudgetAveragesTheRatePerTick() {
        SendQueue q = new SendQueue();
        int sent = 0, bytes = 0;
        for (int tick = 0; tick < 100; tick++) {
            q.refill(12_000, 40_000);
            if (q.canSend()) {
                q.spend(30_000);
                sent++;
                bytes += 30_000;
            }
        }
        assertTrue(bytes <= 100 * 12_000 + 40_000, "sent " + bytes);
        assertTrue(sent >= 38, "only " + sent + " batches");
    }

    @Test
    void byteBudgetDoesNotHoardWhileIdle() {
        SendQueue q = new SendQueue();
        for (int i = 0; i < 1000; i++) q.refill(12_000, 40_000);
        q.spend(40_000);
        assertFalse(q.canSend());
    }

    @Test
    void chunksAroundCoversTheSquare() {
        List<Long> around = ChunkKeys.around(4, -2, 3);
        assertEquals(49, around.size());
        assertTrue(around.contains(ChunkKeys.of(1, -5)));
        assertTrue(around.contains(ChunkKeys.of(7, 1)));
        assertFalse(around.contains(ChunkKeys.of(8, 1)));
    }
}
