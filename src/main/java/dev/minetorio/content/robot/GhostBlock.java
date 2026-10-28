package dev.minetorio.content.robot;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An entity ghost: the translucent plan of a block ({@link GhostBlockEntity}) that construction
 * robots build ({@link ConstructionRobotEntity}), or a player by right-clicking it with the matching
 * item (one item is used; placed through the normal placement path, see {@link Ghosts#build}).
 * No collision, no drops, broken in one hit, and replaceable: placing any other block into it
 * replaces it. Its outline is the target block's shape (one block for a multiblock, whose ghost sits
 * at the controller position). Drawn by a block entity renderer only.
 */
public class GhostBlock extends BaseEntityBlock {
    public static final MapCodec<GhostBlock> CODEC = simpleCodec(GhostBlock::new);

    public GhostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GhostBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof GhostBlockEntity be && be.isValid()
                && !(be.target().getBlock() instanceof MultiblockControllerBlock)) {
            VoxelShape s = be.target().getShape(level, pos, ctx);
            if (!s.isEmpty()) return s;
        }
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GhostBlockEntity be) || !be.isValid() || !stack.is(be.item())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server) {
            if (!Ghosts.build(server, pos, player, stack.copyWithCount(1))) return ItemInteractionResult.FAIL;
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** A belt ghost re-plans its shape when a neighbour changes (diagonal ramp neighbours: {@link Ghosts#refreshAround}). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide()) Ghosts.refresh(level, pos);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof GhostBlockEntity be && be.isValid() ? new ItemStack(be.item()) : ItemStack.EMPTY;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Nullable
    public static GhostBlockEntity at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GhostBlockEntity be ? be : null;
    }
}
