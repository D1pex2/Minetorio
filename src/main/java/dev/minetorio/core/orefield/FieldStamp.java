package dev.minetorio.core.orefield;

import java.util.ArrayList;
import java.util.List;

/** Which columns of a box get a field block of which type and amount: the pure half of stamping patches into the world. */
public final class FieldStamp {
    /** One field block to stamp: column (x, z), its type and initial amount. */
    public record Cell(int x, int z, OreType type, long amount) {}

    private FieldStamp() {}

    /**
     * The cells of {@code patches} inside the box [minX..maxX] × [minZ..maxZ] (inclusive), amounts
     * per {@link OreAmounts#amountAt} with spawn at (spawnX, spawnZ). Where patches overlap, the
     * first in the list wins. Depends only on the columns, so stamping chunk by chunk equals stamping
     * the whole area at once.
     */
    public static List<Cell> plan(List<OrePatch> patches, int minX, int minZ, int maxX, int maxZ, int spawnX, int spawnZ) {
        List<Cell> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (OrePatch p : patches) {
                    if (p.contains(x, z)) {
                        out.add(new Cell(x, z, p.type(), OreAmounts.amountAt(p, x, z, spawnX, spawnZ)));
                        break;
                    }
                }
            }
        }
        return out;
    }
}
