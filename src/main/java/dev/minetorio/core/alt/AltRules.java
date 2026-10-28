package dev.minetorio.core.alt;

import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;
import dev.minetorio.core.research.Technology;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the alt-mode icon and status of each machine kind from plain facts (pure, unit tested).
 *
 * <p>Status priority, as in Factorio: a missing setup (no recipe, no research, no ore) first, then
 * a missing energy source (no power, no fuel), then a blocked output, then low power.
 */
public final class AltRules {
    /** Electric satisfaction below this (but above 0) shows {@link AltStatus#LOW_POWER}. */
    public static final double LOW_POWER_BELOW = 0.95;
    /** The science pack shown by a lab whose research has no item icon. */
    public static final String DEFAULT_LAB_ICON = "minetorio:automation_science_pack";

    private AltRules() {}

    /** Status of an electric consumer from the satisfaction the grid last supplied (0 also outside every pole's area). */
    public static AltStatus electric(double satisfaction) {
        if (satisfaction <= 0) return AltStatus.NO_POWER;
        return satisfaction < LOW_POWER_BELOW ? AltStatus.LOW_POWER : AltStatus.NONE;
    }

    /** Status of a burner: no fuel items left and nothing burning. */
    public static AltStatus burner(boolean hasFuel) {
        return hasFuel ? AltStatus.NONE : AltStatus.NO_FUEL;
    }

    /** Whether a burner holding {@code fuel} items with {@code remainingJ} of the current item left has anything to burn. */
    public static boolean hasFuel(Map<String, Integer> fuel, long remainingJ) {
        if (remainingJ > 0) return true;
        for (int n : fuel.values()) if (n > 0) return true;
        return false;
    }

    /** Merges an energy status with a blocked output: energy errors win, output full beats low power. */
    private static AltStatus withOutput(AltStatus energy, boolean outputFull) {
        if (energy.severity() == AltStatus.Severity.ERROR) return energy;
        if (outputFull) return AltStatus.OUTPUT_FULL;
        return energy;
    }

    /**
     * Assembler: the recipe's first output; no recipe → {@link AltStatus#NO_RECIPE}.
     *
     * @param recipeOutput first output of the selected recipe, or null without a recipe
     */
    public static AltInfo assembler(String recipeOutput, boolean outputFull, AltStatus energy) {
        if (recipeOutput == null) return AltInfo.of(null, AltStatus.NO_RECIPE);
        return AltInfo.of(recipeOutput, withOutput(energy, outputFull));
    }

    /**
     * Furnace: the product of the smelt (current recipe), else the item waiting in the output, else
     * no icon; fuel and output warnings as for any burner machine.
     */
    public static AltInfo furnace(String recipeOutput, String storedOutput, boolean outputFull, AltStatus energy) {
        String icon = recipeOutput != null ? recipeOutput : storedOutput;
        return AltInfo.of(icon, withOutput(energy, outputFull));
    }

    /**
     * Mining drill: the ore of its field; no field block under the area → {@link AltStatus#NO_ORE}
     * (with the ore still in the output buffer as icon, if any).
     *
     * @param ore           item of the field block being mined, or null if there is none
     * @param bufferedOre   item stuck in the output buffer, or null
     */
    public static AltInfo drill(String ore, String bufferedOre, AltStatus energy) {
        boolean blocked = bufferedOre != null;
        if (ore == null && !blocked) return AltInfo.of(null, AltStatus.NO_ORE);
        return AltInfo.of(ore != null ? ore : bufferedOre, withOutput(energy, blocked));
    }

    /** Lab: status only (the icon comes from the client's research, see {@link #labIcon}). */
    public static AltInfo lab(boolean hasResearch, AltStatus energy) {
        return AltInfo.of(null, hasResearch ? energy : AltStatus.NO_RESEARCH);
    }

    /** Boiler: the fuel it burns as icon, {@link AltStatus#NO_FUEL} without fuel. */
    public static AltInfo boiler(String fuelItem, boolean hasFuel) {
        return AltInfo.of(hasFuel ? fuelItem : null, burner(hasFuel));
    }

    /**
     * Solar panel and accumulator: no icon; {@link AltStatus#NO_POWER} (Factorio's "not connected")
     * while no pole's supply area reaches them.
     */
    public static AltInfo gridMember(boolean connected) {
        return AltInfo.of(null, connected ? AltStatus.NONE : AltStatus.NO_POWER);
    }

    /** Status-only machines (radar). */
    public static AltInfo statusOnly(AltStatus energy) { return AltInfo.of(null, energy); }

    /**
     * Inserter, from its synced arm state: a hand in motion that does not move stalls for want of
     * energy.
     */
    public static AltStatus inserter(boolean moving, boolean working, boolean burner) {
        if (!moving || working) return AltStatus.NONE;
        return burner ? AltStatus.NO_FUEL : AltStatus.NO_POWER;
    }

    /** Icon of a technology: output of its first unlocked recipe that has one, else {@link #DEFAULT_LAB_ICON}. */
    public static String techIcon(Technology tech, RecipeBook recipes) {
        for (String id : tech.unlocksRecipes()) {
            Optional<Recipe> r = recipes.get(id);
            if (r.isPresent() && !r.get().outputs().isEmpty()) return r.get().outputs().get(0).item();
        }
        return DEFAULT_LAB_ICON;
    }

    /** Icon of a lab: the current research's icon; none without research. */
    public static String labIcon(Optional<Technology> current, RecipeBook recipes) {
        return current.map(t -> techIcon(t, recipes)).orElse(null);
    }

    // --- presentation ----------------------------------------------------------------------------

    /** Flash period of status badges, in ticks. */
    public static final int FLASH_PERIOD = 20;
    /** Ticks of each period the badge is lit. */
    public static final int FLASH_ON = 13;

    /** Whether a flashing badge is lit at {@code gameTime} (all badges flash in step, as in Factorio). */
    public static boolean flashOn(long gameTime) {
        return Math.floorMod(gameTime, FLASH_PERIOD) < FLASH_ON;
    }

    /** Distance (blocks) up to which icons keep their base size. */
    public static final double NEAR = 10;
    /** Largest growth factor at a distance. */
    public static final double MAX_GROWTH = 2.2;

    /**
     * World size of an icon at {@code distance} blocks from the camera: the base size near the
     * player, growing with the distance (up to {@link #MAX_GROWTH}×) so it still reads far away.
     */
    public static double iconSize(double base, double distance) {
        double growth = Math.max(1, Math.min(MAX_GROWTH, distance / NEAR));
        return base * growth;
    }
}
