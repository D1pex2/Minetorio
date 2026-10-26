package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.SplitterBlockEntity;
import dev.minetorio.content.belt.UndergroundBeltBlockEntity;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.multiblock.MultiblockPartBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Minetorio.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartBlockEntity>> MULTIBLOCK_PART =
            BLOCK_ENTITIES.register("multiblock_part", () -> BlockEntityType.Builder
                    .of(MultiblockPartBlockEntity::new, ModBlocks.MULTIBLOCK_PART.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneFurnaceBlockEntity>> STONE_FURNACE =
            BLOCK_ENTITIES.register("stone_furnace", () -> BlockEntityType.Builder
                    .of(StoneFurnaceBlockEntity::new, ModBlocks.STONE_FURNACE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltBlockEntity>> TRANSPORT_BELT =
            BLOCK_ENTITIES.register("transport_belt", () -> BlockEntityType.Builder
                    .of(BeltBlockEntity::new, ModBlocks.TRANSPORT_BELT.get(), ModBlocks.FAST_TRANSPORT_BELT.get(),
                            ModBlocks.EXPRESS_TRANSPORT_BELT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UndergroundBeltBlockEntity>> UNDERGROUND_BELT =
            BLOCK_ENTITIES.register("underground_belt", () -> BlockEntityType.Builder
                    .of(UndergroundBeltBlockEntity::new, ModBlocks.UNDERGROUND_BELT.get(), ModBlocks.FAST_UNDERGROUND_BELT.get(),
                            ModBlocks.EXPRESS_UNDERGROUND_BELT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SplitterBlockEntity>> SPLITTER =
            BLOCK_ENTITIES.register("splitter", () -> BlockEntityType.Builder
                    .of(SplitterBlockEntity::new, ModBlocks.SPLITTER.get(), ModBlocks.FAST_SPLITTER.get(),
                            ModBlocks.EXPRESS_SPLITTER.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
