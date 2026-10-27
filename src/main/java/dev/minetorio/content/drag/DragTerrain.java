package dev.minetorio.content.drag;

import dev.minetorio.core.drag.DragDir;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a drag line follows the ground (shared by the server placement and the client ghost, so
 * both agree): each cell sits on the level of the cell before it, one block up when a full
 * terrain block stands in the way and the cell above it is free, one block down when the ground
 * drops by exactly one block. Placed belts then turn into ramps by the normal neighbour rule
 * ({@code BeltBlock.withShape}), just as when the same belts are placed by hand.
 */
public final class DragTerrain {
    private DragTerrain() {}

    /** A cell a drag can build into: replaceable (air, grass, snow layer) and without fluid. */
    public static boolean isFree(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** A block the line climbs over instead of skipping: a full block with no block entity (terrain). */
    static boolean isStep(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity() && state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Level of the drag cell in column ({@code x}, {@code z}) after a cell at level {@code prevY}:
     * <ul>
     *   <li>{@code prevY} when that cell is free and stands on something;</li>
     *   <li>{@code prevY - 1} when it is free, the cell below it is free and stands on something
     *       (a one-block drop);</li>
     *   <li>{@code prevY + 1} when a terrain block fills it and the cell above is free (a one-block
     *       rise);</li>
     *   <li>otherwise {@code prevY}: a deeper drop is bridged level, an obstacle is skipped.</li>
     * </ul>
     */
    public static int cellY(BlockGetter level, int x, int z, int prevY) {
        BlockPos at = new BlockPos(x, prevY, z);
        if (isFree(level, at)) {
            if (!isFree(level, at.below())) return prevY;
            return isFree(level, at.below(2)) ? prevY : prevY - 1;
        }
        return isStep(level, at) && isFree(level, at.above()) ? prevY + 1 : prevY;
    }

    public static Direction toMc(DragDir d) {
        return Direction.valueOf(d.name());
    }

    public static DragDir fromMc(Direction d) {
        return DragDir.valueOf(d.name());
    }
}
