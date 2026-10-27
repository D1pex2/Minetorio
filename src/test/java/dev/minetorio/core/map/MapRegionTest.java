package dev.minetorio.core.map;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MapRegionTest {
    private static byte[] filled(int value) {
        byte[] b = new byte[MapPalette.COLUMNS];
        Arrays.fill(b, (byte) value);
        return b;
    }

    private static byte[] noise(Random r) {
        byte[] b = new byte[MapPalette.COLUMNS];
        r.nextBytes(b);
        return b;
    }

    @Test
    void chunkKeysRoundTripAndMapToRegions() {
        for (int[] c : new int[][] {{0, 0}, {-1, -1}, {31, 32}, {-33, 5}, {1_874_999, -1_874_999}}) {
            long k = ChunkKeys.of(c[0], c[1]);
            assertEquals(c[0], ChunkKeys.x(k));
            assertEquals(c[1], ChunkKeys.z(k));
            long region = ChunkKeys.region(k);
            assertEquals(Math.floorDiv(c[0], 32), ChunkKeys.x(region));
            assertEquals(Math.floorDiv(c[1], 32), ChunkKeys.z(region));
            int local = ChunkKeys.local(k);
            assertTrue(local >= 0 && local < MapRegion.CHUNKS);
            assertEquals(k, ChunkKeys.fromLocal(region, local));
        }
    }

    @Test
    void putReportsChangesAndMarksDirty() {
        MapRegion r = new MapRegion();
        assertFalse(r.isDirty());
        assertTrue(r.put(5, filled(7)));
        assertTrue(r.isDirty());
        r.markClean();
        assertFalse(r.put(5, filled(7)), "same colors are no change");
        assertFalse(r.isDirty());
        assertTrue(r.put(5, filled(8)));
        assertEquals(1, r.count());
        assertArrayEquals(filled(8), r.get(5));
        assertNull(r.get(6));
    }

    @Test
    void putCopiesTheArray() {
        MapRegion r = new MapRegion();
        byte[] colors = filled(3);
        r.put(0, colors);
        colors[0] = 9;
        assertEquals(3, r.get(0)[0]);
    }

    @Test
    void encodeDecodeRoundTrip() throws IOException {
        Random rnd = new Random(1);
        MapRegion r = new MapRegion();
        r.put(0, filled(4));
        r.put(1023, noise(rnd));
        r.put(517, noise(rnd));
        MapRegion back = MapRegion.decode(r.encode());
        assertEquals(3, back.count());
        for (int i : new int[] {0, 1023, 517}) assertArrayEquals(r.get(i), back.get(i));
        assertFalse(back.isDirty());
    }

    @Test
    void uniformRegionCompressesWell() {
        MapRegion r = new MapRegion();
        for (int i = 0; i < MapRegion.CHUNKS; i++) r.put(i, filled(29));
        int raw = MapRegion.CHUNKS * (MapPalette.COLUMNS + 2);
        assertTrue(r.encode().length < raw / 50, "compressed " + r.encode().length + " of " + raw);
    }

    @Test
    void decodeRejectsGarbage() {
        assertThrows(IOException.class, () -> MapRegion.decode(new byte[] {1, 2, 3, 4, 5}));
        byte[] bomb = MapCompression.deflate(new byte[MapRegion.MAX_ENCODED_RAW + 1]);
        assertThrows(IOException.class, () -> MapRegion.decode(bomb));
    }

    @Test
    void batchRoundTripAndSizeIsBounded() throws IOException {
        Random rnd = new Random(2);
        List<MapChunkBatch.Entry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < MapChunkBatch.MAX_CHUNKS; i++) entries.add(new MapChunkBatch.Entry(ChunkKeys.of(i, -i), noise(rnd)));
        byte[] data = MapChunkBatch.encode(entries);
        assertTrue(data.length <= MapChunkBatch.MAX_BYTES, "worst case " + data.length + " > " + MapChunkBatch.MAX_BYTES);
        List<MapChunkBatch.Entry> back = MapChunkBatch.decode(data);
        assertEquals(entries.size(), back.size());
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(entries.get(i).key(), back.get(i).key());
            assertArrayEquals(entries.get(i).colors(), back.get(i).colors());
        }
    }

    @Test
    void batchRefusesTooManyChunks() {
        List<MapChunkBatch.Entry> entries = new java.util.ArrayList<>();
        for (int i = 0; i <= MapChunkBatch.MAX_CHUNKS; i++) entries.add(new MapChunkBatch.Entry(i, filled(1)));
        assertThrows(IllegalArgumentException.class, () -> MapChunkBatch.encode(entries));
    }

    @Test
    void typicalTerrainBatchIsSmall() {
        // terrain-like data: few distinct colors in runs
        Random rnd = new Random(3);
        List<MapChunkBatch.Entry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < MapChunkBatch.MAX_CHUNKS; i++) {
            byte[] b = new byte[MapPalette.COLUMNS];
            for (int j = 0; j < b.length; j++) b[j] = (byte) (4 + 4 * (rnd.nextInt(8) == 0 ? 2 : 1) + rnd.nextInt(3));
            entries.add(new MapChunkBatch.Entry(i, b));
        }
        assertTrue(MapChunkBatch.encode(entries).length < 128 * 256 / 2);
    }
}
