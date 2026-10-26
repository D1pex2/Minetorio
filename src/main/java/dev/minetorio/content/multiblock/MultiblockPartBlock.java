package dev.minetorio.content.multiblock;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible filler of a multiblock machine. Shared by all machines; has no item form.
 * Shape (full cube by default), entity contact, interaction, mining speed, picking and drops
 * delegate to the controller; removing a part
 * dismantles the whole machine (see {@link MultiblockControllerBlock#dismantle}).
 */
public class MultiblockPartBlock extends BaseEntityBlock {
    public static final MapCodec<MultiblockPartBlock> CODEC = simpleCodec(MultiblockPartBlock::new);

    public MultiblockPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // the controller's model covers the whole machine
    }

    /** Controller position of the part at {@code pos}, or null if {@code pos} is not a linked part. */
    @Nullable
    public static BlockPos controllerPos(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity be && be.isLinked() ? be.controllerPos() : null;
    }

    /** State of the controller at {@code controller}, or null if there is no controller block there. */
    @Nullable
    private static BlockState controllerState(BlockGetter level, @Nullable BlockPos controller) {
        if (controller == null) return null;
        BlockState state = level.getBlockState(controller);
        return state.getBlock() instanceof MultiblockControllerBlock ? state : null;
    }

    /** The controller decides each part's shape ({@link MultiblockControllerBlock#partShape}); full block if unlinked. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockPos c = controllerPos(level, pos);
        BlockState cs = controllerState(level, c);
        return cs == null ? Shapes.block() : ((MultiblockControllerBlock) cs.getBlock()).partShape(cs, pos.subtract(c));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        BlockPos c = controllerPos(level, pos);
        BlockState cs = controllerState(level, c);
        if (cs != null) ((MultiblockControllerBlock) cs.getBlock()).partEntityInside(cs, level, pos, entity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos c = controllerPos(level, pos);
        BlockState cs = controllerState(level, c);
        return cs == null ? InteractionResult.PASS : cs.useWithoutItem(level, player, hit.withPosition(c));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos c = controllerPos(level, pos);
        BlockState cs = controllerState(level, c);
        return cs == null ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : cs.useItemOn(stack, level, player, hand, hit.withPosition(c));
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockPos c = controllerPos(level, pos);
        BlockState cs = controllerState(level, c);
        return cs == null ? super.getDestroyProgress(state, player, level, pos) : cs.getDestroyProgress(player, level, c);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockPos c = controllerPos(level, pos);
        BlockState cs = controllerState(level, c);
        return cs == null ? ItemStack.EMPTY : cs.getCloneItemStack(target, level, c, player);
    }

    /**
     * A part drops what its controller block would drop (the machine item), with this break's loot
     * context (tool, breaker, explosion). The controller itself is then removed without drops.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MultiblockPartBlockEntity be
                && be.controllerBlock() != null) {
            return be.controllerBlock().defaultBlockState().getDrops(params);
        }
        return List.of();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos c = controllerPos(level, pos);
            BlockState cs = controllerState(level, c);
            if (cs != null) ((MultiblockControllerBlock) cs.getBlock()).dismantle(level, c, cs);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
