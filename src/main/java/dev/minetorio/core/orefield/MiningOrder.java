package dev.minetorio.core.orefield;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The order a drill takes the field blocks of its mining area in. */
public final class MiningOrder {
    private MiningOrder() {}

    /**
     * Columns {x, z} of the box [minX..maxX] × [minZ..maxZ] (inclusive), nearest to the box center
     * first; equally near ones row by row (z, then x).
     */
    public static List<int[]> nearestFirst(int minX, int minZ, int maxX, int maxZ) {
        List<int[]> out = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) out.add(new int[] {x, z});
        }
        // doubled coordinates keep the (possibly half-block) center exact
        long cx2 = (long) minX + maxX, cz2 = (long) minZ + maxZ;
        out.sort(Comparator.comparingLong((int[] c) -> sq(2L * c[0] - cx2) + sq(2L * c[1] - cz2))
                .thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
        return out;
    }

    private static long sq(long v) { return v * v; }
}
