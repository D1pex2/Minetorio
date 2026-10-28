package dev.minetorio.content.fluid;

import dev.minetorio.content.registry.ModBlockEntities;
import dev.minetorio.core.fluid.FluidData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An underground pipe: a segment node like a pipe ({@link FluidData#UNDERGROUND_PIPE_CAPACITY}).
 * It has no settings of its own (its facing is the block state).
 */
public class UndergroundPipeBlockEntity extends PipeBlockEntity {
    public UndergroundPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNDERGROUND_PIPE.get(), pos, state, FluidData.UNDERGROUND_PIPE_CAPACITY);
    }
}
