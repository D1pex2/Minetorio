package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);

    // Materials
    public static final DeferredItem<Item> IRON_ORE = ITEMS.registerSimpleItem("iron_ore");
    public static final DeferredItem<Item> COPPER_ORE = ITEMS.registerSimpleItem("copper_ore");
    public static final DeferredItem<Item> IRON_PLATE = ITEMS.registerSimpleItem("iron_plate");
    public static final DeferredItem<Item> COPPER_PLATE = ITEMS.registerSimpleItem("copper_plate");
    public static final DeferredItem<Item> STONE_BRICK = ITEMS.registerSimpleItem("stone_brick");
    public static final DeferredItem<Item> IRON_GEAR_WHEEL = ITEMS.registerSimpleItem("iron_gear_wheel");
    public static final DeferredItem<Item> COPPER_CABLE = ITEMS.registerSimpleItem("copper_cable");
    public static final DeferredItem<Item> ELECTRONIC_CIRCUIT = ITEMS.registerSimpleItem("electronic_circuit");
    public static final DeferredItem<Item> AUTOMATION_SCIENCE_PACK = ITEMS.registerSimpleItem("automation_science_pack");
    public static final DeferredItem<Item> LOGISTIC_SCIENCE_PACK = ITEMS.registerSimpleItem("logistic_science_pack");

    // Machines (block items; the item id equals the block id)
    public static final DeferredItem<BlockItem> STONE_FURNACE = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_FURNACE);

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
