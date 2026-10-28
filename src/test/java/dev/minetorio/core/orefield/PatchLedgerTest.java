package dev.minetorio.core.orefield;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PatchLedgerTest {
    private final PatchGenerator gen = new PatchGenerator(12345L, 0, 0);

    private OrePatch someGenerated() {
        for (int rx = 10; ; rx++) {
            OrePatch p = gen.inRegion(rx, 7);
            if (p != null) return p;
        }
    }

    private static long planSum(OrePatch p, int minX, int minZ, int maxX, int maxZ, int sx, int sz) {
        long s = 0;
        for (FieldStamp.Cell c : FieldStamp.plan(List.of(p), minX, minZ, maxX, maxZ, sx, sz)) s += c.amount();
        return s;
    }

    // --- lookup ------------------------------------------------------------------------------------

    @Test void lookupFindsTheGeneratedPatchAtCenterAndEdge() {
        OrePatch p = someGenerated();
        PatchLookup.Ref center = PatchLookup.find(p.centerX(), p.centerZ(), p.type(), List.of(), gen, null);
        assertNotNull(center);
        assertEquals(p, center.patch());
        assertEquals(PatchKind.GENERATED, center.kind());
        int ex = p.centerX() + p.radius();
        PatchLookup.Ref edge = PatchLookup.find(ex, p.centerZ(), p.type(), List.of(), gen, null);
        assertNotNull(edge, "edge cell");
        assertEquals(p, edge.patch());
        assertNull(PatchLookup.find(ex + 1, p.centerZ(), p.type(), List.of(), gen, null), "just outside");
    }

    @Test void lookupNeedsTheBlocksType() {
        OrePatch p = someGenerated();
        OreType other = p.type() == OreType.IRON ? OreType.COAL : OreType.IRON;
        assertNull(PatchLookup.find(p.centerX(), p.centerZ(), other, List.of(), gen, null));
    }

    @Test void recordedPatchesWinLatestFirst() {
        OrePatch p = someGenerated();
        PatchLookup.Ref a = new PatchLookup.Ref(PatchKind.STARTING, new OrePatch(p.type(), p.centerX() + 2, p.centerZ(), 4));
        PatchLookup.Ref b = new PatchLookup.Ref(PatchKind.DEMO, new OrePatch(p.type(), p.centerX() + 4, p.centerZ(), 4));
        assertEquals(b, PatchLookup.find(p.centerX() + 3, p.centerZ(), p.type(), List.of(a, b), gen, null), "the later stamp wins");
        assertEquals(a, PatchLookup.find(p.centerX() + 3, p.centerZ(), p.type(), List.of(b, a), gen, null));
        assertEquals(a, PatchLookup.find(p.centerX() - 1, p.centerZ(), p.type(), List.of(a, b), gen, null), "only a covers it");
        assertEquals(p, PatchLookup.find(p.centerX() - 3, p.centerZ(), p.type(), List.of(a, b), gen, null).patch(), "generated below");
    }

    @Test void generatedPatchesInTheStartingAreaAreGone() {
        OrePatch p = someGenerated();
        int sx = p.centerX() + PatchGenerator.STARTING_AREA_RADIUS + p.radius() - 3, sz = p.centerZ();
        assertNull(PatchLookup.find(p.centerX(), p.centerZ(), p.type(), List.of(), gen, new int[] {sx, sz}));
        assertNotNull(PatchLookup.find(p.centerX(), p.centerZ(), p.type(), List.of(), gen, new int[] {sx + 200, sz}));
    }

    // --- keys --------------------------------------------------------------------------------------

    @Test void keysAreDistinctPerKindAndCenter() {
        Set<Long> keys = new HashSet<>();
        for (PatchKind k : PatchKind.values()) {
            for (int x : new int[] {-30_000_000, -1, 0, 1, 30_000_000}) {
                for (int z : new int[] {-30_000_000, -1, 0, 1, 30_000_000}) {
                    long key = PatchLedger.key(k, new OrePatch(OreType.IRON, x, z, 5));
                    assertNotEquals(0, key);
                    assertTrue(keys.add(key), "duplicate key " + k + " " + x + " " + z);
                }
            }
        }
        assertEquals(PatchLedger.key(PatchKind.DEMO, new OrePatch(OreType.IRON, 3, 4, 5)),
                PatchLedger.key(PatchKind.DEMO, new OrePatch(OreType.COAL, 3, 4, 9)), "the center identifies a patch of a kind");
    }

    // --- totals ------------------------------------------------------------------------------------

    @Test void unstampedGeneratedPatchShowsItsFormulaTotal() {
        OrePatch p = someGenerated();
        int r = p.radius();
        long formula = planSum(p, p.centerX() - r, p.centerZ() - r, p.centerX() + r, p.centerZ() + r, 0, 0);
        assertEquals(formula, PatchLedger.formulaTotal(p, 0, 0));
        assertEquals(0, PatchLedger.pending(p, PatchLedger.ALL_DONE, 0, 0));
    }

    @Test void stampingChunkByChunkKeepsTheTotal() {
        OrePatch p = someGenerated();
        PatchLedger ledger = new PatchLedger();
        long formula = PatchLedger.formulaTotal(p, 0, 0);
        long key = PatchLedger.key(PatchKind.GENERATED, p);
        int r = p.radius();
        for (int cx = (p.centerX() - r) >> 4; cx <= (p.centerX() + r) >> 4; cx++) {
            for (int cz = (p.centerZ() - r) >> 4; cz <= (p.centerZ() + r) >> 4; cz++) {
                long sum = planSum(p, cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15, 0, 0);
                ledger.chunkStamped(p, cx, cz, sum);
                assertEquals(formula, ledger.total(key), "after chunk " + cx + "," + cz);
            }
        }
        assertEquals(formula, ledger.entry(key).remaining(), "fully stamped: all in the remainder");
        assertEquals(0, PatchLedger.pending(p, ledger.entry(key).doneMask(), 0, 0));
    }

    @Test void skippedCellsDropOutWhenTheirChunkGenerates() {
        OrePatch p = new OrePatch(OreType.IRON, 8, 8, 6); // one chunk: 0,0
        PatchLedger ledger = new PatchLedger();
        long formula = PatchLedger.formulaTotal(p, 0, 0);
        long key = PatchLedger.key(PatchKind.GENERATED, p);
        long water = OreAmounts.amountAt(p, 8, 8, 0, 0);
        ledger.chunkStamped(p, 0, 0, formula - water); // the center column was water
        assertEquals(formula - water, ledger.total(key));
    }

    @Test void miningAndRemovalSubtract() {
        PatchLedger ledger = new PatchLedger();
        OrePatch demo = new OrePatch(OreType.COAL, 100, 100, 3);
        long key = ledger.register(PatchKind.DEMO, demo, 0, 0);
        int cells = PatchShape.of(demo).cells();
        assertTrue(ledger.add(key, 10_000L * cells));
        assertEquals(10_000L * cells, ledger.total(key));
        ledger.add(key, -1);
        ledger.add(key, -9_999);
        assertEquals(10_000L * (cells - 1), ledger.total(key));
        assertFalse(ledger.add(12345L, -1), "unknown patch");
        ledger.add(key, -10_000L * cells);
        assertEquals(0, ledger.total(key), "never negative");
        assertNotNull(ledger.remove(key));
        assertEquals(-1, ledger.total(key));
    }

    @Test void reregisteringKeepsTheRemainderAndMovesToTheEnd() {
        PatchLedger ledger = new PatchLedger();
        OrePatch a = new OrePatch(OreType.IRON, 0, 0, 3), b = new OrePatch(OreType.IRON, 2, 0, 3);
        long ka = ledger.register(PatchKind.DEMO, a, 0, 0);
        ledger.register(PatchKind.STARTING, b, 0, 0);
        ledger.add(ka, 50);
        ledger.register(PatchKind.DEMO, a, 0, 0);
        assertEquals(50, ledger.total(ka));
        assertEquals(List.of(new PatchLookup.Ref(PatchKind.STARTING, b), new PatchLookup.Ref(PatchKind.DEMO, a)), ledger.recorded());
    }

    @Test void chunkBitsCoverTheBoundingBox() {
        OrePatch p = new OrePatch(OreType.IRON, -17, 31, 14);
        Set<Integer> bits = new HashSet<>();
        for (int x = p.centerX() - 14; x <= p.centerX() + 14; x++) {
            for (int z = p.centerZ() - 14; z <= p.centerZ() + 14; z++) {
                int bit = PatchLedger.chunkBit(p, x >> 4, z >> 4);
                assertTrue(bit >= 0 && bit < 64);
                bits.add(bit);
            }
        }
        assertTrue(bits.size() <= 9);
        assertEquals(-1, PatchLedger.chunkBit(p, PatchLedger.minChunkX(p) - 1, PatchLedger.minChunkZ(p)));
    }

    // --- shape and format --------------------------------------------------------------------------

    @Test void shapeRunsCoverTheDiskAndEdgesTheBorder() {
        OrePatch p = new OrePatch(OreType.STONE, 5, -5, 4);
        PatchShape s = PatchShape.of(p);
        int n = 0;
        for (int x = 0; x <= 10; x++) for (int z = -10; z <= 0; z++) if (p.contains(x, z)) n++;
        assertEquals(n, s.cells());
        for (PatchShape.Run r : s.runs()) {
            for (int x = r.x0(); x <= r.x1(); x++) assertTrue(p.contains(x, r.z()));
            assertFalse(p.contains(r.x0() - 1, r.z()));
            assertFalse(p.contains(r.x1() + 1, r.z()));
        }
        for (PatchShape.Edge e : s.edges()) {
            assertTrue(p.contains(e.x(), e.z()));
            int ox = e.x() + (e.side() == PatchShape.WEST ? -1 : e.side() == PatchShape.EAST ? 1 : 0);
            int oz = e.z() + (e.side() == PatchShape.NORTH ? -1 : e.side() == PatchShape.SOUTH ? 1 : 0);
            assertFalse(p.contains(ox, oz));
        }
        assertTrue(s.edges().contains(new PatchShape.Edge(9, -5, PatchShape.EAST)));
        assertTrue(s.edges().contains(new PatchShape.Edge(5, -9, PatchShape.NORTH)));
        assertFalse(s.edges().stream().anyMatch(e -> e.x() == 5 && e.z() == -5), "no edge inside");
    }

    @Test void compactAmounts() {
        assertEquals("0", AmountFormat.compact(0));
        assertEquals("0", AmountFormat.compact(-5));
        assertEquals("999", AmountFormat.compact(999));
        assertEquals("1k", AmountFormat.compact(1000));
        assertEquals("1.5k", AmountFormat.compact(1550));
        assertEquals("9.9k", AmountFormat.compact(9999));
        assertEquals("10k", AmountFormat.compact(10_000));
        assertEquals("190k", AmountFormat.compact(190_400));
        assertEquals("999k", AmountFormat.compact(999_999));
        assertEquals("1M", AmountFormat.compact(1_000_000));
        assertEquals("3.2M", AmountFormat.compact(3_250_000));
        assertEquals("45M", AmountFormat.compact(45_600_000));
        assertEquals("1.2G", AmountFormat.compact(1_234_567_890));
        assertEquals("9.2E", AmountFormat.compact(Long.MAX_VALUE));
    }
}
