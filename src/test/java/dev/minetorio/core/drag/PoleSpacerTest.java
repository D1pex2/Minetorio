package dev.minetorio.core.drag;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoleSpacerTest {
    private static final double REACH = 7.5;

    private static List<int[]> straight(int n) {
        List<int[]> path = new ArrayList<>();
        for (int x = 0; x < n; x++) path.add(new int[] {x, 0, 0});
        return path;
    }

    private static double dist(int[] a, int[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test void straightLineUsesTheLongestSpan() {
        assertEquals(7, PoleSpacer.straightSpacing(REACH));
        assertEquals(List.of(7, 14), PoleSpacer.picks(straight(21), REACH));
        assertEquals(List.of(7, 14), PoleSpacer.picks(straight(16), REACH));
        assertEquals(List.of(7), PoleSpacer.picks(straight(15), REACH), "cell 14 is the head: no pole until the next cell");
        assertEquals(List.of(), PoleSpacer.picks(straight(8), REACH), "a short drag places no extra pole (Factorio)");
    }

    @Test void cornerMeasuresStraightLineDistance() {
        List<int[]> path = new ArrayList<>();
        for (int x = 0; x <= 5; x++) path.add(new int[] {x, 0, 0});
        for (int z = -1; z >= -9; z--) path.add(new int[] {5, 0, z});
        List<Integer> picks = PoleSpacer.picks(path, REACH);
        // (5,0,-5) is 7.07 from the start, (5,0,-6) 7.81: the pole goes on (5,0,-5), index 10
        assertEquals(10, picks.getFirst());
        assertArrayEquals(new int[] {5, 0, -5}, path.get(10));
    }

    @Test void heightCountsTowardTheReach() {
        List<int[]> path = new ArrayList<>();
        for (int x = 0; x <= 10; x++) path.add(new int[] {x, x / 3, 0}); // y 0 0 0 1 1 1 2 2 2 3 3
        List<Integer> picks = PoleSpacer.picks(path, REACH);
        // (7,2) is sqrt(53) = 7.28 away, (8,2) is sqrt(68) = 8.2
        assertEquals(List.of(7), picks.subList(0, 1));
    }

    @Test void consecutivePolesAreAlwaysInReach() {
        List<int[]> path = new ArrayList<>();
        int x = 0, z = 0;
        for (int i = 0; i < 200; i++) {
            if ((i / 9) % 2 == 0) x++; else z--;
            path.add(new int[] {x, (i / 5) % 3, z});
        }
        List<Integer> picks = PoleSpacer.picks(path, REACH);
        assertFalse(picks.isEmpty());
        int[] last = path.getFirst();
        for (int i : picks) {
            assertTrue(dist(last, path.get(i)) <= REACH, "gap before pick " + i);
            last = path.get(i);
        }
        assertTrue(dist(last, path.getLast()) <= REACH + 1.5, "tail is at most one cell past the reach");
    }

    @Test void incrementalMatchesBatch() {
        List<int[]> path = straight(30);
        PoleSpacer spacer = new PoleSpacer(REACH, 0, 0, 0);
        List<Integer> got = new ArrayList<>();
        for (int i = 1; i + 1 < path.size(); i++) {
            int[] c = path.get(i), n = path.get(i + 1);
            if (spacer.poleBefore(n[0], n[1], n[2])) {
                got.add(i);
                spacer.placed(c[0], c[1], c[2]);
            }
        }
        assertEquals(PoleSpacer.picks(path, REACH), got);
        assertEquals(List.of(7, 14, 21, 28), got);
    }

    @Test void failedPlacementKeepsTheOldPoleAsReference() {
        PoleSpacer spacer = new PoleSpacer(REACH, 0, 0, 0);
        assertTrue(spacer.poleBefore(8, 0, 0));
        // not placed: the spacer still measures from the start, so the next cell asks again
        assertTrue(spacer.poleBefore(9, 0, 0));
        spacer.placed(8, 0, 0);
        assertFalse(spacer.poleBefore(9, 0, 0));
    }
}
