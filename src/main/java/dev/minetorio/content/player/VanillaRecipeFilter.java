package dev.minetorio.content.player;

import dev.minetorio.Minetorio;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * No vanilla crafting: while {@link MinetorioGameRules#VANILLA_RECIPES} is false the server's recipe
 * manager holds only recipes in the {@code minetorio} namespace (the mod's own crafting runs on its
 * recipe data, not on the recipe manager). This empties every vanilla recipe type at once: crafting
 * table and 2×2 grid, furnace/smoker/blast furnace, campfire, stonecutter, smithing, special recipes.
 * Applied when the server starts, after every datapack reload (before recipes are sent to clients),
 * and when the rule changes. The removed recipes are kept so the rule can be switched back on without
 * a reload.
 */
public final class VanillaRecipeFilter {
    /** The full recipe list of {@link #backupOwner}, taken before filtering. */
    private static List<RecipeHolder<?>> backup;
    private static RecipeManager backupOwner;

    private VanillaRecipeFilter() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> apply(e.getServer(), false));
        // Fired after a reload (player == null) and on each login, both before the recipe packet.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> apply(e.getPlayerList().getServer(), false));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            backup = null;
            backupOwner = null;
        });
    }

    /** Brings the recipe manager in line with the game rule and resends recipes to players if it changed. */
    public static void apply(MinecraftServer server) {
        apply(server, true);
    }

    private static synchronized void apply(MinecraftServer server, boolean resend) {
        RecipeManager manager = server.getRecipeManager();
        boolean changed = false;
        if (!MinetorioGameRules.vanillaRecipes(server)) {
            List<RecipeHolder<?>> all = new ArrayList<>(manager.getRecipes());
            List<RecipeHolder<?>> kept = new ArrayList<>();
            for (RecipeHolder<?> r : all) {
                if (r.id().getNamespace().equals(Minetorio.MODID)) kept.add(r);
            }
            if (kept.size() != all.size()) {
                if (backupOwner != manager) {
                    backup = all;
                    backupOwner = manager;
                }
                manager.replaceRecipes(kept);
                changed = true;
            }
        } else if (backupOwner == manager && backup != null) {
            manager.replaceRecipes(backup);
            backup = null;
            backupOwner = null;
            changed = true;
        }
        if (changed && resend && server.getPlayerList() != null) {
            server.getPlayerList().broadcastAll(new ClientboundUpdateRecipesPacket(manager.getOrderedRecipes()));
        }
    }
}
