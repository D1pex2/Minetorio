package dev.minetorio.core.orefield;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Which patch a field block column belongs to, by the same rule stamping uses: a later stamp
 * overwrites an earlier one, so recorded patches (starting fields, demo fields; stamped whole, after
 * world generation of their chunks) win over generated ones, and among recorded patches the one
 * stamped last wins. Generated patches never overlap each other ({@link PatchGenerator}); those
 * reaching into the starting area are removed when the starting fields are stamped, so they are
 * never the answer once the starting fields exist. Only a patch of the block's own type matches
 * (a field block of another type there was put by something else).
 */
public final class PatchLookup {
    private PatchLookup() {}

    /** A patch and where it comes from. */
    public record Ref(PatchKind kind, OrePatch patch) {
        public long key() { return PatchLedger.key(kind, patch); }
    }

    /**
     * The patch of the {@code type} field block in column (x, z), or null.
     *
     * @param recorded recorded patches, oldest stamp first
     * @param generator the generated layout (around the origin), or null for none
     * @param startingSpawn the spawn the starting fields were stamped around ({x, z}), or null if they are not stamped
     */
    @Nullable
    public static Ref find(int x, int z, OreType type, List<Ref> recorded, @Nullable PatchGenerator generator, @Nullable int[] startingSpawn) {
        for (int i = recorded.size() - 1; i >= 0; i--) {
            Ref r = recorded.get(i);
            if (r.patch().type() == type && r.patch().contains(x, z)) return r;
        }
        if (generator == null) return null;
        for (OrePatch p : generator.patchesTouching(x, z, x, z)) {
            if (p.type() != type || !p.contains(x, z)) continue;
            if (startingSpawn != null && PatchGenerator.overlapsStartingArea(p, startingSpawn[0], startingSpawn[1])) continue;
            return new Ref(PatchKind.GENERATED, p);
        }
        return null;
    }
}
