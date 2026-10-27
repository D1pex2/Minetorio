package dev.minetorio.content.belt;

import com.mojang.serialization.MapCodec;
import dev.minetorio.core.belt.BeltShape;
import dev.minetorio.core.belt.BeltSlope;
import dev.minetorio.core.belt.BeltTier;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import dev.minetorio.core.belt.BeltLane;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Transport belt (all tiers): a 2 px high plate. {@link #FACING} is the travel direction (the
 * player's horizontal look direction at placement). {@link #SHAPE} follows the neighbours: a curve
 * when nothing feeds from behind and exactly one side feeds in ({@link BeltShape#of}).
 * {@link #SLOPE} makes a straight belt a ramp like a vanilla rail (R25, {@link #withShape}).
 * Entities standing on it ride along at the belt's speed; sneaking players do not.
 */
public class BeltBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    public static final EnumProperty<Slope> SLOPE = EnumProperty.create("slope", Slope.class);
    private static final VoxelShape PLATE = Block.box(0, 0, 0, 16, 2, 16);
    /** How high (blocks) an entity carried up a ramp hops at its top to get onto the next belt. */
    private static final double TOP_HOP = 0.35;
    /** Ramp outlines/collision per slope and facing: 8 steps of 2 px (see {@link #rampShape}). */
    private static final Map<Slope, Map<Direction, VoxelShape>> RAMPS = new EnumMap<>(Slope.class);

    static {
        for (Slope slope : new Slope[] {Slope.ASCENDING, Slope.DESCENDING}) {
            Map<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);
            for (Direction f : Direction.Plane.HORIZONTAL) byFacing.put(f, rampShape(f, slope.core()));
            RAMPS.put(slope, byFacing);
        }
    }

    /** Block-state mirror of {@link BeltSlope}. */
    public enum Slope implements StringRepresentable {
        FLAT(BeltSlope.FLAT), ASCENDING(BeltSlope.ASCENDING), DESCENDING(BeltSlope.DESCENDING);

        private final BeltSlope core;

        Slope(BeltSlope core) { this.core = core; }

        public BeltSlope core() { return core; }

        public static Slope of(BeltSlope core) { return values()[core.ordinal()]; }

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

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
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.STRAIGHT)
                .setValue(SLOPE, Slope.FLAT));
    }

    public BeltTier tier() { return tier; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, SLOPE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Slope slope = state.getValue(SLOPE);
        return slope == Slope.FLAT ? PLATE : RAMPS.get(slope).get(state.getValue(FACING));
    }

    /**
     * A ramp as a stair of 8 steps of 2 px run, each as high as the ramp surface at its middle,
     * capped at 15 px so an entity on the ramp always overlaps this block (and gets pushed): the
     * last step up to the next belt (3 px) is taken by the entity's step height.
     */
    private static VoxelShape rampShape(Direction facing, BeltSlope slope) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < 8; i++) {
            // step i spans z = 14-2i .. 16-2i of a NORTH-facing block; its middle is at t = (2i+1)/16
            double t = (2 * i + 1) / 16.0;
            double top = Math.min(2 + 16 * slope.rise(t), 15);
            shape = Shapes.or(shape, BeltGeometry.box(facing, 0, 0, 14 - 2 * i, 16, top, 16 - 2 * i));
        }
        return shape.optimize();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withShape(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()));
    }

    /**
     * {@code state} with {@link #SHAPE} and {@link #SLOPE} recomputed from its surroundings (R25,
     * like vanilla rails):
     * <ul>
     *   <li>DESCENDING when nothing at this level feeds it from behind, the cell behind and one up
     *       outputs into the cell above this belt (a flat or descending belt, an underground exit,
     *       a splitter, facing the same way), and that cell holds no belt-like block of its own.
     *       A descending ramp is always straight;</li>
     *   <li>otherwise the shape follows the inputs at this level, an ascending ramp behind and one
     *       down counting as the back input ({@link BeltShape#of});</li>
     *   <li>ASCENDING when straight, the cell in front holds no belt-like block (else this belt
     *       feeds that one) and the cell in front and one up holds a belt facing the same way that
     *       is not itself descending.</li>
     * </ul>
     * Ramp neighbours sit diagonally (vanilla shape updates do not reach them), so placing or
     * removing a belt refreshes the belts around it one level up and down ({@link #refreshRamps}).
     */
    public static BlockState withShape(BlockGetter level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        BlockPos behind = pos.relative(f.getOpposite());
        BlockPos front = pos.relative(f);
        boolean sameLevelBack = BeltGeometry.outputsInto(level, behind, f);
        boolean fedFromAbove = !sameLevelBack && BeltGeometry.outputsInto(level, behind.above(), f)
                && !BeltGeometry.isBeltLike(level, pos.above());
        boolean back = sameLevelBack || BeltGeometry.rampsInto(level, behind.below(), f);
        boolean left = BeltGeometry.outputsInto(level, pos.relative(f.getCounterClockWise()), f.getClockWise());
        boolean right = BeltGeometry.outputsInto(level, pos.relative(f.getClockWise()), f.getCounterClockWise());
        BeltShape shape = fedFromAbove ? BeltShape.STRAIGHT : BeltShape.of(back, left, right);
        BlockState upper = level.getBlockState(front.above());
        boolean feedsUpward = !BeltGeometry.isBeltLike(level, front) && upper.getBlock() instanceof BeltBlock
                && upper.getValue(FACING) == f && upper.getValue(SLOPE) != Slope.DESCENDING;
        BeltSlope slope = BeltSlope.of(fedFromAbove, feedsUpward, shape == BeltShape.STRAIGHT);
        return state.setValue(SHAPE, Shape.of(shape)).setValue(SLOPE, Slope.of(slope));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return dir != Direction.DOWN ? withShape(level, pos, state) : state;
    }

    /**
     * Re-evaluates the belts diagonally one block up or down next to {@code pos} (the ramp
     * neighbours of {@link #withShape}); changed states cascade through their own updates.
     */
    static void refreshRamps(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy += 2) {
                BlockPos p = pos.offset(d.getStepX(), dy, d.getStepZ());
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof BeltBlock)) continue;
                BlockState n = withShape(level, p, s);
                if (n != s) level.setBlock(p, n, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock()) || oldState.getValue(SLOPE) != state.getValue(SLOPE)) refreshRamps(level, pos);
    }

    /**
     * Fast-replace: right-clicking with a belt of another tier swaps this belt in place, keeping
     * facing and lane contents (shape and slope are recomputed from the same neighbours, so they
     * stay too); the old belt item goes back to the player (not in creative).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BeltBlock next = FastReplace.target(stack, this, BeltBlock.class, player, level, pos);
        if (next == null || !FastReplace.canPay(player, stack, 1)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BeltBlockEntity old)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BeltLane[] lanes = old.takeLanes();
        BlockState placed = withShape(level, pos, next.defaultBlockState().setValue(FACING, state.getValue(FACING)));
        level.setBlock(pos, placed, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof BeltBlockEntity be) {
            be.restoreLanes(lanes[0], lanes[1]);
        } else {
            for (BeltLane lane : lanes) LaneStacks.drop(level, pos, lane);
        }
        FastReplace.finish(level, pos, placed, player, stack, this, 1);
        return ItemInteractionResult.CONSUME;
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
        if (!(newState.getBlock() instanceof BeltBlock)) refreshRamps(level, pos);
    }

    /**
     * Item entities on the belt are taken onto the lane nearest to them, one item per contact while
     * the lane has room (R21); whatever is left of the stack, and every other entity, rides along.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        BeltSlope slope = state.getValue(SLOPE).core();
        Direction facing = state.getValue(FACING);
        if (!level.isClientSide() && entity instanceof ItemEntity item && isStandingOn(pos, entity, facing, slope)
                && level.getBlockEntity(pos) instanceof BeltBlockEntity be) {
            be.absorb(item);
            if (item.isRemoved()) return;
        }
        pushEntity(level, pos, entity, facing, slope, tier.speed());
    }

    /** Whether {@code entity} stands on top of the flat belt cell at {@code pos}. */
    static boolean isStandingOn(BlockPos pos, Entity entity) {
        return entity.onGround() && entity.getY() - pos.getY() <= 0.5
                && BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()).equals(pos);
    }

    /**
     * Whether {@code entity} stands on the belt cell at {@code pos} facing {@code facing}: on a flat
     * belt {@link #isStandingOn(BlockPos, Entity)}; on a ramp its feet are in this cell and it is on
     * the ground or hovers at most 0.25 above the highest ramp surface under it (entities carried
     * up a ramp hop over its stair steps, see
     * {@link #pushEntity(Level, BlockPos, Entity, Direction, BeltSlope, double)}).
     */
    static boolean isStandingOn(BlockPos pos, Entity entity, Direction facing, BeltSlope slope) {
        if (slope == BeltSlope.FLAT) return isStandingOn(pos, entity);
        if (!BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()).equals(pos)) return false;
        return entity.onGround() || entity.getY() - pos.getY() <= rampSurface(pos, entity, facing, slope) + 0.25;
    }

    /** Height of the ramp surface (above the block's bottom) under the higher edge of {@code entity}. */
    static double rampSurface(BlockPos pos, Entity entity, Direction facing, BeltSlope slope) {
        return 2.0 / 16 + slope.rise(Math.max(0, Math.min(1, frontEdge(pos, entity, facing, slope))));
    }

    /** Lane fraction (0 back .. 1 front edge of the cell) of the uphill edge of {@code entity}'s box. */
    private static double frontEdge(BlockPos pos, Entity entity, Direction facing, BeltSlope slope) {
        double along = (entity.getX() - pos.getX() - 0.5) * facing.getStepX()
                + (entity.getZ() - pos.getZ() - 0.5) * facing.getStepZ() + 0.5;
        return along + Math.signum(slope.grade()) * entity.getBbWidth() / 2;
    }

    /**
     * Makes an entity standing on the belt cell at {@code pos} ride at {@code speed} blocks/tick in
     * {@code travel}, on top of its own motion. Ground friction f (of the block the entity's movement
     * code uses: {@link Entity#getBlockPosBelowThatAffectsMyMovement}, under the thin belt) multiplies
     * the velocity after each move, so adding speed·(1−f)/f per tick converges to exactly
     * {@code speed}. Players are moved by their own client (the server never pushes them) and are not
     * pushed while sneaking.
     */
    public static void pushEntity(Level level, BlockPos pos, BlockState state, Entity entity, Direction travel, double speed) {
        pushEntity(level, pos, entity, travel, BeltSlope.FLAT, speed);
    }

    /**
     * {@link #pushEntity(Level, BlockPos, BlockState, Entity, Direction, double)} on a belt with
     * {@code slope}. Mobs and players climb an ascending ramp's 2 px stair steps by their step
     * height. Other entities that cannot step up (items, armor stands) are carried up the 45° slope
     * instead: their velocity along {@code travel} is set to {@code speed} and their vertical
     * velocity raised to {@code speed}, so they rise as fast as the ramp (gravity pulls them back
     * after each move) and hop over the steps; at the top they hop {@link #TOP_HOP} high onto the
     * next belt.
     */
    public static void pushEntity(Level level, BlockPos pos, Entity entity, Direction travel, BeltSlope slope, double speed) {
        if (!isStandingOn(pos, entity, travel, slope)) return;
        if (entity instanceof Player player && (!level.isClientSide() || player.isShiftKeyDown())) return;
        Vec3 dm = entity.getDeltaMovement();
        if (slope == BeltSlope.ASCENDING && !(entity instanceof Player) && entity.maxUpStep() < 2.0 / 16) {
            double along = dm.x * travel.getStepX() + dm.z * travel.getStepZ();
            double dv = speed - along;
            double lift = speed;
            // at the top the entity's front meets the next belt's 3 px higher plate: hop onto it
            if (frontEdge(pos, entity, travel, slope) >= 1) lift = Math.sqrt(2 * entity.getGravity() * TOP_HOP) + entity.getGravity();
            entity.setDeltaMovement(dm.x + travel.getStepX() * dv, Math.max(dm.y, lift), dm.z + travel.getStepZ() * dv);
            return;
        }
        if (!entity.onGround()) return;
        BlockPos below = entity.getBlockPosBelowThatAffectsMyMovement();
        double f = level.getBlockState(below).getFriction(level, below, entity) * (entity instanceof ItemEntity ? 0.98 : 0.91);
        double dv = speed * (1 - f) / f;
        entity.setDeltaMovement(dm.x + travel.getStepX() * dv, dm.y, dm.z + travel.getStepZ() * dv);
    }
}
