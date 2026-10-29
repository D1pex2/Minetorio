package dev.minetorio.core.player;

/** Mining times from wube/factorio-data 1.1.110 entities.lua, mining-drill.lua and trees.lua. */
public final class EngineerMiningRules {
    private EngineerMiningRules() {}
    public static double entityTime(String id) {
        if(id.contains("ore_field")) return 1;
        if(id.startsWith("tree_") || id.equals("tree_trunk")) return 0.55;
        if(id.startsWith("dry_tree")) return 0.5;
        if(id.equals("huge_rock")) return 3;
        if(id.equals("big_rock") || id.equals("sand_rock")) return 2;
        return switch(id) {
            case "burner_mining_drill","electric_mining_drill","steam_engine" -> 0.3;
            case "pumpjack","storage_tank" -> 0.5;
            case "stone_furnace","steel_furnace","assembling_machine_1","assembling_machine_2",
                 "lab","boiler","pump","oil_refinery","iron_chest","steel_chest" -> 0.2;
            default -> 0.1;
        };
    }
    public static double ticks(String id,boolean steelAxe) {
        return 20*entityTime(id)/(steelAxe?1:0.5);
    }
}
