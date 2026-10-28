package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.core.power.PoleType;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small electric pole: a thin post {@value #HEIGHT} blocks tall that connects by wire to every pole
 * within 7.5 blocks ({@link PoleType#SMALL}) and supplies the 5×5×5 cube around its lower
 * block (see {@link PowerGridManager}). Wires are drawn by the client from the connections in the block
 * entity's update tag.
 *
 * <p>The pole is one block per {@link #PART}: part 0 (the lower block, placed by the item) holds the
 * block entity; parts 1 and 2 above it are its middle and top, placed with it. Placing needs the two
 * cells above free of blocks and entities. Breaking any part breaks the whole pole, which drops one item (the loot table
 * drops only for part 0): an upper part's removal destroys the lower block with drops, and parts whose
 * block below is gone turn to air.
 */
public class ElectricPoleBlock extends BaseEntityBlock implements PowerPole {
    public static final MapCodec<ElectricPoleBlock> CODEC = simpleCodec(ElectricPoleBlock::new);
    /** Blocks the pole stands tall. */
    public static final int HEIGHT = 3;
    /** 0 = the lower block (with the block entity), 1 = middle, 2 = top. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, HEIGHT - 1);
    private static final VoxelShape POST = box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape TOP = box(6, 0, 6, 10, 15, 10);

    public ElectricPoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public PoleType poleType() { return PoleType.SMALL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(PART) == HEIGHT - 1 ? TOP : POST;
    }

    /** The lower block of the pole that {@code state} at {@code pos} belongs to. */
    public static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() + HEIGHT - 1 >= level.getMaxBuildHeight()) return null;
        for (int i = 1; i < HEIGHT; i++) {
            BlockPos cell = pos.above(i);
            if (!level.getBlockState(cell).canBeReplaced(ctx)) return null;
            // no entity (the player included) may stand where the upper parts go
            if (!level.isUnobstructed(defaultBlockState().setValue(PART, i), cell, CollisionContext.empty())) return null;
        }
        return defaultBlockState();
    }

    /** Whether the pole whose lower block is at {@code lower} stands full height (a pole from an older world is one block). */
    public static boolean isFullHeight(BlockGetter level, BlockPos lower) {
        BlockState above = level.getBlockState(lower.above());
        return above.getBlock() instanceof ElectricPoleBlock && above.getValue(PART) == 1;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        for (int i = 1; i < HEIGHT; i++) level.setBlock(pos.above(i), state.setValue(PART, i), Block.UPDATE_ALL);
    }

    /** An upper part stands only on the part below it. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        int part = state.getValue(PART);
        if (part > 0 && dir == Direction.DOWN && !(neighbor.is(this) && neighbor.getValue(PART) == part - 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** In creative, breaking an upper part removes the lower block first, so nothing drops. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) > 0) {
            BlockPos lower = lowerPos(pos, state);
            if (isLower(level.getBlockState(lower))) {
                level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** An upper part going away takes the lower block (and with it the rest) along, dropping the item. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && state.getValue(PART) > 0) {
            BlockPos lower = lowerPos(pos, state);
            if (isLower(level.getBlockState(lower))) level.destroyBlock(lower, true);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private boolean isLower(BlockState state) {
        return state.is(this) && state.getValue(PART) == 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new ElectricPoleBlockEntity(pos, state) : null;
    }
}
