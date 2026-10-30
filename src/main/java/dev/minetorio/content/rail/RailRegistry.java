package dev.minetorio.content.rail;

import dev.minetorio.Minetorio;
import dev.minetorio.core.item.ItemStackSizes;
import net.minecraft.world.item.Item;
import dev.minetorio.core.data.RailData.StockKind;
import net.minecraft.world.entity.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Предметы каталога; типы блоков и подвижного состава добавляются вместе с их реализацией. */
public final class RailRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Minetorio.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Minetorio.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Minetorio.MODID);
    public static final DeferredHolder<Block,RailCellBlock> CELL = BLOCKS.register("rail_cell",()->new RailCellBlock(BlockBehaviour.Properties.of().strength(.7f).noOcclusion().noLootTable()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<RailCellBlockEntity>> CELL_ENTITY = BLOCK_ENTITIES.register("rail_cell",()->BlockEntityType.Builder.of(RailCellBlockEntity::new,CELL.get()).build(null));
    public static final DeferredItem<Item> RAIL = ITEMS.register("rail",()->new RailItem(new Item.Properties().stacksTo(100)));
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,Minetorio.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS=DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,Minetorio.MODID);
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<Double>> STOCK_HEALTH=COMPONENTS.registerComponentType("stock_health",b->b.persistent(com.mojang.serialization.Codec.doubleRange(0.000000001,1)).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.DOUBLE));
    public static final DeferredHolder<EntityType<?>,EntityType<RollingStockEntity>> STOCK=ENTITIES.register("rolling_stock",()->EntityType.Builder.of(RollingStockEntity::new,MobCategory.MISC).sized(1.8f,2.5f).clientTrackingRange(16).updateInterval(1).build("rolling_stock"));
    public static final DeferredHolder<Block,TrainStopBlock> STOP_BLOCK=BLOCKS.register("train_stop",()->new TrainStopBlock(BlockBehaviour.Properties.of().strength(1).noOcclusion().noLootTable()));
    public static final DeferredHolder<Block,RailSignalBlock> SIGNAL_BLOCK=BLOCKS.register("rail_signal",()->new RailSignalBlock(BlockBehaviour.Properties.of().strength(.5f).noOcclusion().noLootTable()));
    public static final DeferredHolder<Block,RailSignalBlock> CHAIN_BLOCK=BLOCKS.register("rail_chain_signal",()->new RailSignalBlock(BlockBehaviour.Properties.of().strength(.5f).noOcclusion().noLootTable()));
    public static final DeferredItem<Item> LOCOMOTIVE = stock("locomotive",StockKind.LOCOMOTIVE);
    public static final DeferredItem<Item> CARGO_WAGON = stock("cargo_wagon",StockKind.CARGO);
    public static final DeferredItem<Item> FLUID_WAGON = stock("fluid_wagon",StockKind.FLUID);
    public static final DeferredItem<Item> TRAIN_STOP = attachment("train_stop",0);
    public static final DeferredItem<Item> RAIL_SIGNAL = attachment("rail_signal",1);
    public static final DeferredItem<Item> RAIL_CHAIN_SIGNAL = attachment("rail_chain_signal",2);

    private RailRegistry() {}

    private static DeferredItem<Item> stock(String id,StockKind kind) { return ITEMS.register(id,()->new RollingStockItem(new Item.Properties().stacksTo(5),kind)); }
    private static DeferredItem<Item> attachment(String id,int kind) { return ITEMS.register(id,()->new RailAttachmentItem(new Item.Properties().stacksTo(kind==0?10:50),kind)); }
    public static Item stockItem(StockKind kind) { return switch(kind) { case LOCOMOTIVE->LOCOMOTIVE.get();case CARGO->CARGO_WAGON.get();case FLUID->FLUID_WAGON.get(); }; }
    public static String stockRecipe(StockKind kind) { return switch(kind) { case LOCOMOTIVE->"locomotive";case CARGO->"cargo_wagon";case FLUID->"fluid_wagon"; }; }
    private static DeferredItem<Item> item(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(ItemStackSizes.limit(Minetorio.MODID + ":" + id)));
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); BLOCKS.register(bus); BLOCK_ENTITIES.register(bus); ENTITIES.register(bus); COMPONENTS.register(bus); RailwayManager.register(); }
}
