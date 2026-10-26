package dev.minetorio.client.handcraft;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandcraftGroupsTest {
    @Test
    void everyCraftingRecipeHasAnExplicitGroup() {
        for (Recipe r : Phase1Data.recipes().all()) {
            if (r.category() != RecipeCategory.CRAFTING) continue;
            assertTrue(HandcraftGroups.isMapped(r.id()), r.id() + " has no group");
        }
    }

    @Test
    void groupsFollowFactorio() {
        assertEquals(HandcraftGroups.Group.LOGISTICS, HandcraftGroups.of("transport_belt"));
        assertEquals(HandcraftGroups.Group.LOGISTICS, HandcraftGroups.of("inserter"));
        assertEquals(HandcraftGroups.Group.PRODUCTION, HandcraftGroups.of("assembling_machine_1"));
        assertEquals(HandcraftGroups.Group.INTERMEDIATES, HandcraftGroups.of("iron_gear_wheel"));
        assertEquals(HandcraftGroups.Group.SCIENCE, HandcraftGroups.of("automation_science_pack"));
        assertEquals(HandcraftGroups.Group.INTERMEDIATES, HandcraftGroups.of("unknown_recipe"));
    }

    @Test
    void recipesOfAGroupKeepBookOrder() {
        List<String> science = HandcraftGroups.recipes(HandcraftGroups.Group.SCIENCE, Phase1Data.recipes(), id -> true);
        assertEquals(List.of("automation_science_pack", "logistic_science_pack"), science);
        List<String> unlockedScience = HandcraftGroups.recipes(HandcraftGroups.Group.SCIENCE, Phase1Data.recipes(),
                id -> !id.equals("logistic_science_pack"));
        assertEquals(List.of("automation_science_pack"), unlockedScience);
        assertFalse(HandcraftGroups.recipes(HandcraftGroups.Group.PRODUCTION, Phase1Data.recipes(), id -> true)
                .contains("iron_plate"), "smelting recipe listed");
    }
}
