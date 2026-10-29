package dev.minetorio.core.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import dev.minetorio.core.Units;
import dev.minetorio.core.item.ItemStackSizes;
import dev.minetorio.core.recipe.*;
import dev.minetorio.core.research.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class RailDataTest {
    private static final JsonObject SOURCE = fixture();
    private static final List<String> RECIPES = List.of("rail", "locomotive", "cargo_wagon", "fluid_wagon",
            "train_stop", "rail_signal", "rail_chain_signal");

    @Test void allNormalRecipesMatchPinnedFactorioSource() {
        assertEquals(7, RailData.recipes().size());
        for (JsonElement element : SOURCE.getAsJsonArray("recipes")) {
            JsonObject row = element.getAsJsonObject();
            String id = id(row.get("name").getAsString());
            Recipe recipe = RailData.recipes().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
            assertEquals(ingredients(row.getAsJsonArray("ingredients")), recipe.inputs(), id);
            assertEquals(List.of(new Ingredient(item(row.get("result").getAsString()),
                    (int) value(row, "result_count", defaultValue("result_count")))), recipe.outputs(), id);
            assertEquals(Units.ticks(value(row, "energy_required", defaultValue("energy_required"))), recipe.ticks(), id);
            assertEquals(RecipeCategory.CRAFTING, recipe.category(), id);
            assertEquals(recipe, Phase1Data.recipes().get(id).orElseThrow(), id);
        }
    }

    @Test void stackLimitsMatchAllSevenSourceItems() {
        for (JsonElement element : SOURCE.getAsJsonArray("items")) {
            JsonObject row = element.getAsJsonObject();
            String item = item(row.get("name").getAsString());
            assertEquals(row.get("stack_size").getAsInt(), ItemStackSizes.limit(item), item);
            assertEquals(row.get("stack_size").getAsInt(), ItemStackSizes.limit(item + "#data"), item);
        }
    }

    @Test void allResearchCostsDependenciesAndUnlocksMatchSource() {
        assertEquals(11, RailData.technologies().size());
        for (JsonElement element : SOURCE.getAsJsonArray("technologies")) {
            JsonObject row = element.getAsJsonObject();
            String id = id(row.get("name").getAsString());
            Technology technology = RailData.technologies().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
            JsonObject unit = row.getAsJsonObject("unit");
            assertEquals(ids(row.getAsJsonArray("prerequisites")), technology.prerequisites(), id);
            Map<String, Integer> packs = new LinkedHashMap<>();
            for (Ingredient ingredient : ingredients(unit.getAsJsonArray("ingredients"))) packs.put(ingredient.item(), ingredient.count());
            assertEquals(packs, technology.packsPerUnit(), id);
            assertEquals(unit.get("count").getAsInt(), technology.units(), id);
            assertEquals(Units.ticks(unit.get("time").getAsDouble()), technology.ticksPerUnit(), id);
            assertEquals(ids(row.getAsJsonArray("unlocks")), technology.unlocksRecipes(), id);
            assertEquals(technology, Phase1Data.techTree().get(id).orElseThrow(), id);
            if (row.has("train-braking-force-bonus"))
                assertEquals(row.get("train-braking-force-bonus").getAsDouble(), RailData.brakingBonus(Set.of(id)), 1e-12, id);
        }
    }

    @Test void rollingStockMatchesSourceWithoutConvertingSixtyHertzSpeed() {
        for (JsonElement element : SOURCE.getAsJsonArray("stock")) {
            JsonObject row = element.getAsJsonObject();
            RailData.StockKind kind = switch (row.get("name").getAsString()) {
                case "locomotive" -> RailData.StockKind.LOCOMOTIVE;
                case "cargo-wagon" -> RailData.StockKind.CARGO;
                case "fluid-wagon" -> RailData.StockKind.FLUID;
                default -> throw new AssertionError(row);
            };
            assertEquals(new RailData.StockSpec(row.get("weight").getAsDouble(), row.get("max_speed").getAsDouble(),
                    row.get("braking_force").getAsDouble(), row.get("friction_force").getAsDouble(),
                    row.get("air_resistance").getAsDouble(), row.get("max_health").getAsInt(),
                    (int) value(row, "fuel_inventory_size", value(row, "inventory_size", 0)),
                    value(row, "capacity", 0)), RailData.stock(kind), kind.name());
        }
    }

    @Test void chemicalFuelsMatchSourceIncludingLateCatalogOnlyFuels() {
        for (JsonElement element : SOURCE.getAsJsonArray("fuels")) {
            JsonObject row = element.getAsJsonObject();
            String energy = row.get("fuel_value").getAsString();
            long joules = Math.round(Double.parseDouble(energy.substring(0, energy.length() - 2))
                    * (energy.endsWith("GJ") ? 1_000_000_000L : 1_000_000L));
            assertEquals(new RailData.FuelSpec(joules,
                    value(row, "fuel_acceleration_multiplier", defaultValue("fuel_acceleration_multiplier")),
                    value(row, "fuel_top_speed_multiplier", defaultValue("fuel_top_speed_multiplier"))),
                    RailData.fuel(item(row.get("name").getAsString())).orElseThrow());
        }
        for (String id : List.of("rocket_fuel", "nuclear_fuel")) {
            assertTrue(Phase1Data.recipes().get(id).isEmpty());
            assertFalse(Phase1Data.startRecipes().contains(id));
            assertTrue(Phase1Data.techTree().all().stream().noneMatch(t -> t.unlocksRecipes().contains(id)));
        }
    }

    @Test void vanillaBurnablesAndUnknownItemsAreNotTrainFuel() {
        for (String item : List.of("minecraft:coal", "minecraft:oak_planks", "minetorio:iron_plate", "minetorio:unknown", ""))
            assertTrue(RailData.fuel(item).isEmpty(), item);
    }

    @Test void allRailRecipesLockedAtWorldStart() {
        ResearchState state = state(Set.of());
        for (String id : RECIPES) {
            assertTrue(Phase1Data.recipes().get(id).isPresent(), id);
            assertFalse(Phase1Data.startRecipes().contains(id), id);
            assertFalse(state.isRecipeUnlocked(id), id);
        }
    }

    @Test void railwayUnlocksOnlyThreeRecipes() {
        ResearchState state = state(Set.of("logistics_2", "engine"));
        complete(state, "railway");
        assertEquals(Set.of("rail", "locomotive", "cargo_wagon"), unlockedRail(state));
    }

    @Test void signalsRequireAutomatedRail() {
        ResearchState state = state(Set.of("railway"));
        assertFalse(state.canResearch("rail_signals"));
        assertThrows(IllegalStateException.class, () -> state.setCurrent("rail_signals"));
        complete(state, "automated_rail_transportation");
        assertTrue(state.isRecipeUnlocked("train_stop"));
        assertFalse(state.isRecipeUnlocked("rail_signal"));
        assertFalse(state.isRecipeUnlocked("rail_chain_signal"));
        complete(state, "rail_signals");
        assertTrue(state.isRecipeUnlocked("rail_signal"));
        assertTrue(state.isRecipeUnlocked("rail_chain_signal"));
    }

    @Test void fluidWagonRequiresFluidHandling() {
        ResearchState state = state(Set.of("railway"));
        assertFalse(state.canResearch("fluid_wagon"));
        assertThrows(IllegalStateException.class, () -> state.setCurrent("fluid_wagon"));
        ResearchState ready = state(Set.of("railway", "fluid_handling"));
        complete(ready, "fluid_wagon");
        assertTrue(ready.isRecipeUnlocked("fluid_wagon"));
    }

    @Test void brakingBonusAddsOnlyCompletedLevels() {
        ResearchState state = state(Set.of("railway", "chemical_science_pack"));
        assertEquals(0, RailData.brakingBonus(state.researched()));
        double sum = 0;
        for (int level = 1; level <= 7; level++) {
            String id = "braking_force_" + level;
            state.setCurrent(id);
            int units = state.current().orElseThrow().units();
            state.completeUnit();
            assertEquals(sum, RailData.brakingBonus(state.researched()), 1e-12);
            for (int i = 1; i < units; i++) state.completeUnit();
            sum += level == 1 ? 0.1 : 0.15;
            assertEquals(sum, RailData.brakingBonus(state.researched()), 1e-12);
        }
        assertEquals(0.15, RailData.brakingBonus(Set.of("braking_force_4", "braking_force_8", "railway")), 1e-12);
    }

    @Test void firstBrakingResearchRequiresChemicalScienceTechnology() {
        ResearchState state = state(Set.of("railway"));
        assertFalse(state.canResearch("braking_force_1"));
        assertThrows(IllegalStateException.class, () -> state.setCurrent("braking_force_1"));
    }

    @Test void missingLateScienceDoesNotUnlockResearch() {
        for (int level = 3; level <= 7; level++) {
            String id = "braking_force_" + level;
            ResearchState state = state(Set.of("braking_force_" + (level - 1)));
            state.setCurrent(id);
            Technology technology = state.current().orElseThrow();
            LabLogic lab = new LabLogic(1, Units.kw(60));
            for (String pack : List.of("automation", "logistic", "chemical")) lab.insertPack(item(pack + "_science_pack"), 1, false);
            for (int tick = 0; tick < technology.ticksPerUnit() * 2; tick++) lab.tick(state, 1);
            assertEquals(0, state.unitsDone(), id);
            assertFalse(state.isResearched(id), id);
            assertEquals(0, lab.powerDemand(), id);
            lab.insertPack(item("production_science_pack"), 1, false);
            if (level >= 6) {
                for (int tick = 0; tick < technology.ticksPerUnit(); tick++) lab.tick(state, 1);
                assertEquals(0, state.unitsDone(), id);
                lab.insertPack(item("utility_science_pack"), 1, false);
            }
            for (int tick = 0; tick < technology.ticksPerUnit(); tick++) lab.tick(state, 1);
            assertEquals(1, state.unitsDone(), id);
        }
    }

    @Test void existingIronStickDependencyIsReused() {
        assertEquals(1, Phase1Data.recipes().all().stream().filter(r -> r.id().equals("iron_stick")).count());
        assertEquals(List.of(new Ingredient(item("iron_plate"), 1)), Phase1Data.recipes().get("iron_stick").orElseThrow().inputs());
        assertTrue(Phase1Data.startRecipes().contains("iron_stick"));
        assertTrue(RailData.recipes().stream().anyMatch(r -> r.inputs().contains(new Ingredient(item("iron_stick"), 1))));
    }

    private static ResearchState state(Set<String> completed) {
        ResearchState state = new ResearchState(Phase1Data.techTree(), Phase1Data.startRecipes());
        state.load(completed, null, Map.of());
        return state;
    }

    private static void complete(ResearchState state, String id) {
        state.setCurrent(id);
        int count = state.current().orElseThrow().units();
        for (int i = 0; i < count; i++) state.completeUnit();
    }

    private static Set<String> unlockedRail(ResearchState state) {
        Set<String> result = new HashSet<>();
        for (String id : RECIPES) if (state.isRecipeUnlocked(id)) result.add(id);
        return result;
    }

    private static JsonObject fixture() {
        try (InputStream stream = RailDataTest.class.getResourceAsStream("/factorio-1.1.110/railway.json")) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static String id(String original) { return original.replace('-', '_'); }
    private static String item(String original) { return "minetorio:" + id(original); }
    private static double defaultValue(String name) { return SOURCE.getAsJsonObject("defaults").get(name).getAsDouble(); }
    private static double value(JsonObject row, String name, double fallback) { return row.has(name) ? row.get(name).getAsDouble() : fallback; }
    private static List<String> ids(JsonArray rows) {
        List<String> result = new ArrayList<>();
        for (JsonElement row : rows) result.add(id(row.getAsString()));
        return result;
    }
    private static List<Ingredient> ingredients(JsonArray rows) {
        List<Ingredient> result = new ArrayList<>();
        for (JsonElement row : rows) {
            JsonArray pair = row.getAsJsonArray();
            result.add(new Ingredient(item(pair.get(0).getAsString()), pair.get(1).getAsInt()));
        }
        return result;
    }
}
