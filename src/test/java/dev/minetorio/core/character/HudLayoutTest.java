package dev.minetorio.core.character;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    @Test void barsAndQueueStayAboveAllVisibleRowsAtDifferentSizes() {
        for (int width : new int[]{120, 190, 854}) for (int height : new int[]{96, 140, 480})
            for (int rows = 1; rows <= 4; rows++) {
                var layout = HudLayout.of(width, height, rows);
                assertTrue(layout.scale() > 0 && layout.scale() <= 1);
                assertTrue(layout.left() >= 0 && layout.left() + 180 * layout.scale() <= width + 0.01f);
                assertTrue(layout.nameTop() >= -0.01f);
                if (width >= 196 && height > 50) assertTrue(layout.nameTop() >= 47.99f);
                assertTrue(layout.nameTop() + 10 * layout.scale() <= layout.queueTop() + 0.01f);
                assertTrue(layout.queueTop() + 18 * layout.scale() <= layout.vitalsTop() + 0.01f);
                assertTrue(layout.vitalsTop() + 20 * layout.scale() <= layout.hotbarTop() + 0.01f);
                assertTrue(layout.hotbarTop() + rows * 20 * layout.scale() <= height + 0.01f);
            }
    }

    @Test void healthNumbersRepresentActualMinecraftHealthOnFactorioScale() {
        assertEquals(250, HudLayout.factorioHealth(20));
        assertEquals(125, HudLayout.factorioHealth(10));
        assertEquals(500, HudLayout.factorioHealth(40));
        assertEquals(0, HudLayout.factorioHealth(0));
    }
}
