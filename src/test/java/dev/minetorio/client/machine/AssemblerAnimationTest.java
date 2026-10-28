package dev.minetorio.client.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The assembler's work loop: phase 0 is the rest pose (what a ghost shows), the press head plunges at
 * both ends of the arm's swing, the lamp is dark while idle and tier 1's blinks; every part model exists.
 */
class AssemblerAnimationTest {
    private static final Path MODELS = Paths.get(System.getProperty("minetorio.projectDir", "."),
            "src/main/resources/assets/minetorio/models");

    @Test
    void headRestsAtPhaseZeroAndPlungesAtTheSwingEnds() {
        assertEquals(0, AssemblerRenderer.headDrop(0), 1e-6);
        assertEquals(0, AssemblerRenderer.headDrop(0.5f), 1e-6);
        assertEquals(AssemblerRenderer.HEAD_DROP_PX, AssemblerRenderer.headDrop(0.25f), 1e-5);
        assertEquals(AssemblerRenderer.HEAD_DROP_PX, AssemblerRenderer.headDrop(0.75f), 1e-5);
        for (float p = -0.05f; p < 1; p += 0.01f) {
            float d = AssemblerRenderer.headDrop(p);
            assertTrue(d >= -1e-6 && d <= AssemblerRenderer.HEAD_DROP_PX + 1e-6, "drop " + d + " at " + p);
        }
    }

    @Test
    void lampIsDarkWhileIdleAndTierOneBlinks() {
        for (float p : new float[] {0, 0.1f, 0.3f, 0.6f, 0.9f}) {
            assertFalse(AssemblerRenderer.lampLit(false, p, false));
            assertFalse(AssemblerRenderer.lampLit(true, p, false));
            assertTrue(AssemblerRenderer.lampLit(true, p, true));
        }
        assertTrue(AssemblerRenderer.lampLit(false, 0.1f, true));
        assertFalse(AssemblerRenderer.lampLit(false, 0.3f, true));
        assertTrue(AssemblerRenderer.lampLit(false, 0.6f, true));
        assertFalse(AssemblerRenderer.lampLit(false, 0.8f, true));
        assertTrue(AssemblerRenderer.lampLit(false, -0.01f, true) == AssemblerRenderer.lampLit(false, 0.99f, true));
    }

    @Test
    void everyPartModelExists() {
        for (ModelResourceLocation m : AssemblerRenderer.MODELS) {
            assertTrue(Files.exists(MODELS.resolve(m.id().getPath() + ".json")), m.toString());
        }
    }
}
