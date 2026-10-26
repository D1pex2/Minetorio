package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.machine.StoneFurnaceBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Minetorio.MODID);

    /** Shared invisible filler of every multiblock; mining speed is delegated to the controller. */
    public static final DeferredBlock<MultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.registerBlock("multiblock_part",
            MultiblockPartBlock::new, machine(MapColor.NONE).noLootTable());

    public static final DeferredBlock<StoneFurnaceBlock> STONE_FURNACE = BLOCKS.registerBlock("stone_furnace",
            StoneFurnaceBlock::new, machine(MapColor.STONE));

    private ModBlocks() {}

    /** Properties shared by machine controllers and parts: stone-like, see-through to light, immovable. */
    private static BlockBehaviour.Properties machine(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(3.5f)
                .sound(SoundType.STONE)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
