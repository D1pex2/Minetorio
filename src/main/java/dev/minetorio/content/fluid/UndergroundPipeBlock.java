package dev.minetorio.content.fluid;

import com.mojang.serialization.MapCodec;
import dev.minetorio.core.fluid.FluidData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Underground pipe (Factorio's pipe-to-ground), in any of the six directions: {@link #FACING} is its
 * open end, which connects like a pipe side; the tunnel runs the other way and pairs with the
 * nearest underground pipe on that line within {@link FluidData#UNDERGROUND_PIPE_REACH} blocks that
 * faces back ({@link #partner}). Vertical pairs (facing up or down) take fluid through floors.
 *
 * <p>Placing like Factorio: the open end faces the player and the tunnel goes where the player
 * looks; if an unpaired underground pipe behind points its tunnel this way, the new one becomes its
 * exit (its open end facing away).
 */
public class UndergroundPipeBlock extends BaseEntityBlock {
    public static final MapCodec<UndergroundPipeBlock> CODEC = simpleCodec(UndergroundPipeBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new java.util.EnumMap<>(Direction.class);

    static {
        VoxelShape body = box(3, 3, 3, 13, 13, 13);
        for (Direction d : Direction.values()) {
            SHAPES.put(d, Shapes.or(body, box(
                    d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 12 : 4.5,
                    d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 12 : 4.5,
                    d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 12 : 4.5,
                    d.getStepX() < 0 ? 4 : d.getStepX() > 0 ? 16 : 11.5,
                    d.getStepY() < 0 ? 4 : d.getStepY() > 0 ? 16 : 11.5,
                    d.getStepZ() < 0 ? 4 : d.getStepZ() > 0 ? 16 : 11.5)));
        }
    }

    public UndergroundPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    /**
     * The partner of the underground pipe at {@code pos} with open end {@code facing}: walking the
     * tunnel ({@code facing.getOpposite()}) up to the reach, the first underground pipe met, if it
     * faces back ({@code facing.getOpposite()}); one facing the same way blocks the tunnel.
     */
    @Nullable
    public static BlockPos partner(BlockGetter level, BlockPos pos, Direction facing) {
        Direction tunnel = facing.getOpposite();
        for (int k = 1; k <= FluidData.UNDERGROUND_PIPE_REACH; k++) {
            BlockPos q = pos.relative(tunnel, k);
            if (level instanceof LevelReader r && !r.hasChunkAt(q)) return null;
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof UndergroundPipeBlock) return s.getValue(FACING) == tunnel ? q : null;
        }
        return null;
    }

    /** Facing for a new underground pipe at {@code pos} placed looking {@code look} (see class doc). */
    public static Direction placementFacing(BlockGetter level, BlockPos pos, Direction look) {
        Direction back = look.getOpposite();
        for (int k = 1; k <= FluidData.UNDERGROUND_PIPE_REACH; k++) {
            BlockPos q = pos.relative(back, k);
            if (level instanceof LevelReader r && !r.hasChunkAt(q)) break;
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof UndergroundPipeBlock) {
                return s.getValue(FACING) == back ? look : back;
            }
        }
        return back;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = placementFacing(level, pos, ctx.getNearestLookingDirection());
        if (level instanceof ServerLevel server) {
            List<BlockPos> nodes = new ArrayList<>();
            if (FluidConnections.endAt(level, pos.relative(facing), facing.getOpposite()) instanceof FluidConnections.NodeEnd n) nodes.add(n.pos());
            BlockPos partner = partner(level, pos, facing);
            if (partner != null) nodes.add(partner);
            if (PipeBlock.refuseMix(server, nodes, ctx.getPlayer())) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) requeueAxis(server, pos, state.getValue(FACING));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock())) requeueAxis(server, pos, state.getValue(FACING));
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Pairings along the axis may have changed: re-link the underground pipes within reach both ways. */
    private static void requeueAxis(ServerLevel level, BlockPos pos, Direction facing) {
        FluidNetworkManager m = FluidNetworkManager.get(level);
        m.queueNode(pos);
        for (Direction d : new Direction[] {facing, facing.getOpposite()}) {
            for (int k = 1; k <= FluidData.UNDERGROUND_PIPE_REACH; k++) {
                BlockPos q = pos.relative(d, k);
                if (!level.isLoaded(q)) break;
                if (level.getBlockState(q).getBlock() instanceof UndergroundPipeBlock) m.queueNode(q);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UndergroundPipeBlockEntity(pos, state);
    }
}
