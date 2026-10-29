package dev.minetorio.content.world;

import dev.minetorio.Minetorio;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

/** Factorio's ordinary chests, with independent storage and automation. */
public final class BasicChests {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Minetorio.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Minetorio.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Minetorio.MODID);
    public static final DeferredBlock<BasicChestBlock> WOODEN = BLOCKS.registerBlock("wooden_chest", p -> new BasicChestBlock(16,p), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2).sound(SoundType.WOOD));
    public static final DeferredBlock<BasicChestBlock> IRON = BLOCKS.registerBlock("iron_chest", p -> new BasicChestBlock(32,p), BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> WOODEN_ITEM = ITEMS.registerSimpleBlockItem(WOODEN, new Item.Properties().stacksTo(50));
    public static final DeferredItem<BlockItem> IRON_ITEM = ITEMS.registerSimpleBlockItem(IRON, new Item.Properties().stacksTo(50));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicChestBlockEntity>> CHEST = ENTITIES.register("basic_chest", () -> BlockEntityType.Builder.of(BasicChestBlockEntity::new, WOODEN.get(), IRON.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BasicChestMenu>> MENU = MENUS.register("basic_chest", () -> IMenuTypeExtension.create(BasicChestMenu::client));
    private BasicChests() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus);
        bus.addListener(BasicChests::capabilities);
    }
    private static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHEST.get(), (be, side) -> be.itemHandler());
    }
}
