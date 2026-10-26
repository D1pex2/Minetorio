package dev.minetorio.content.multiblock;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Invisible full-cube filler of a multiblock machine. Shared by all machines; has no item form.
 * Interaction, mining speed and picking delegate to the controller; removing a part dismantles
 * the whole machine (see {@link MultiblockControllerBlock#dismantle}).
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

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
        // Creative breaking must not drop the machine item; survival drops it from onRemove.
        if (!level.isClientSide() && player.isCreative()) dismantleController(level, pos, false);
        return result;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) dismantleController(level, pos, true);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void dismantleController(Level level, BlockPos partPos, boolean dropMachine) {
        BlockPos c = controllerPos(level, partPos);
        BlockState cs = controllerState(level, c);
        if (cs != null) ((MultiblockControllerBlock) cs.getBlock()).dismantle(level, c, cs, dropMachine);
    }
}
