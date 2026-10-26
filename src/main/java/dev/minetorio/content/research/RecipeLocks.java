package dev.minetorio.content.research;

import net.minecraft.server.MinecraftServer;

/**
 * The single place that decides whether a recipe may be selected (assembler recipe choice, later
 * hand crafting). Every recipe-selection path asks here. Stub until research lands (Task 16): every
 * recipe counts as unlocked.
 */
public final class RecipeLocks {
    private RecipeLocks() {}

    /** Whether {@code recipeId} is unlocked on {@code server}. */
    public static boolean isUnlocked(MinecraftServer server, String recipeId) {
        return true;
    }

    /**
     * Client-side view used only to decide what the recipe list shows; the server re-checks with
     * {@link #isUnlocked} on every selection. Stub: everything (Task 16 syncs the research state).
     */
    public static boolean isUnlockedOnClient(String recipeId) {
        return true;
    }
}
