package dev.minetorio.content.belt;

import com.mojang.serialization.MapCodec;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.BeltTier;
import dev.minetorio.core.belt.UndergroundLink;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.phys.BlockHitResult;
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

    /** Farthest exit distance from its entrance: gap + 1. */
    public int maxDistance() {
        return tier.undergroundMaxGap() + 1;
    }

    /** Links the new piece and tells a placing player what happened (action bar). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof UndergroundBeltBlockEntity be)) return;
        be.tryLink();
        if (level.isClientSide() || !(placer instanceof Player player)) return;
        if (be.isPaired()) {
            player.displayClientMessage(Component.translatable("minetorio.underground.connected", be.distance()), true);
        } else if (state.getValue(TYPE) == Type.INPUT) {
            player.displayClientMessage(Component.translatable("minetorio.underground.entrance", maxDistance()), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, lines, flag);
        lines.add(Component.translatable("minetorio.underground.tooltip.1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("minetorio.underground.tooltip.2", maxDistance()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Fast-replace with another tier. A paired piece swaps both pieces (needs 2 items and enough
     * range for the current distance), an unpaired one swaps alone; pairing and contents are kept.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        UndergroundBeltBlock next = FastReplace.target(stack, this, UndergroundBeltBlock.class, player, level, pos);
        if (next == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof UndergroundBeltBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockPos partnerPos = be.partnerPos();
        UndergroundBeltBlockEntity partner = partnerPos != null
                && level.getBlockEntity(partnerPos) instanceof UndergroundBeltBlockEntity p && pos.equals(p.partnerPos()) ? p : null;
        if (partner != null && !level.mayInteract(player, partnerPos)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int pieces = partner != null ? 2 : 1;
        if (partner != null && be.distance() > next.maxDistance()) {
            FastReplace.refuse(player, "minetorio.fast_replace.range");
            return ItemInteractionResult.CONSUME;
        }
        if (!FastReplace.canPay(player, stack, pieces)) {
            FastReplace.refuse(player, "minetorio.fast_replace.need_two");
            return ItemInteractionResult.CONSUME;
        }
        int distance = be.distance();
        BlockState placed = replacePiece(level, pos, state, be, next, distance);
        if (partner != null) replacePiece(level, partnerPos, level.getBlockState(partnerPos), partner, next, distance);
        FastReplace.finish(level, pos, placed, player, stack, this, pieces);
        return ItemInteractionResult.CONSUME;
    }

    private static BlockState replacePiece(Level level, BlockPos pos, BlockState state, UndergroundBeltBlockEntity old,
                                           UndergroundBeltBlock next, int distance) {
        BeltLane[] lanes = old.takeForReplace();
        BlockState placed = next.defaultBlockState().setValue(FACING, state.getValue(FACING)).setValue(TYPE, state.getValue(TYPE));
        level.setBlock(pos, placed, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof UndergroundBeltBlockEntity be) {
            be.restore(lanes[0], lanes[1], distance);
        } else {
            for (BeltLane lane : lanes) LaneStacks.drop(level, pos, lane);
        }
        return placed;
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
