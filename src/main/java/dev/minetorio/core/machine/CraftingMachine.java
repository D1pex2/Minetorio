package dev.minetorio.core.machine;

import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Item crafting machine: pure logic, no power source of its own. The caller passes the power
 * satisfaction (0..1) each tick, e.g. an electric network ratio or a {@link Burner#draw()} result.
 */
public final class CraftingMachine {
    private static final int STACK_LIMIT = 64;
    private static final double EPS = 1e-9;

    private final double speed;
    private final long powerPerTickJ;
    private final RecipeBook smeltingBook; // non-null for smelters that choose their recipe by input

    private Recipe recipe;
    private final Map<String, Integer> inputs = new LinkedHashMap<>();
    private final Map<String, Integer> outputs = new LinkedHashMap<>();
    private final Map<String, Integer> pendingReturn = new LinkedHashMap<>();
    private boolean crafting;
    private double progress;

    public CraftingMachine(double speed, long powerPerTickJ) { this(speed, powerPerTickJ, null); }

    private CraftingMachine(double speed, long powerPerTickJ, RecipeBook smeltingBook) {
        this.speed = speed;
        this.powerPerTickJ = powerPerTickJ;
        this.smeltingBook = smeltingBook;
    }

    /** A furnace: the recipe follows the first inserted input and changes only when idle and empty. */
    public static CraftingMachine smelter(double speed, long powerPerTickJ, RecipeBook book) {
        return new CraftingMachine(speed, powerPerTickJ, book);
    }

    /**
     * Sets the recipe (may be null) and resets progress. Stored inputs and the inputs of a craft
     * already in progress are returned through {@link #drainInputs()}.
     */
    public void setRecipe(Recipe r) {
        if (crafting && recipe != null)
            for (Ingredient in : recipe.inputs()) pendingReturn.merge(in.item(), in.count(), Integer::sum);
        inputs.forEach((k, v) -> pendingReturn.merge(k, v, Integer::sum));
        inputs.clear();
        crafting = false;
        progress = 0;
        recipe = r;
    }

    /** Returns and clears the inputs to hand back to the player after a recipe change. */
    public Map<String, Integer> drainInputs() {
        Map<String, Integer> out = new LinkedHashMap<>(pendingReturn);
        inputs.forEach((k, v) -> out.merge(k, v, Integer::sum));
        pendingReturn.clear();
        inputs.clear();
        return out;
    }

    /** Inserts up to {@code count} of an input; returns the amount accepted. */
    public int insertInput(String item, int count, boolean automated) {
        if (count <= 0) return 0;
        if (smeltingBook != null && isIdle()) {
            Optional<Recipe> r = smeltingBook.smeltingFor(item);
            if (r.isPresent() && r.get() != recipe) setRecipe(r.get());
        }
        if (recipe == null) return 0;
        int need = 0;
        for (Ingredient in : recipe.inputs()) if (in.item().equals(item)) need = in.count();
        if (need == 0) return 0;
        int have = inputs.getOrDefault(item, 0);
        int limit = automated ? 2 * need : STACK_LIMIT;
        int accepted = Math.max(0, Math.min(count, limit - have));
        if (accepted > 0) inputs.merge(item, accepted, Integer::sum);
        return accepted;
    }

    private boolean isIdle() { return !crafting && inputs.isEmpty() && outputs.isEmpty(); }

    public int extractOutput(String item, int max) {
        int have = outputs.getOrDefault(item, 0);
        int taken = Math.max(0, Math.min(have, max));
        if (taken == have) outputs.remove(item);
        else if (taken > 0) outputs.put(item, have - taken);
        return taken;
    }

    public Map<String, Integer> inputs() { return Map.copyOf(inputs); }

    public Map<String, Integer> outputs() { return Map.copyOf(outputs); }

    public double progress() { return progress; }

    private boolean hasInputs() {
        for (Ingredient in : recipe.inputs())
            if (inputs.getOrDefault(in.item(), 0) < in.count()) return false;
        return true;
    }

    private boolean outputHasRoom() {
        for (Ingredient out : recipe.outputs())
            if (outputs.getOrDefault(out.item(), 0) + out.count() > STACK_LIMIT) return false;
        return true;
    }

    private boolean canStart() { return recipe != null && hasInputs() && outputHasRoom(); }

    /** Power demand this tick: full while a craft runs or can start, otherwise 0. */
    public long powerDemand() { return (crafting || canStart()) ? powerPerTickJ : 0L; }

    public void tick(double satisfaction) {
        if (recipe == null) return;
        if (!crafting) {
            if (!canStart()) {
                progress = 0; // idle or blocked: drop any carried overshoot
                return;
            }
            for (Ingredient in : recipe.inputs()) {
                int left = inputs.get(in.item()) - in.count();
                if (left == 0) inputs.remove(in.item()); else inputs.put(in.item(), left);
            }
            crafting = true; // progress keeps the overshoot carried from the previous craft
        }
        progress += satisfaction * speed / recipe.ticks();
        if (progress >= 1.0 - EPS) {
            for (Ingredient out : recipe.outputs()) outputs.merge(out.item(), out.count(), Integer::sum);
            crafting = false;
            double carry = progress - 1.0;
            progress = carry > EPS ? carry : 0;
        }
    }
}
