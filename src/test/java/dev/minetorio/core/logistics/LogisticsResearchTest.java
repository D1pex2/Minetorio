package dev.minetorio.core.logistics;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.data.Phase1Data;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Factorio 1.1 logistic technologies (temporary red + green packs) and their locked recipes. */
class LogisticsResearchTest {
    @Test
    void logisticRoboticsSystemAndPersonalRoboport() {
        var tree = Phase1Data.techTree();
        var robotics = tree.get("logistic_robotics").orElseThrow();
        assertEquals(List.of("logistic_robot", "passive_provider_chest", "storage_chest"), robotics.unlocksRecipes());
        assertEquals(List.of("construction_robotics"), robotics.prerequisites());
        assertEquals(250, robotics.units());
        var system = tree.get("logistic_system").orElseThrow();
        assertEquals(List.of("active_provider_chest", "requester_chest", "buffer_chest"), system.unlocksRecipes());
        assertEquals(List.of("logistic_robotics"), system.prerequisites());
        assertEquals(500, system.units());
        var personal = tree.get("personal_roboport").orElseThrow();
        assertEquals(List.of("personal_roboport"), personal.unlocksRecipes());
        assertEquals(50, personal.units());
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
