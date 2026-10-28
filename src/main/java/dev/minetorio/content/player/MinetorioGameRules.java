package dev.minetorio.content.player;

import dev.minetorio.core.player.CharacterRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * World settings of the Factorio player rules, as game rules (per world, editable in the world-creation
 * screen and with {@code /gamerule}).
 */
public final class MinetorioGameRules {
    /** Whether vanilla (non-minetorio) recipes exist. Default false: only the mod's recipes. */
    public static final GameRules.Key<GameRules.BooleanValue> VANILLA_RECIPES = GameRules.register(
            "minetorio:vanillaRecipes", GameRules.Category.MISC,
            GameRules.BooleanValue.create(false, (server, value) -> VanillaRecipeFilter.apply(server)));

    /** Length of a full day-night cycle in real seconds; 0 = vanilla (20 min). Default ≈ Factorio's 416 s. */
    public static final GameRules.Key<GameRules.IntegerValue> DAY_SECONDS = GameRules.register(
            "minetorio:daySeconds", GameRules.Category.UPDATES,
            GameRules.IntegerValue.create(CharacterRules.FACTORIO_DAY_SECONDS, (server, value) -> DayLength.apply(server)));

    /** Minutes of world time a character corpse lasts; 0 = forever. */
    public static final GameRules.Key<GameRules.IntegerValue> CORPSE_MINUTES = GameRules.register(
            "minetorio:corpseMinutes", GameRules.Category.PLAYER,
            GameRules.IntegerValue.create(CharacterRules.CORPSE_MINUTES));

    private MinetorioGameRules() {}

    /** Loads the class (registering the rules); called from the mod constructor. */
    public static void init() {}

    static boolean vanillaRecipes(MinecraftServer server) {
        return server.getGameRules().getBoolean(VANILLA_RECIPES);
    }
}
