package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.belt.BeltItem;
import dev.minetorio.content.blueprint.BlueprintItem;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.robot.GhostItem;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
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
    public static final DeferredItem<BlockItem> STONE_FURNACE = multiblock(ModBlocks.STONE_FURNACE);
    public static final DeferredItem<BlockItem> BURNER_MINING_DRILL = multiblock(ModBlocks.BURNER_MINING_DRILL);
    public static final DeferredItem<BlockItem> ELECTRIC_MINING_DRILL = multiblock(ModBlocks.ELECTRIC_MINING_DRILL);
    public static final DeferredItem<BlockItem> ASSEMBLING_MACHINE_1 = multiblock(ModBlocks.ASSEMBLING_MACHINE_1);
    public static final DeferredItem<BlockItem> ASSEMBLING_MACHINE_2 = multiblock(ModBlocks.ASSEMBLING_MACHINE_2);
    public static final DeferredItem<BlockItem> LAB = multiblock(ModBlocks.LAB);
    public static final DeferredItem<BlockItem> RADAR = multiblock(ModBlocks.RADAR);

    // Belts (express_* have no recipe: creative tab only)
    public static final DeferredItem<BlockItem> TRANSPORT_BELT = belt(ModBlocks.TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> FAST_TRANSPORT_BELT = belt(ModBlocks.FAST_TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> EXPRESS_TRANSPORT_BELT = belt(ModBlocks.EXPRESS_TRANSPORT_BELT);
    public static final DeferredItem<BlockItem> UNDERGROUND_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.UNDERGROUND_BELT);
    public static final DeferredItem<BlockItem> FAST_UNDERGROUND_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.FAST_UNDERGROUND_BELT);
    public static final DeferredItem<BlockItem> EXPRESS_UNDERGROUND_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.EXPRESS_UNDERGROUND_BELT);
    public static final DeferredItem<BlockItem> SPLITTER = multiblock(ModBlocks.SPLITTER);
    public static final DeferredItem<BlockItem> FAST_SPLITTER = multiblock(ModBlocks.FAST_SPLITTER);
    public static final DeferredItem<BlockItem> EXPRESS_SPLITTER = multiblock(ModBlocks.EXPRESS_SPLITTER);

    // Inserters
    public static final DeferredItem<BlockItem> BURNER_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.BURNER_INSERTER);
    public static final DeferredItem<BlockItem> INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.INSERTER);
    public static final DeferredItem<BlockItem> LONG_HANDED_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.LONG_HANDED_INSERTER);
    public static final DeferredItem<BlockItem> FAST_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.FAST_INSERTER);
    public static final DeferredItem<BlockItem> FILTER_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.FILTER_INSERTER);
    public static final DeferredItem<BlockItem> STACK_INSERTER = ITEMS.registerSimpleBlockItem(ModBlocks.STACK_INSERTER);

    // Steam power
    public static final DeferredItem<BlockItem> OFFSHORE_PUMP = multiblock(ModBlocks.OFFSHORE_PUMP);
    public static final DeferredItem<BlockItem> PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.PIPE);
    public static final DeferredItem<BlockItem> BOILER = multiblock(ModBlocks.BOILER);
    public static final DeferredItem<BlockItem> STEAM_ENGINE = multiblock(ModBlocks.STEAM_ENGINE);
    public static final DeferredItem<BlockItem> SMALL_ELECTRIC_POLE = ITEMS.registerSimpleBlockItem(ModBlocks.SMALL_ELECTRIC_POLE);

    // Construction robots
    public static final DeferredItem<BlockItem> ROBOPORT = multiblock(ModBlocks.ROBOPORT);
    public static final DeferredItem<Item> CONSTRUCTION_ROBOT = ITEMS.registerSimpleItem("construction_robot");
    /** Factorio's ghost cursor as an item (see {@link GhostItem}); not in the creative tab. */
    public static final DeferredItem<Item> GHOST_ITEM = ITEMS.registerItem("ghost_item", p -> new GhostItem(p.stacksTo(1)));
    /** Factorio's blueprint (see {@link BlueprintItem}); free from the blueprint key. */
    public static final DeferredItem<Item> BLUEPRINT = ITEMS.registerItem("blueprint", p -> new BlueprintItem(p.stacksTo(1)));

    /** Factorio's deconstruction planner: free from the shortcut key, never crafted. */
    public static final DeferredItem<Item> DECONSTRUCTION_PLANNER = ITEMS.registerItem("deconstruction_planner",
            p -> new dev.minetorio.content.robot.PlannerItems.DeconstructionPlanner(p.stacksTo(1)));
    /** Factorio's upgrade planner: free from the shortcut key, never crafted. */
    public static final DeferredItem<Item> UPGRADE_PLANNER = ITEMS.registerItem("upgrade_planner",
            p -> new dev.minetorio.content.robot.PlannerItems.UpgradePlanner(p.stacksTo(1)));

    /** Paths of every item registered above (LangCompletenessTest checks it against {@link #ITEMS} and the lang files). */
    public static final List<String> IDS = List.of(
            "iron_ore", "copper_ore", "iron_plate", "copper_plate", "stone_brick", "iron_gear_wheel", "copper_cable",
            "electronic_circuit", "automation_science_pack", "logistic_science_pack",
            "stone_furnace", "burner_mining_drill", "electric_mining_drill", "assembling_machine_1", "assembling_machine_2", "lab",
            "radar",
            "transport_belt", "fast_transport_belt", "express_transport_belt",
            "underground_belt", "fast_underground_belt", "express_underground_belt",
            "splitter", "fast_splitter", "express_splitter",
            "burner_inserter", "inserter", "long_handed_inserter", "fast_inserter", "filter_inserter", "stack_inserter",
            "offshore_pump", "pipe", "boiler", "steam_engine", "small_electric_pole",
            "roboport", "construction_robot", "ghost_item", "blueprint", "deconstruction_planner", "upgrade_planner");

    private ModItems() {}

    /** Item of a multiblock machine: placed centered on the targeted cell (R26). */
    private static DeferredItem<BlockItem> belt(DeferredBlock<? extends Block> block) {
        return ITEMS.registerItem(block.getId().getPath(), p -> new BeltItem(block.get(), p));
    }

    private static DeferredItem<BlockItem> multiblock(DeferredBlock<? extends MultiblockControllerBlock> block) {
        return ITEMS.registerItem(block.getId().getPath(), p -> new MultiblockBlockItem(block.get(), p));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
