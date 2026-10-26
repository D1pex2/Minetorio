package dev.minetorio.content.research;

import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.RecipeCategory;
import dev.minetorio.core.research.ResearchState;
import net.minecraft.server.MinecraftServer;

/**
 * The single place that decides whether a recipe may be selected (assembler recipe choice, later
 * hand crafting). Every recipe-selection path asks here. Smelting recipes are always unlocked;
 * crafting recipes are unlocked at start ({@link Phase1Data#startRecipes}) or by research.
 */
public final class RecipeLocks {
    private RecipeLocks() {}

    /** Whether {@code recipeId} is unlocked on {@code server} (the world's {@link ResearchSavedData}). */
    public static boolean isUnlocked(MinecraftServer server, String recipeId) {
        return isUnlocked(ResearchSavedData.get(server).state(), recipeId);
    }

    /**
     * Client-side view used only to decide what the recipe list shows ({@link ClientResearch}); the
     * server re-checks with {@link #isUnlocked(MinecraftServer, String)} on every selection.
     */
    public static boolean isUnlockedOnClient(String recipeId) {
        return isUnlocked(ClientResearch.state(), recipeId);
    }

    private static boolean isUnlocked(ResearchState state, String recipeId) {
        boolean smelting = Phase1Data.recipes().get(recipeId).map(r -> r.category() == RecipeCategory.SMELTING).orElse(false);
        return smelting || state.isRecipeUnlocked(recipeId);
    }
}
