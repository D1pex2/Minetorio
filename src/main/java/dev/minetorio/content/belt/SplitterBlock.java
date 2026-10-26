package dev.minetorio.content.belt;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.core.belt.BeltTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Splitter (all tiers): a 2×1 multiblock ({@link MultiblockShape#SPLITTER}) across the travel
 * direction {@link #FACING}. Placed looking along the travel direction, the clicked position becomes
 * the left column (controller) and the block to its right the right column (part). Each column is a
 * belt plate under a low housing (8 px) that spans both columns across their middle.
 */
public class SplitterBlock extends MultiblockControllerBlock {
    private static final VoxelShape[] LEFT_SHAPES = new VoxelShape[4];
    private static final VoxelShape[] RIGHT_SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape plate = Block.box(0, 0, 0, 16, 2, 16);
            // Low housing (8 px total) across the middle of both columns; belt open in front and behind.
            VoxelShape column = Shapes.or(plate, BeltGeometry.box(d, 0, 2, 5, 16, 8, 11));
            LEFT_SHAPES[d.get2DDataValue()] = column;
            RIGHT_SHAPES[d.get2DDataValue()] = column;
        }
    }

    private final BeltTier tier;
    private final MapCodec<SplitterBlock> codec;

    public SplitterBlock(BeltTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = simpleCodec(p -> new SplitterBlock(tier, p));
    }

    public BeltTier tier() { return tier; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.SPLITTER; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SplitterBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return LEFT_SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape partShape(BlockState controllerState, BlockPos partOffset) {
        return RIGHT_SHAPES[controllerState.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // The part is linked only now: let neighbouring belts re-check what feeds into them.
        if (!oldState.is(this)) {
            for (BlockPos p : footprint(pos, state)) {
                if (!p.equals(pos)) level.getBlockState(p).updateNeighbourShapes(level, p, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        BeltBlock.pushEntity(level, pos, state, entity, state.getValue(FACING), tier.speed());
    }

    @Override
    public void partEntityInside(BlockState controllerState, Level level, BlockPos partPos, Entity entity) {
        BeltBlock.pushEntity(level, partPos, controllerState, entity, controllerState.getValue(FACING), tier.speed());
    }
}
