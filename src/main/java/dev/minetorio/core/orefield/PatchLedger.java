package dev.minetorio.core.orefield;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Remaining ore per patch, so a whole patch's total is known without loading its chunks: one entry
 * per patch (not per block). Stamping adds each stamped block's amount; every ore taken and every
 * field block removed subtracts.
 *
 * <p>Generated patches are stamped chunk by chunk by world generation; their entry remembers which
 * of the patch's chunks are stamped ({@link Entry#doneMask}, one bit per chunk of the patch's
 * bounding box, at most {@value #MAX_CHUNK_SPAN}×{@value #MAX_CHUNK_SPAN}). The total of a patch is
 * the stamped remainder plus, for its chunks not generated yet, the formula amount of every disk
 * cell there ({@link OreAmounts#amountAt}), as Factorio shows a whole patch. Columns that stamping
 * will skip (water, trees, buildings: {@code OreFields.stampColumn}) cannot be known before their
 * chunk generates, so until then they count with their formula amount: the total of a partly
 * generated patch is an upper estimate that becomes exact (drops by the skipped cells) as its
 * chunks generate. Recorded patches (starting, demo) are stamped whole, so all their chunks count
 * as stamped from the start.
 *
 * <p>Plain data, single-threaded; the server wraps it in saved data.
 */
public final class PatchLedger {
    /** Chunks per axis of a patch's bounding box the done mask can hold (8 × 8 = 64 bits). */
    public static final int MAX_CHUNK_SPAN = 8;
    /** A done mask with every chunk stamped. */
    public static final long ALL_DONE = -1L;

    /** One patch: its remaining stamped ore and which of its chunks are stamped. */
    public static final class Entry {
        private final PatchKind kind;
        private final OrePatch patch;
        private final int spawnX;
        private final int spawnZ;
        private long remaining;
        private long doneMask;

        public Entry(PatchKind kind, OrePatch patch, int spawnX, int spawnZ, long remaining, long doneMask) {
            this.kind = kind;
            this.patch = patch;
            this.spawnX = spawnX;
            this.spawnZ = spawnZ;
            this.remaining = remaining;
            this.doneMask = doneMask;
        }

        public PatchKind kind() { return kind; }

        public OrePatch patch() { return patch; }

        /** Spawn the formula amounts are measured from ({@link OreAmounts#amountAt}). */
        public int spawnX() { return spawnX; }

        public int spawnZ() { return spawnZ; }

        /** Ore left in the stamped blocks. */
        public long remaining() { return remaining; }

        /** Bit {@link #chunkBit} set per stamped chunk. */
        public long doneMask() { return doneMask; }

        public long key() { return PatchLedger.key(kind, patch); }

        /** Stamped remainder plus the formula amount of the cells in chunks not stamped yet; never negative. */
        public long total() {
            return Math.max(0, remaining + pending(patch, doneMask, spawnX, spawnZ));
        }
    }

    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();

    /**
     * Key of a patch: kind code (2 bits), then its center x and z (31 bits each, two's complement;
     * world coordinates stay far inside). Centers are unique per kind: one generated patch per region,
     * starting patches 48 apart, demo fields at fixed showcase offsets. Never 0.
     */
    public static long key(PatchKind kind, OrePatch p) {
        return ((long) kind.code() << 62) | ((p.centerX() & 0x7FFFFFFFL) << 31) | (p.centerZ() & 0x7FFFFFFFL);
    }

    /** First chunk x of the patch's bounding box. */
    public static int minChunkX(OrePatch p) { return (p.centerX() - p.radius()) >> 4; }

    public static int minChunkZ(OrePatch p) { return (p.centerZ() - p.radius()) >> 4; }

    /** Bit of chunk (chunkX, chunkZ) in a done mask of {@code p}, or -1 outside the maskable span. */
    public static int chunkBit(OrePatch p, int chunkX, int chunkZ) {
        int dx = chunkX - minChunkX(p), dz = chunkZ - minChunkZ(p);
        if (dx < 0 || dz < 0 || dx >= MAX_CHUNK_SPAN || dz >= MAX_CHUNK_SPAN) return -1;
        return dx + dz * MAX_CHUNK_SPAN;
    }

    /**
     * Formula amount of the disk cells of {@code p} in chunks whose bit in {@code doneMask} is not
     * set (chunks beyond the maskable span count as stamped).
     */
    public static long pending(OrePatch p, long doneMask, int spawnX, int spawnZ) {
        if (doneMask == ALL_DONE) return 0;
        long sum = 0;
        int r = p.radius();
        for (int x = p.centerX() - r; x <= p.centerX() + r; x++) {
            for (int z = p.centerZ() - r; z <= p.centerZ() + r; z++) {
                if (!p.contains(x, z)) continue;
                int bit = chunkBit(p, x >> 4, z >> 4);
                if (bit >= 0 && (doneMask & (1L << bit)) == 0) sum += OreAmounts.amountAt(p, x, z, spawnX, spawnZ);
            }
        }
        return sum;
    }

    /** Formula amount of the whole disk of {@code p}. */
    public static long formulaTotal(OrePatch p, int spawnX, int spawnZ) {
        return pending(p, 0, spawnX, spawnZ);
    }

    /**
     * Records a patch stamped whole (starting, demo), latest last (it wins in {@link #recorded}). An
     * existing entry keeps its remainder (its blocks are still in the world; restamping replaces them
     * block by block, each replacement subtracting the old amount). Returns the key.
     */
    public long register(PatchKind kind, OrePatch p, int spawnX, int spawnZ) {
        long k = key(kind, p);
        Entry old = entries.remove(k);
        entries.put(k, new Entry(kind, p, spawnX, spawnZ, old == null ? 0 : old.remaining, ALL_DONE));
        return k;
    }

    /**
     * World generation stamped chunk (chunkX, chunkZ) of generated patch {@code p}: {@code amount} ore
     * went into its blocks there (0 if every column was skipped). Creates the entry on first use.
     */
    public void chunkStamped(OrePatch p, int chunkX, int chunkZ, long amount) {
        Entry e = entries.computeIfAbsent(key(PatchKind.GENERATED, p), k -> new Entry(PatchKind.GENERATED, p, 0, 0, 0, 0));
        e.remaining += amount;
        int bit = chunkBit(p, chunkX, chunkZ);
        if (bit >= 0) e.doneMask |= 1L << bit;
    }

    /**
     * Adds {@code delta} (negative: ore taken or blocks removed) to the patch with this key; false if
     * there is no such entry.
     */
    public boolean add(long key, long delta) {
        Entry e = entries.get(key);
        if (e == null) return false;
        e.remaining += delta;
        return true;
    }

    @Nullable
    public Entry entry(long key) { return entries.get(key); }

    /** Total of the patch with this key, or -1 without an entry. */
    public long total(long key) {
        Entry e = entries.get(key);
        return e == null ? -1 : e.total();
    }

    @Nullable
    public Entry remove(long key) { return entries.remove(key); }

    /** Every entry: generated ones by first stamp, recorded ones by latest registration. */
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }

    /** Recorded (non-generated) patches, oldest registration first: the order {@link PatchLookup#find} expects. */
    public List<PatchLookup.Ref> recorded() {
        List<PatchLookup.Ref> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.kind != PatchKind.GENERATED) out.add(new PatchLookup.Ref(e.kind, e.patch));
        return out;
    }

    /** Restores an entry as saved (call in saved order). */
    public void put(Entry e) { entries.put(e.key(), e); }

    public int size() { return entries.size(); }
}
