package dev.minetorio.content.belt;

import com.mojang.serialization.MapCodec;
import dev.minetorio.core.belt.BeltTier;
import dev.minetorio.core.belt.UndergroundLink;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Underground belt (all tiers). {@link #FACING} is the travel direction. On placement the piece
 * becomes an {@link Type#OUTPUT exit} if an unpaired entrance of the same tier and facing lies
 * behind it within {@code tier.undergroundMaxGap()} blocks of gap, otherwise an
 * {@link Type#INPUT entrance}; an entrance also pairs with an unpaired exit ahead of it.
 * The covered half (front of an entrance, back of an exit) is a hood.
 */
public class UndergroundBeltBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Type> TYPE = EnumProperty.create("type", Type.class);

    public enum Type implements StringRepresentable {
        INPUT, OUTPUT;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    private static final VoxelShape[] INPUT_SHAPES = new VoxelShape[4];
    private static final VoxelShape[] OUTPUT_SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape plate = Block.box(0, 0, 0, 16, 2, 16);
            INPUT_SHAPES[d.get2DDataValue()] = Shapes.or(plate, BeltGeometry.box(d, 0, 2, 0, 16, 8, 8));
            OUTPUT_SHAPES[d.get2DDataValue()] = Shapes.or(plate, BeltGeometry.box(d, 0, 2, 8, 16, 8, 16));
        }
    }

    private final BeltTier tier;
    private final MapCodec<UndergroundBeltBlock> codec;

    public UndergroundBeltBlock(BeltTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = simpleCodec(p -> new UndergroundBeltBlock(tier, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, Type.INPUT));
    }

    public BeltTier tier() { return tier; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int i = state.getValue(FACING).get2DDataValue();
        return state.getValue(TYPE) == Type.INPUT ? INPUT_SHAPES[i] : OUTPUT_SHAPES[i];
    }

    /**
     * Placing looking along the travel direction: an exit if an unpaired entrance of this tier facing
     * the same way lies behind (within range), otherwise an entrance facing the look direction.
     * Placing while looking back at an unpaired entrance ahead that points toward the player also
     * makes its exit (facing like the entrance), so the exit can be placed from either side.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction look = ctx.getHorizontalDirection();
        BlockState input = defaultBlockState().setValue(FACING, look).setValue(TYPE, Type.INPUT);
        if (findPartner(ctx.getLevel(), ctx.getClickedPos(), look, Type.OUTPUT).isPresent()) {
            return input.setValue(TYPE, Type.OUTPUT);
        }
        if (findPartner(ctx.getLevel(), ctx.getClickedPos(), look.getOpposite(), Type.OUTPUT).isPresent()) {
            return input.setValue(FACING, look.getOpposite()).setValue(TYPE, Type.OUTPUT);
        }
        return input;
    }

    /**
     * Distance to the nearest unpaired partner for a piece of type {@code type} at {@code pos}: an
     * entrance behind for an exit, an exit ahead for an entrance (same tier and facing).
     */
    public OptionalInt findPartner(BlockGetter level, BlockPos pos, Direction facing, Type type) {
        Direction toward = type == Type.OUTPUT ? facing.getOpposite() : facing;
        Type wanted = type == Type.OUTPUT ? Type.INPUT : Type.OUTPUT;
        int max = tier.undergroundMaxGap() + 1;
        List<Boolean> matches = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            BlockPos p = pos.relative(toward, i);
            BlockState s = level.getBlockState(p);
            matches.add(s.is(this) && s.getValue(FACING) == facing && s.getValue(TYPE) == wanted
                    && level.getBlockEntity(p) instanceof UndergroundBeltBlockEntity be && !be.isPaired());
        }
        return UndergroundLink.findEntranceDistance(matches, tier.undergroundMaxGap());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof UndergroundBeltBlockEntity be) be.tryLink();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UndergroundBeltBlockEntity(pos, state);
    }

    /** Drops the items on the lanes and unpairs the partner; the belt item comes from the loot table. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof UndergroundBeltBlockEntity be) {
            be.dropItemsAndUnlink();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        BeltBlock.pushEntity(level, pos, state, entity, state.getValue(FACING), tier.speed());
    }
}
