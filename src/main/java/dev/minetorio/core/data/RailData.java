package dev.minetorio.core.data;

import dev.minetorio.core.Units;
import dev.minetorio.core.recipe.*;
import dev.minetorio.core.research.Technology;
import java.util.*;

/** Каталог железной дороги по wube/factorio-data, тег 1.1.110. */
public final class RailData {
    private static final String M = "minetorio:";
    private RailData() {}

    public enum StockKind { LOCOMOTIVE, CARGO, FLUID }

    /** Скорость и сопротивления сохраняют исходные единицы динамики 60 Гц. */
    public record StockSpec(double weight, double maxSpeed, double brake, double friction, double air,
                            int health, int slots, double fluidCapacity) {}

    public record FuelSpec(long joules, double acceleration, double topSpeed) {}

    /** base/prototypes/recipe.lua: обычные рецепты, без дорогого режима. */
    public static List<Recipe> recipes() {
        return List.of(
                craft("rail", "1 stone,1 iron_stick,1 steel_plate", 2, 0.5),
                craft("locomotive", "20 engine_unit,10 electronic_circuit,30 steel_plate", 1, 4),
                craft("cargo_wagon", "10 iron_gear_wheel,20 iron_plate,20 steel_plate", 1, 1),
                craft("fluid_wagon", "10 iron_gear_wheel,16 steel_plate,8 pipe,1 storage_tank", 1, 1.5),
                craft("train_stop", "5 electronic_circuit,6 iron_plate,6 iron_stick,3 steel_plate", 1, 0.5),
                craft("rail_signal", "1 electronic_circuit,5 iron_plate", 1, 0.5),
                craft("rail_chain_signal", "1 electronic_circuit,5 iron_plate", 1, 0.5));
    }

    /** Поздние пакеты остаются обязательными и до реализации их производства. */
    public static List<Technology> technologies() {
        return List.of(
                tech("railway", "logistics_2,engine", "automation,logistic", 75, 30, "rail,locomotive,cargo_wagon"),
                tech("automated_rail_transportation", "railway", "automation,logistic", 75, 30, "train_stop"),
                tech("rail_signals", "automated_rail_transportation", "automation,logistic", 100, 30, "rail_signal,rail_chain_signal"),
                tech("fluid_wagon", "railway,fluid_handling", "automation,logistic", 200, 30, "fluid_wagon"),
                tech("braking_force_1", "railway,chemical_science_pack", "automation,logistic,chemical", 100, 30, ""),
                tech("braking_force_2", "braking_force_1", "automation,logistic,chemical", 200, 30, ""),
                tech("braking_force_3", "braking_force_2", "automation,logistic,chemical,production", 250, 30, ""),
                tech("braking_force_4", "braking_force_3", "automation,logistic,chemical,production", 350, 30, ""),
                tech("braking_force_5", "braking_force_4", "automation,logistic,chemical,production", 450, 35, ""),
                tech("braking_force_6", "braking_force_5", "automation,logistic,chemical,production,utility", 550, 45, ""),
                tech("braking_force_7", "braking_force_6", "automation,logistic,chemical,production,utility", 650, 60, ""));
    }

    public static double brakingBonus(Set<String> completed) {
        double bonus = 0;
        for (int level = 1; level <= 7; level++) {
            if (completed.contains("braking_force_" + level)) bonus += level == 1 ? 0.10 : 0.15;
        }
        return bonus;
    }

    /** base/prototypes/entity/trains.lua; slots для локомотива означает топливные слоты. */
    public static StockSpec stock(StockKind kind) {
        return switch (kind) {
            case LOCOMOTIVE -> new StockSpec(2000, 1.2, 10, 0.50, 0.0075, 1000, 3, 0);
            case CARGO -> new StockSpec(1000, 1.5, 3, 0.50, 0.01, 600, 40, 0);
            case FLUID -> new StockSpec(1000, 1.5, 3, 0.50, 0.01, 600, 0, 25000);
        };
    }

    /** Только химическое топливо каталога; item - полный идентификатор предмета. */
    public static Optional<FuelSpec> fuel(String item) {
        return switch (item) {
            case M + "wood" -> Optional.of(new FuelSpec(Units.mj(2), 1, 1));
            case M + "coal" -> Optional.of(new FuelSpec(Units.mj(4), 1, 1));
            case M + "solid_fuel" -> Optional.of(new FuelSpec(Units.mj(12), 1.2, 1.05));
            // Каталог для поздних блоков: здесь предметы и производство не регистрируются.
            case M + "rocket_fuel" -> Optional.of(new FuelSpec(Units.mj(100), 1.8, 1.15));
            case M + "nuclear_fuel" -> Optional.of(new FuelSpec(1_210_000_000L, 2.5, 1.15));
            default -> Optional.empty();
        };
    }

    private static Recipe craft(String id, String input, int count, double seconds) {
        List<Ingredient> ingredients = Arrays.stream(input.split(",")).map(s -> {
            String[] pair = s.split(" ", 2);
            return new Ingredient(M + pair[1], Integer.parseInt(pair[0]));
        }).toList();
        return new Recipe(id, ingredients, List.of(new Ingredient(M + id, count)), Units.ticks(seconds), RecipeCategory.CRAFTING);
    }

    private static Technology tech(String id, String prerequisites, String packs, int count, double seconds, String unlocks) {
        Map<String, Integer> ingredients = new LinkedHashMap<>();
        for (String pack : packs.split(",")) ingredients.put(M + pack + "_science_pack", 1);
        return new Technology(id, List.of(prerequisites.split(",")), ingredients, count, Units.ticks(seconds),
                unlocks.isEmpty() ? List.of() : List.of(unlocks.split(",")));
    }
}
