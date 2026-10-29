package dev.minetorio.core.logistics;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.data.Phase1Data;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Factorio 1.1 logistic technologies retain their exact science and future equipment dependencies. */
class LogisticsResearchTest {
    @Test
    void logisticRoboticsSystemAndPersonalRoboport() {
        var tree = Phase1Data.techTree();
        var robotics = tree.get("logistic_robotics").orElseThrow();
        assertEquals(List.of("roboport", "passive_provider_chest", "storage_chest", "logistic_robot"), robotics.unlocksRecipes());
        assertEquals(List.of("robotics"), robotics.prerequisites());
        assertTrue(robotics.packsPerUnit().containsKey("minetorio:chemical_science_pack"));
        assertEquals(250, robotics.units());
        var system = tree.get("logistic_system").orElseThrow();
        assertEquals(List.of("active_provider_chest", "requester_chest", "buffer_chest"), system.unlocksRecipes());
        assertEquals(List.of("utility_science_pack", "logistic_robotics"), system.prerequisites());
        assertTrue(system.packsPerUnit().containsKey("minetorio:utility_science_pack"));
        assertEquals(500, system.units());
        var personal = tree.get("personal_roboport").orElseThrow();
        assertEquals(List.of("personal_roboport"), personal.unlocksRecipes());
        assertEquals(50, personal.units());
        assertEquals(List.of("construction_robotics", "solar_panel_equipment"),personal.prerequisites());
    }

    @Test
    void logisticRecipesExistAndAreLockedAtStart() {
        for (String id : List.of("logistic_robot", "passive_provider_chest", "active_provider_chest", "storage_chest",
                "requester_chest", "buffer_chest", "personal_roboport")) {
            assertTrue(Phase1Data.recipes().get(id).isPresent(), id);
            assertFalse(Phase1Data.startRecipes().contains(id), id + " unlocked at start");
        }
    }
}
