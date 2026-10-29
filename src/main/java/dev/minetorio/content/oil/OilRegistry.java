package dev.minetorio.content.oil;

import dev.minetorio.Minetorio;
import dev.minetorio.content.fluid.FluidPorts;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.capabilities.Capabilities;
import dev.minetorio.core.oil.OilRecipe.MachineKind;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;
import java.util.function.Supplier;

/** Independent registries, wired by the mod entrypoint. */
public final class OilRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Minetorio.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Minetorio.MODID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Minetorio.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Minetorio.MODID);
    public static final DeferredBlock<OilSourceBlock> OIL_SOURCE = BLOCKS.registerBlock("oil_source", OilSourceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1, 3600000).noLootTable().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<PumpjackBlock> PUMPJACK = BLOCKS.registerBlock("pumpjack", PumpjackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3).sound(SoundType.METAL));
    public static final DeferredBlock<OilProcessingBlock> OIL_REFINERY = BLOCKS.registerBlock("oil_refinery", p -> new OilProcessingBlock(MachineKind.REFINERY,p),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3).sound(SoundType.METAL));
    public static final DeferredBlock<OilProcessingBlock> CHEMICAL_PLANT = BLOCKS.registerBlock("chemical_plant", p -> new OilProcessingBlock(MachineKind.CHEMICAL_PLANT,p),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3).sound(SoundType.METAL));
    public static final DeferredBlock<SteelFurnaceBlock> STEEL_FURNACE = BLOCKS.registerBlock("steel_furnace", SteelFurnaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3).sound(SoundType.METAL).lightLevel(s -> s.getValue(SteelFurnaceBlock.LIT) ? SteelFurnaceBlock.LIGHT : 0));
    public static final DeferredBlock<SteelChestBlock> STEEL_CHEST = BLOCKS.registerBlock("steel_chest", SteelChestBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> PUMPJACK_ITEM = ITEMS.registerItem("pumpjack", p -> new MultiblockBlockItem(PUMPJACK.get(), p));
    public static final DeferredItem<BlockItem> OIL_REFINERY_ITEM = ITEMS.registerItem("oil_refinery", p -> new MultiblockBlockItem(OIL_REFINERY.get(), p));
    public static final DeferredItem<BlockItem> CHEMICAL_PLANT_ITEM = ITEMS.registerItem("chemical_plant", p -> new MultiblockBlockItem(CHEMICAL_PLANT.get(), p));
    public static final DeferredItem<BlockItem> STEEL_FURNACE_ITEM = ITEMS.registerItem("steel_furnace", p -> new MultiblockBlockItem(STEEL_FURNACE.get(), p));
    public static final DeferredItem<BlockItem> STEEL_CHEST_ITEM = ITEMS.registerSimpleBlockItem(STEEL_CHEST);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilSourceBlockEntity>> OIL_SOURCE_BE = ENTITIES.register("oil_source",
            () -> BlockEntityType.Builder.of(OilSourceBlockEntity::new, OIL_SOURCE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpjackBlockEntity>> PUMPJACK_BE = ENTITIES.register("pumpjack",
            () -> BlockEntityType.Builder.of(PumpjackBlockEntity::new, PUMPJACK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilProcessingBlockEntity>> PROCESSING_BE = ENTITIES.register("oil_processing",
            () -> BlockEntityType.Builder.of(OilProcessingBlockEntity::new, OIL_REFINERY.get(), CHEMICAL_PLANT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteelFurnaceBlockEntity>> STEEL_FURNACE_BE = ENTITIES.register("steel_furnace",
            () -> BlockEntityType.Builder.of(SteelFurnaceBlockEntity::new, STEEL_FURNACE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteelChestBlockEntity>> STEEL_CHEST_BE = ENTITIES.register("steel_chest",
            () -> BlockEntityType.Builder.of(SteelChestBlockEntity::new, STEEL_CHEST.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<OilMenu>> PROCESSING_MENU = MENUS.register("oil_processing", () -> IMenuTypeExtension.create(OilMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<SteelChestMenu>> STEEL_CHEST_MENU = MENUS.register("steel_chest", () -> IMenuTypeExtension.create(SteelChestMenu::client));
    public static final Supplier<OilFieldFeature> OIL_FIELD = FEATURES.register("oil_field", OilFieldFeature::new);
    private OilRegistry() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); FEATURES.register(bus); MENUS.register(bus);
        bus.addListener(OilRegistry::capabilities);
        OilStartingArea.register();
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        FluidPorts.registerCapability(event, PUMPJACK_BE.get()); FluidPorts.registerCapability(event, PROCESSING_BE.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PROCESSING_BE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STEEL_FURNACE_BE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STEEL_CHEST_BE.get(), (be, side) -> be.itemHandler());
    }
}
