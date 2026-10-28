package dev.minetorio.client.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minetorio.client.machine.DrillRenderer;
import dev.minetorio.client.research.LabRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The steam engine's crank train keeps its rod rigid and rests at angle 0 (what a ghost shows), the valve
 * stems stay inside their stroke, and every model the reworked machine renderers draw exists.
 */
class MachineAnimationTest {
    private static final Path MODELS = Paths.get(System.getProperty("minetorio.projectDir", "."),
            "src/main/resources/assets/minetorio/models");

    @Test
    void connectingRodStaysRigidAndRestsAtAngleZero() {
        assertEquals(0, SteamMachineRenderer.Engine.rodAngle(0), 1e-5);
        assertEquals(0, SteamMachineRenderer.Engine.crossheadDrop(0), 1e-5);
        assertEquals(-2 * SteamMachineRenderer.Engine.CRANK_R, SteamMachineRenderer.Engine.crossheadDrop(180), 1e-4);
        float restTop = SteamMachineRenderer.Engine.CRANK_Y + SteamMachineRenderer.Engine.CRANK_R + SteamMachineRenderer.Engine.ROD_L;
        for (float a = 0; a < 360; a += 7.5f) {
            float[] pin = SteamMachineRenderer.Engine.crankPin(a);
            double phi = Math.toRadians(SteamMachineRenderer.Engine.rodAngle(a));
            // the rod's top end (pin + L turned by phi about +x) is on the guide line at the crosshead's height
            double topY = pin[0] + SteamMachineRenderer.Engine.ROD_L * Math.cos(phi);
            double topZ = pin[1] + SteamMachineRenderer.Engine.ROD_L * Math.sin(phi);
            assertEquals(SteamMachineRenderer.Engine.CRANK_Z, topZ, 1e-4, "rod top z at " + a);
            assertEquals(restTop + SteamMachineRenderer.Engine.crossheadDrop(a), topY, 1e-4, "rod top y at " + a);
        }
    }

    @Test
    void valveStemsStayInTheirStroke() {
        assertEquals(0, SteamMachineRenderer.Engine.valveLift(0, 1), 1e-6);
        for (int k = 0; k < 3; k++) {
            for (float a = 0; a < 360; a += 10) {
                float lift = SteamMachineRenderer.Engine.valveLift(a, k);
                assertTrue(lift >= -1e-6 && lift <= SteamMachineRenderer.Engine.VALVE_STROKE_PX + 1e-6);
            }
        }
    }

    @Test
    void everyPartModelExists() {
        List<ModelResourceLocation> all = new ArrayList<>(List.of(SteamMachineRenderer.MODELS));
        all.addAll(List.of(OffshorePumpRenderer.MODELS));
        all.addAll(List.of(DrillRenderer.MODELS));
        all.addAll(List.of(LabRenderer.MODELS));
        for (ModelResourceLocation m : all) {
            Path p = MODELS.resolve(m.id().getPath() + ".json");
            assertTrue(Files.exists(p), "missing " + p);
        }
    }
}
