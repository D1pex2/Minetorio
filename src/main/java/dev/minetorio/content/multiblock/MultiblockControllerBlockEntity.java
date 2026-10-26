package dev.minetorio.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Base block entity of a multiblock controller; holds the machine's state. */
public abstract class MultiblockControllerBlockEntity extends BlockEntity {
    /** Set while the structure is being torn down, so part/controller removals do not recurse. */
    boolean dismantling;

    protected MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Drops the machine's stored items (not the machine item itself) when the structure is
     * dismantled. Called once, server side, before the blocks are removed. Default: nothing.
     */
    protected void dropContents(Level level, BlockPos pos) {}
}
