package dev.minetorio.content.research;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Lab controller (3×3×2). No GUI: right-click (any piece) with science packs inserts them by hand
 * (up to 64 of each); right-click otherwise shows the stored packs and the current research in the
 * action bar. Its block model is empty (particle only): body and dome are drawn by a block entity
 * renderer (R19).
 */
public class LabBlock extends MultiblockControllerBlock {
    public static final MapCodec<LabBlock> CODEC = simpleCodec(LabBlock::new);

    public LabBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.LAB; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.LAB.get(), level.isClientSide()
                ? (l, p, s, be) -> be.tickClient()
                : (l, p, s, be) -> be.tickServer(l));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!LabBlockEntity.isPack(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LabBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int n = be.insertManually(stack);
        if (n > 0) stack.consume(n, player);
        player.displayClientMessage(be.status(), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LabBlockEntity be)) return InteractionResult.PASS;
        player.displayClientMessage(be.status(), true);
        return InteractionResult.CONSUME;
    }
}
