package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Factorio 1.1 intermediates. Later science exists as a cost icon, with no premature recipe. */
public final class OilMaterials {
    private OilMaterials() {}
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    public static final List<String> IDS = List.of("steel_plate", "iron_stick", "engine_unit", "electric_engine_unit",
            "advanced_circuit", "flying_robot_frame", "plastic_bar", "sulfur", "battery", "explosives", "solid_fuel",
            "chemical_science_pack", "production_science_pack", "utility_science_pack", "empty_barrel",
            "water_barrel", "crude_oil_barrel", "heavy_oil_barrel", "light_oil_barrel", "petroleum_gas_barrel",
            "sulfuric_acid_barrel", "lubricant_barrel");
    static {
        for (String id : IDS) ITEMS.registerSimpleItem(id, new net.minecraft.world.item.Item.Properties().stacksTo(stackSize(id)));
    }
    private static int stackSize(String id) {
        if (id.endsWith("_barrel")) return 10;
        if (id.endsWith("_science_pack") || id.equals("advanced_circuit") || id.equals("battery")) return 200;
        if (List.of("sulfur", "explosives", "solid_fuel", "engine_unit", "electric_engine_unit", "flying_robot_frame").contains(id)) return 50;
        return 100;
    }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
