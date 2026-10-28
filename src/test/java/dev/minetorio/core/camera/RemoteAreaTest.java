package dev.minetorio.core.camera;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RemoteAreaTest {
    /** Chunks with x in 0..3 and any z are eligible. */
    private static final RemoteArea.ChunkTest STRIP = (cx, cz) -> cx >= 0 && cx <= 3;

    @Test
    void chunkOfFloorsNegativeCoordinates() {
        assertEquals(0, RemoteArea.chunkOf(0));
        assertEquals(0, RemoteArea.chunkOf(15.99));
        assertEquals(1, RemoteArea.chunkOf(16));
        assertEquals(-1, RemoteArea.chunkOf(-0.01));
        assertEquals(-1, RemoteArea.chunkOf(-16));
        assertEquals(-2, RemoteArea.chunkOf(-16.5));
    }

    @Test
    void ownViewIsAChebyshevSquare() {
        assertTrue(RemoteArea.ownView(0, 0, 8, 8, -8));
        assertFalse(RemoteArea.ownView(0, 0, 8, 9, 0));
        assertFalse(RemoteArea.ownView(5, 5, 2, 5, 8));
    }

    @Test
    void areaNeedsDistanceAndEligibility() {
        RemoteArea.ChunkTest all = (x, z) -> true;
        assertTrue(RemoteArea.inArea(10, 10, 10 + RemoteArea.RADIUS, 10 - RemoteArea.RADIUS, all));
        assertFalse(RemoteArea.inArea(10, 10, 10 + RemoteArea.RADIUS + 1, 10, all));
        assertTrue(RemoteArea.inArea(1, 0, 3, 4, STRIP));
        assertFalse(RemoteArea.inArea(1, 0, 4, 0, STRIP), "unwatched chunk inside the radius");
        assertFalse(RemoteArea.inArea(1, 0, -1, 0, STRIP));
    }

    @Test
    void stepMovesFreelyOverEligibleGround() {
        assertArrayEquals(new double[] {20, 5}, RemoteArea.step(10, 0, 10, 5, STRIP), 1e-9);
        assertArrayEquals(new double[] {10, 0}, RemoteArea.step(10, 0, 0, 0, STRIP), 1e-9);
    }

    @Test
    void stepSlidesAlongTheEdge() {
        // heading diagonally out of the strip at x = 64: keeps the Z part
        assertArrayEquals(new double[] {60, 13}, RemoteArea.step(60, 10, 8, 3, STRIP), 1e-9);
        // out at x < 0 the same way
        assertArrayEquals(new double[] {1, -4}, RemoteArea.step(1, 0, -2, -4, STRIP), 1e-9);
    }

    @Test
    void stepStopsWhenBothAxesLeave() {
        RemoteArea.ChunkTest one = (cx, cz) -> cx == 0 && cz == 0;
        assertArrayEquals(new double[] {15, 15}, RemoteArea.step(15, 15, 2, 2, one), 1e-9);
    }

    @Test
    void ticketReachesSendableLevelOverTheWholeArea() {
        // region ticket level at Chebyshev distance d: 33 - TICKET_DISTANCE + d; 32 = block ticking (sendable)
        assertTrue(33 - RemoteArea.TICKET_DISTANCE + RemoteArea.RADIUS <= 32);
        // the tracking circle with its outer ring reaches RADIUS chunks on the axes
        assertEquals(RemoteArea.RADIUS, RemoteArea.TRACKING_VIEW_DISTANCE + 1);
    }

    @Test
    void panSpeedGrowsWithHeight() {
        assertTrue(RemoteArea.panSpeed(40) > RemoteArea.panSpeed(8));
    }
}
