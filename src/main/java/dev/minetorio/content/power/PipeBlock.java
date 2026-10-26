package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.registry.ModBlockEntities;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pipe: connects (block state per side) to every neighboring pipe and to every machine fluid port
 * that opens toward it (see {@link FluidPorts}). The block entity holds the fluid and does the flow.
 */
public class PipeBlock extends BaseEntityBlock {
    public static final MapCodec<PipeBlock> CODEC = simpleCodec(PipeBlock::new);
    public static final Map<Direction, BooleanProperty> SIDES = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape s = CORE;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) == 0) continue;
                s = Shapes.or(s, box(
                        d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 11 : 5,
                        d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 11 : 5,
                        d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 11 : 5,
                        d.getStepX() < 0 ? 5 : d.getStepX() > 0 ? 16 : 11,
                        d.getStepY() < 0 ? 5 : d.getStepY() > 0 ? 16 : 11,
                        d.getStepZ() < 0 ? 5 : d.getStepZ() > 0 ? 16 : 11));
            }
            SHAPES[mask] = s;
        }
    }

    public PipeBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any();
        for (BooleanProperty p : SIDES.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES.values().toArray(BooleanProperty[]::new));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(SIDES.get(d))) mask |= 1 << d.ordinal();
        }
        return SHAPES[mask];
    }

    /** Whether a pipe at {@code pos} connects toward {@code side}: a pipe there, or a machine port opening back toward it. */
    public static boolean connects(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos n = pos.relative(side);
        if (level.getBlockState(n).getBlock() instanceof PipeBlock) return true;
        return FluidPorts.portAt(level, n, side.getOpposite()) != null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.values()) s = s.setValue(SIDES.get(d), connects(ctx.getLevel(), ctx.getClickedPos(), d));
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(SIDES.get(side), connects(level, pos, side));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlockEntities.PIPE.get() ? null
                : (l, p, s, be) -> ((PipeBlockEntity) be).tickServer(l, s);
    }
}
