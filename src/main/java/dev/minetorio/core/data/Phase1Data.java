package dev.minetorio.core.data;

import dev.minetorio.core.Units;
import dev.minetorio.core.recipe.*;
import dev.minetorio.core.research.*;
import java.util.*;

/** Phase 1 content tables (Factorio 1.1 values). Includes the tech tree. */
public final class Phase1Data {
    private static final String M = "minetorio:";
    private static final RecipeBook BOOK = build();
    private static final Map<String, Long> FUEL = buildFuel();
    private static final Set<String> START = buildStart();
    private static final TechTree TECH = buildTech();

    private Phase1Data() {}

    public static RecipeBook recipes() { return BOOK; }

    /** Fuel energy in joules per item. */
    public static Map<String, Long> fuelValues() { return FUEL; }

    /**
     * Recipe ids available at game start: all crafting recipes except the locked ones.
     * Smelting recipe ids are included too (smelting is unlocked by category anyway).
     */
    public static Set<String> startRecipes() { return START; }

    public static TechTree techTree() { return TECH; }

    private static Technology tech(String id, String prereqs, String packs, int units, double sec, String unlocks) {
        List<String> pre = prereqs.isEmpty() ? List.of() : List.of(prereqs.split(","));
        Map<String, Integer> pk = new LinkedHashMap<>();
        for (String p : packs.split(",")) pk.put(M + p + "_science_pack", 1);
        return new Technology(id, pre, pk, units, Units.ticks(sec), List.of(unlocks.split(",")));
    }

    private static TechTree buildTech() {
        return new TechTree(List.of(
                tech("automation", "", "automation", 10, 10, "assembling_machine_1,long_handed_inserter"),
                tech("logistics", "", "automation", 20, 15, "underground_belt,splitter"),
                tech("fast_inserter", "automation", "automation", 30, 15, "fast_inserter,filter_inserter"),
                tech("logistic_science_pack", "", "automation", 75, 5, "logistic_science_pack"),
                tech("automation_2", "automation,logistic_science_pack", "automation,logistic", 40, 15,
                        "assembling_machine_2"),
                tech("logistics_2", "logistics,logistic_science_pack", "automation,logistic", 200, 30,
                        "fast_transport_belt,fast_underground_belt,fast_splitter"),
                // TEMP: Factorio 1.1 also requires advanced electronics (oil stage)
                tech("stack_inserter", "fast_inserter,logistics_2", "automation,logistic", 150, 30, "stack_inserter"),
                // TEMP: red + green until the oil stage; Factorio's construction robotics needs more
                tech("construction_robotics", "automation,logistic_science_pack", "automation,logistic", 100, 30,
                        "roboport,construction_robot"),
                // Stage 4 logistic network. TEMP: red + green until the oil stage (Factorio 1.1: logistic
                // robotics and personal roboport need chemical science, logistic system also utility science;
                // logistic robotics' prerequisite "robotics" is stood in for by construction robotics)
                tech("logistic_robotics", "construction_robotics", "automation,logistic", 250, 30,
                        "logistic_robot,passive_provider_chest,storage_chest"),
                tech("logistic_system", "logistic_robotics", "automation,logistic", 500, 30,
                        "active_provider_chest,requester_chest,buffer_chest"),
                tech("personal_roboport", "construction_robotics", "automation,logistic", 50, 30, "personal_roboport")));
    }

    private static Ingredient ing(String spec) {
        String[] p = spec.split(" ", 2);
        int n = p.length == 2 ? Integer.parseInt(p[0]) : 1;
        String item = p.length == 2 ? p[1] : p[0];
        return new Ingredient(item.contains(":") ? item : M + item, n);
    }

