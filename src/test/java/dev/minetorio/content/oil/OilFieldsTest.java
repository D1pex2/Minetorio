package dev.minetorio.content.oil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OilFieldsTest {
    @Test void deterministicAndOutsideStart() {
        int count = 0;
        for (int x = -64; x < 64; x++) for (int z = -64; z < 64; z++) {
            var a = OilFields.inChunk(42, x, z);
            assertEquals(a, OilFields.inChunk(42, x, z));
            for (var well : a) {
                assertEquals(x, Math.floorDiv(well.x(), 16));
                assertEquals(z, Math.floorDiv(well.z(), 16));
                count++;
            }
        }
        assertTrue(count > 0);
    }
    @Test void yieldDecaysToNonzeroFloor() {
        assertEquals(2, OilFields.yield(2, 0));
        assertTrue(OilFields.yield(2, 3000) < 2);
        assertEquals(0.4, OilFields.yield(2, Long.MAX_VALUE), 1e-9);
    }
}
