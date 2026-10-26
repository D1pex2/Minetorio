package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
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

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