    private static List<Ingredient> ings(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).map(Phase1Data::ing).toList();
    }

    private static Recipe craft(String id, String in, int outCount, double sec) {
        return new Recipe(id, ings(in), List.of(new Ingredient(M + id, outCount)),
                Units.ticks(sec), RecipeCategory.CRAFTING);
    }

    private static Recipe smelt(String id, String in, String out) {
        return new Recipe(id, ings(in), List.of(new Ingredient(out, 1)),
                Units.ticks(3.2), RecipeCategory.SMELTING);
    }

    private static RecipeBook build() {
        List<Recipe> r = new ArrayList<>();
        r.add(smelt("iron_plate", "iron_ore", M + "iron_plate"));
        r.add(smelt("iron_plate_from_raw_iron", "minecraft:raw_iron", M + "iron_plate"));
        r.add(smelt("copper_plate", "copper_ore", M + "copper_plate"));
        r.add(smelt("copper_plate_from_raw_copper", "minecraft:raw_copper", M + "copper_plate"));
        r.add(smelt("stone_brick", "2 minecraft:cobblestone", M + "stone_brick"));

        r.add(craft("iron_gear_wheel", "2 iron_plate", 1, 0.5));
        r.add(craft("copper_cable", "1 copper_plate", 2, 0.5));
        r.add(craft("electronic_circuit", "1 iron_plate, 3 copper_cable", 1, 0.5));
        r.add(craft("pipe", "1 iron_plate", 1, 0.5));
        r.add(craft("stone_furnace", "5 minecraft:cobblestone", 1, 0.5));
        r.add(craft("burner_mining_drill", "3 iron_gear_wheel, 1 stone_furnace, 3 iron_plate", 1, 2));
        r.add(craft("electric_mining_drill", "3 electronic_circuit, 5 iron_gear_wheel, 10 iron_plate", 1, 2));
        r.add(craft("transport_belt", "1 iron_gear_wheel, 1 iron_plate", 2, 0.5));
        r.add(craft("underground_belt", "10 iron_plate, 5 transport_belt", 2, 1));
        r.add(craft("splitter", "5 electronic_circuit, 5 iron_plate, 4 transport_belt", 1, 1));
        r.add(craft("fast_transport_belt", "5 iron_gear_wheel, 1 transport_belt", 1, 0.5));
        r.add(craft("fast_underground_belt", "40 iron_gear_wheel, 2 underground_belt", 2, 2));
        r.add(craft("fast_splitter", "1 splitter, 10 electronic_circuit, 10 iron_gear_wheel", 1, 2));
        r.add(craft("burner_inserter", "1 iron_gear_wheel, 1 iron_plate", 1, 0.5));
        r.add(craft("inserter", "1 electronic_circuit, 1 iron_gear_wheel, 1 iron_plate", 1, 0.5));
        r.add(craft("long_handed_inserter", "1 inserter, 1 iron_gear_wheel, 1 iron_plate", 1, 0.5));
        r.add(craft("fast_inserter", "2 electronic_circuit, 2 iron_plate, 1 inserter", 1, 0.5));
        r.add(craft("filter_inserter", "1 fast_inserter, 4 electronic_circuit", 1, 0.5));
        // TEMP until the oil stage: Factorio's 1 advanced circuit becomes 2 more electronic circuits
        r.add(craft("stack_inserter", "15 iron_gear_wheel, 17 electronic_circuit, 1 fast_inserter", 1, 0.5));
        r.add(craft("assembling_machine_1", "3 electronic_circuit, 5 iron_gear_wheel, 9 iron_plate", 1, 0.5));
        r.add(craft("assembling_machine_2",
                "2 iron_plate, 3 electronic_circuit, 5 iron_gear_wheel, 1 assembling_machine_1", 1, 0.5));
        r.add(craft("lab", "10 electronic_circuit, 10 iron_gear_wheel, 4 transport_belt", 1, 2));
        r.add(craft("radar", "5 electronic_circuit, 5 iron_gear_wheel, 10 iron_plate", 1, 0.5));
        r.add(craft("offshore_pump", "2 electronic_circuit, 1 pipe, 1 iron_gear_wheel", 1, 0.5));
        r.add(craft("boiler", "1 stone_furnace, 4 pipe", 1, 0.5));
        r.add(craft("steam_engine", "8 iron_gear_wheel, 5 pipe, 10 iron_plate", 1, 0.5));
        r.add(craft("small_electric_pole", "1 minecraft:stick, 2 copper_cable", 2, 0.5));
        // TEMP recipes from phase 1 intermediates until the oil stage (Factorio: 45 steel, 45 gears,
        // 45 advanced circuits; and 1 flying robot frame + 2 circuits)
        r.add(craft("roboport", "45 electronic_circuit, 45 iron_gear_wheel, 45 iron_plate", 1, 5));
        r.add(craft("construction_robot", "3 electronic_circuit, 2 iron_gear_wheel, 2 iron_plate", 1, 0.5));
        // Stage 4. TEMP until the oil stage: Factorio's logistic robot is 1 flying robot frame + 2 advanced
        // circuits (as the construction robot's TEMP recipe here); each logistic chest 1 steel chest (8 steel)
        // + 3 electronic + 1 advanced circuit (iron instead of steel, 2 more electronic circuits); the personal
        // roboport 10 advanced circuits, 45 batteries, 20 gears, 20 steel (no batteries: more circuits)
        r.add(craft("logistic_robot", "3 electronic_circuit, 2 iron_gear_wheel, 2 iron_plate", 1, 0.5));
        for (String chest : List.of("passive_provider_chest", "active_provider_chest", "storage_chest", "requester_chest", "buffer_chest")) {
            r.add(craft(chest, "8 iron_plate, 5 electronic_circuit", 1, 0.5));
        }
        r.add(craft("personal_roboport", "40 electronic_circuit, 20 iron_gear_wheel, 20 iron_plate, 20 copper_cable", 1, 10));
        r.add(craft("automation_science_pack", "1 copper_plate, 1 iron_gear_wheel", 1, 5));
        r.add(craft("logistic_science_pack", "1 inserter, 1 transport_belt", 1, 6));
        return new RecipeBook(r);
    }

    private static Map<String, Long> buildFuel() {
        Map<String, Long> f = new LinkedHashMap<>();
        f.put("minecraft:coal", Units.mj(4));
        f.put("minecraft:charcoal", Units.mj(4));
        f.put("minecraft:coal_block", Units.mj(36));
        for (String w : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")) {
            f.put("minecraft:" + w + "_log", Units.mj(2));
            f.put("minecraft:" + w + "_planks", Units.mj(0.5));
        }
        f.put("minecraft:bamboo_planks", Units.mj(0.5));
        f.put("minecraft:stick", Units.mj(0.25));
        return Collections.unmodifiableMap(f);
    }

    private static Set<String> buildStart() {
        Set<String> locked = Set.of("assembling_machine_1", "assembling_machine_2", "long_handed_inserter",
                "fast_inserter", "filter_inserter", "stack_inserter", "underground_belt", "splitter", "logistic_science_pack", "roboport", "construction_robot",
                "logistic_robot", "passive_provider_chest", "active_provider_chest", "storage_chest", "requester_chest",
                "buffer_chest", "personal_roboport");
        Set<String> s = new LinkedHashSet<>();
        for (Recipe r : BOOK.all()) {
            if (r.category() == RecipeCategory.CRAFTING
                    && (locked.contains(r.id()) || r.id().startsWith("fast_") || r.id().startsWith("express_")))
                continue;
            s.add(r.id());
        }
        return Collections.unmodifiableSet(s);
    }
}
