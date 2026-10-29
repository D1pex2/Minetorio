package dev.minetorio.core.item;

/** Normal progression item capacities from wube/factorio-data 1.1.110 base/prototypes/item.lua.
 * Barrel capacities follow base/data-updates.lua. Engine/decorative items keep the engine default.
 */
public final class ItemStackSizes {
    public static final int MAX = 200;
    private ItemStackSizes() {}
    public static int limit(String item) {
        if (item == null) return 64;
        int data = item.indexOf('#');
        if (data >= 0) item = item.substring(0, data);
        if (!item.startsWith("minetorio:")) return 64;
        String id = item.substring("minetorio:".length());
        return switch (id) {
            case "blueprint", "blueprint_book", "deconstruction_planner", "engineer_tool", "ghost_item", "upgrade_planner" -> 1;
            case "chemical_plant", "crude_oil_barrel", "empty_barrel", "heavy_oil_barrel", "lab", "light_oil_barrel",
                 "lubricant_barrel", "oil_refinery", "petroleum_gas_barrel", "roboport", "steam_engine", "sulfuric_acid_barrel",
                 "water_barrel" -> 10;
            case "offshore_pump", "personal_roboport", "pumpjack" -> 20;
            case "accumulator", "active_provider_chest", "assembling_machine_1", "assembling_machine_2", "big_electric_pole", "boiler",
                 "buffer_chest", "burner_inserter", "burner_mining_drill", "coal", "construction_robot", "copper_ore",
                 "electric_engine_unit", "electric_mining_drill", "engine_unit", "explosives", "express_splitter", "express_underground_belt",
                 "fast_inserter", "fast_splitter", "fast_underground_belt", "filter_inserter", "flying_robot_frame", "inserter",
                 "iron_chest", "iron_ore", "logistic_robot", "long_handed_inserter", "medium_electric_pole", "passive_provider_chest",
                 "pipe_to_ground", "pump", "radar", "requester_chest", "small_electric_pole", "solar_panel",
                 "solid_fuel", "splitter", "stack_inserter", "steel_chest", "steel_furnace", "stone",
                 "stone_furnace", "storage_chest", "storage_tank", "substation", "sulfur", "underground_belt",
                 "wooden_chest" -> 50;
            case "copper_plate", "express_transport_belt", "fast_transport_belt", "iron_gear_wheel", "iron_plate", "iron_stick",
                 "pipe", "plastic_bar", "raw_fish", "steel_plate", "stone_brick", "transport_belt",
                 "wood" -> 100;
            case "advanced_circuit", "automation_science_pack", "battery", "chemical_science_pack", "copper_cable", "electronic_circuit",
                 "logistic_science_pack", "production_science_pack", "utility_science_pack" -> 200;
            default -> 64;
        };
    }
}
