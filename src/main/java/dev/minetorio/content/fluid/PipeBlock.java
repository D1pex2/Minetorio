package dev.minetorio.content.fluid;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pipe, connecting in full 3D: each of its six sides is {@link PipeSide#PIPE} where something
 * connects ({@link FluidConnections}: another pipe, an underground pipe's open end, a machine or
 * tank port facing it), {@link PipeSide#NONE} where nothing does, or {@link PipeSide#CAP} where the
 * player closed it: Shift + right-click with an empty hand on a side toggles it ({@link #toggle}),
 * so two lines with different fluids can run side by side. Closed sides are kept in the block
 * state (and copied by blueprints, see {@link PipeBlockEntity}); a closed side never connects.
 *
 * <p>Placing a pipe that would join segments holding different fluids is refused with a message.
 */
public class PipeBlock extends BaseEntityBlock {
    public static final MapCodec<PipeBlock> CODEC = simpleCodec(PipeBlock::new);
    public static final Map<Direction, EnumProperty<PipeSide>> SIDES = new EnumMap<>(Direction.class);
    private static final VoxelShape CORE = box(4, 4, 4, 12, 12, 12);
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (Direction d : Direction.values()) SIDES.put(d, EnumProperty.create(d.getSerializedName(), PipeSide.class));
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape s = CORE;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) == 0) continue;
                s = Shapes.or(s, box(
                        d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? 12 : 4.5,
                        d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? 12 : 4.5,
                        d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? 12 : 4.5,
                        d.getStepX() < 0 ? 4 : d.getStepX() > 0 ? 16 : 11.5,
                        d.getStepY() < 0 ? 4 : d.getStepY() > 0 ? 16 : 11.5,
                        d.getStepZ() < 0 ? 4 : d.getStepZ() > 0 ? 16 : 11.5));
            }
            SHAPES[mask] = s;
        }
    }

    public PipeBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any();
        for (EnumProperty<PipeSide> p : SIDES.values()) s = s.setValue(p, PipeSide.NONE);
        registerDefaultState(s);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction d : Direction.values()) builder.add(SIDES.get(d));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    public static PipeSide side(BlockState state, Direction d) { return state.getValue(SIDES.get(d)); }

    /** Whether side {@code d} is not closed. */
    public static boolean isOpen(BlockState state, Direction d) { return side(state, d) != PipeSide.CAP; }

    /** Bit mask of the closed sides (bit = {@link Direction#ordinal()}). */
    public static int closedMask(BlockState state) {
        int mask = 0;
        for (Direction d : Direction.values()) if (!isOpen(state, d)) mask |= 1 << d.ordinal();
        return mask;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int mask = 0;
        for (Direction d : Direction.values()) if (side(state, d) == PipeSide.PIPE) mask |= 1 << d.ordinal();
        return SHAPES[mask];
    }

    /** The state with every open side recomputed from the neighbours; closed sides stay closed. */
    public static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!isOpen(state, d)) continue;
            state = state.setValue(SIDES.get(d), FluidConnections.connectsToward(level, pos, d) ? PipeSide.PIPE : PipeSide.NONE);
        }
        return state;
    }

    /** Applies a closed-sides mask (blueprints): those sides closed, the others open and recomputed. */
    public static BlockState withClosedMask(BlockState state, int mask, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(SIDES.get(d), (mask & (1 << d.ordinal())) != 0 ? PipeSide.CAP : PipeSide.NONE);
        }
        return withConnections(state, level, pos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState s = withConnections(defaultBlockState(), level, pos);
        if (level instanceof ServerLevel server) {
            List<BlockPos> nodes = new ArrayList<>();
            for (Direction d : Direction.values()) {
                if (FluidConnections.endAt(level, pos.relative(d), d.getOpposite()) instanceof FluidConnections.NodeEnd n) nodes.add(n.pos());
            }
            if (refuseMix(server, nodes, ctx.getPlayer())) return null;
        }
        return s;
    }

    /** True (and tells the player) if connecting to {@code nodes} would mix fluids. */
    static boolean refuseMix(ServerLevel level, List<BlockPos> nodes, @Nullable Player player) {
        FluidNetworkManager m = FluidNetworkManager.get(level);
        Set<String> fluids = m.fluidsOf(nodes);
        if (fluids.size() < 2) return false;
        if (player instanceof ServerPlayer sp) m.tell(sp, FluidNetworkManager.mixMessage(fluids));
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!isOpen(state, side)) return state;
        return state.setValue(SIDES.get(side), FluidConnections.connectsToward(level, pos, side) ? PipeSide.PIPE : PipeSide.NONE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && (!oldState.is(this) || closedMask(oldState) != closedMask(state))) {
            FluidNetworkManager.get(server).queueNode(pos);
        }
    }

    /** Shift + right-click with an empty hand: toggles the side that was clicked. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            dev.minetorio.content.machineui.MachineUi.open(player, level.getBlockEntity(pos));
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        toggle(level, pos, clickedSide(pos, hit.getLocation()), player);
        return InteractionResult.CONSUME;
    }

    /**
     * The pipe side a click at {@code hit} means: the axis where the hit lies farthest from the
     * block's center (an arm's side face still means that arm; the core's face means that side).
     */
    public static Direction clickedSide(BlockPos pos, Vec3 hit) {
        double x = hit.x - pos.getX() - 0.5, y = hit.y - pos.getY() - 0.5, z = hit.z - pos.getZ() - 0.5;
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax >= ay && ax >= az) return x >= 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return y >= 0 ? Direction.UP : Direction.DOWN;
        return z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Closes side {@code side} of the pipe at {@code pos}, or opens it again. Opening is refused if
     * it would join two segments holding different fluids. Server side; returns whether it changed.
     */
    public static boolean toggle(Level level, BlockPos pos, Direction side, @Nullable Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock) || !(level instanceof ServerLevel server)) return false;
        boolean opening = !isOpen(state, side);
        if (opening) {
            BlockState open = state.setValue(SIDES.get(side), PipeSide.NONE);
            List<BlockPos> nodes = new ArrayList<>(List.of(pos));
            if (FluidConnections.endAt(level, pos.relative(side), side.getOpposite()) instanceof FluidConnections.NodeEnd n) nodes.add(n.pos());
            if (refuseMix(server, nodes, player)) return false;
            level.setBlock(pos, withConnections(open, level, pos), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, state.setValue(SIDES.get(side), PipeSide.CAP), Block.UPDATE_ALL);
        }
        if (player instanceof ServerPlayer sp) {
            FluidNetworkManager.get(server).tell(sp, Component.translatable(opening ? "minetorio.pipe.side_opened" : "minetorio.pipe.side_closed",
                    Component.translatable("minetorio.direction." + side.getSerializedName())));
        }
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }
}
