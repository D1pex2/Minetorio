package dev.minetorio.content.multiblock;

import dev.minetorio.content.registry.ModBlocks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Controller block of a multiblock machine: sits at the origin of {@link #shape()} (see
 * {@link MultiblockShape#footprint}) and owns the machine's block entity; every other position
 * is a {@link MultiblockPartBlock}. The controller's facing is the player's horizontal look
 * direction at placement, so the machine extends forward and to the right of the clicked spot.
 *
 * <p>Drops: the machine item comes from the controller's loot table through the normal block-drop
 * path of whichever piece is broken (a part's drops are its controller block's drops, see
 * {@link MultiblockPartBlock}); the rest of the machine is removed without drops by
 * {@link #dismantle}, which also drops the controller block entity's
 * {@link MultiblockControllerBlockEntity#dropContents contents} once.
 */
public abstract class MultiblockControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected MultiblockControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public abstract MultiblockShape shape();

    public static List<BlockPos> footprint(BlockPos origin, Direction facing, MultiblockShape shape) {
        return shape.footprint(origin, facing);
    }

    /** Positions occupied by the machine whose controller is {@code state} at {@code origin}. */
    public List<BlockPos> footprint(BlockPos origin, BlockState state) {
        return shape().footprint(origin, state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos origin = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection();
        for (BlockPos p : footprint(origin, facing, shape())) {
            if (!p.equals(origin) && !canOccupy(level, p)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    private static boolean canOccupy(Level level, BlockPos p) {
        return !level.isOutsideBuildHeight(p)
                && level.getBlockState(p).canBeReplaced()
                && level.isUnobstructed(null, Shapes.block().move(p.getX(), p.getY(), p.getZ()));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) placeParts(level, pos, state);
    }

    /** Fills the footprint with parts; positions that are not replaceable (e.g. after /setblock) are skipped. */
    private void placeParts(Level level, BlockPos origin, BlockState state) {
        BlockState part = ModBlocks.MULTIBLOCK_PART.get().defaultBlockState();
        for (BlockPos p : footprint(origin, state)) {
            if (p.equals(origin) || !level.getBlockState(p).canBeReplaced()) continue;
            level.setBlock(p, part, Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof MultiblockPartBlockEntity be) be.link(origin.subtract(p), this);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) dismantle(level, pos, state);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Tears the whole machine down (server side) after one of its pieces was removed: drops the
     * controller's contents, then removes the remaining parts and the controller without drops.
     * Re-entrant calls from the removals it triggers are ignored.
     */
    public void dismantle(Level level, BlockPos controllerPos, BlockState controllerState) {
        if (level.isClientSide()) return;
        if (!(level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity be) || be.dismantling) return;
        be.dismantling = true;
        be.dropContents(level, controllerPos);
        for (BlockPos p : footprint(controllerPos, controllerState)) {
            if (!p.equals(controllerPos) && level.getBlockState(p).getBlock() instanceof MultiblockPartBlock
                    && controllerPos.equals(MultiblockPartBlock.controllerPos(level, p))) {
                level.removeBlock(p, false);
            }
        }
        if (level.getBlockState(controllerPos).is(this)) level.removeBlock(controllerPos, false);
    }
}
