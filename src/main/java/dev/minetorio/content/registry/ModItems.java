package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.deposit.ScannerItem;
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
    public static final DeferredItem<BlockItem> BURNER_MINING_DRILL = ITEMS.registerSimpleBlockItem(ModBlocks.BURNER_MINING_DRILL);
    public static final DeferredItem<BlockItem> ELECTRIC_MINING_DRILL = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_MINING_DRILL);

    // Tools
    public static final DeferredItem<ScannerItem> SCANNER = ITEMS.registerItem("scanner", ScannerItem::new, new Item.Properties().stacksTo(1));

    // Belts (express_* have no recipe: creative tab only)
    public static final DeferredItem<BlockItem> TRANSPORT_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> FAST_TRANSPORT_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.FAST_TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> EXPRESS_TRANSPORT_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.EXPRESS_TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> UNDERGROUND_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.UNDERGROUND_BELT);
    public static final DeferredItem<BlockItem> FAST_UNDERGROUND_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.FAST_UNDERGROUND_BELT);
    public static final DeferredItem<BlockItem> EXPRESS_UNDERGROUND_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.EXPRESS_UNDERGROUND_BELT);
    public static final DeferredItem<BlockItem> SPLITTER = ITEMS.registerSimpleBlockItem(ModBlocks.SPLITTER);
    public static final DeferredItem<BlockItem> FAST_SPLITTER = ITEMS.registerSimpleBlockItem(ModBlocks.FAST_SPLITTER);
    public static final DeferredItem<BlockItem> EXPRESS_SPLITTER = ITEMS.registerSimpleBlockItem(ModBlocks.EXPRESS_SPLITTER);

    // Inserters
    public static final DeferredItem<BlockItem> BURNER_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.BURNER_INSERTER);
    public static final DeferredItem<BlockItem> INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.INSERTER);
    public static final DeferredItem<BlockItem> LONG_HANDED_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.LONG_HANDED_INSERTER);
    public static final DeferredItem<BlockItem> FAST_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.FAST_INSERTER);

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
