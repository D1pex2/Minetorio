package dev.minetorio.content.machine;

import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Stone furnace controller state. Empty until the furnace logic task. */
public class StoneFurnaceBlockEntity extends MultiblockControllerBlockEntity {
    public StoneFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STONE_FURNACE.get(), pos, state);
    }
}
