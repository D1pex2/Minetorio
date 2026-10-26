package dev.minetorio.content.belt;

import com.mojang.serialization.MapCodec;
import dev.minetorio.core.belt.BeltShape;
import dev.minetorio.core.belt.BeltTier;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Transport belt (all tiers): a 2 px high plate. {@link #FACING} is the travel direction (the
 * player's horizontal look direction at placement). {@link #SHAPE} follows the neighbours: a curve
 * when nothing feeds from behind and exactly one side feeds in ({@link BeltShape#of}).
 * Entities standing on it ride along at the belt's speed; sneaking players do not.
 */
public class BeltBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    private static final VoxelShape PLATE = Block.box(0, 0, 0, 16, 2, 16);

    /** Block-state mirror of {@link BeltShape}. */
    public enum Shape implements StringRepresentable {
        STRAIGHT(BeltShape.STRAIGHT), CURVE_LEFT(BeltShape.CURVE_LEFT), CURVE_RIGHT(BeltShape.CURVE_RIGHT);

        private final BeltShape core;

        Shape(BeltShape core) { this.core = core; }

        public BeltShape core() { return core; }

        public static Shape of(BeltShape core) { return values()[core.ordinal()]; }

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    private final BeltTier tier;
    private final MapCodec<BeltBlock> codec;

    public BeltBlock(BeltTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = simpleCodec(p -> new BeltBlock(tier, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.STRAIGHT));
    }

    public BeltTier tier() { return tier; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return PLATE; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withShape(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()));
    }

    /** {@code state} with {@link #SHAPE} recomputed from what feeds into {@code pos}. */
    public static BlockState withShape(BlockGetter level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        boolean back = BeltGeometry.outputsInto(level, pos.relative(f.getOpposite()), f);
        boolean left = BeltGeometry.outputsInto(level, pos.relative(f.getCounterClockWise()), f.getClockWise());
        boolean right = BeltGeometry.outputsInto(level, pos.relative(f.getClockWise()), f.getCounterClockWise());
        return state.setValue(SHAPE, Shape.of(BeltShape.of(back, left, right)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return dir.getAxis().isHorizontal() ? withShape(level, pos, state) : state;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltBlockEntity(pos, state);
    }

    /** Drops the items on the lanes; the belt item itself comes from the loot table. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BeltBlockEntity be) be.dropItems();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        pushEntity(level, pos, state, entity, state.getValue(FACING), tier.speed());
    }

    /**
     * Makes an entity standing on the belt cell at {@code pos} ride at {@code speed} blocks/tick in
     * {@code travel}, on top of its own motion. Ground friction f multiplies the velocity after each
     * move, so adding speed·(1−f)/f per tick converges to exactly {@code speed}. Players are moved
     * by their own client (the server never pushes them) and are not pushed while sneaking.
     */
    public static void pushEntity(Level level, BlockPos pos, BlockState state, Entity entity, Direction travel, double speed) {
        if (!entity.onGround() || entity.getY() - pos.getY() > 0.5) return;
        if (!BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()).equals(pos)) return;
        if (entity instanceof Player player && (!level.isClientSide() || player.isShiftKeyDown())) return;
        double f = state.getFriction(level, pos, entity) * (entity instanceof ItemEntity ? 0.98 : 0.91);
        double dv = speed * (1 - f) / f;
        Vec3 dm = entity.getDeltaMovement();
        entity.setDeltaMovement(dm.x + travel.getStepX() * dv, dm.y, dm.z + travel.getStepZ() * dv);
    }
}
