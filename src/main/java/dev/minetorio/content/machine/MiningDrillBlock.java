package dev.minetorio.content.machine;

import javax.annotation.Nullable;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Controller block shared by the mining drills: ticking, the status line on right-click with an
 * empty hand (parts delegate here), and dropping the cached deposit when a neighbor changes.
 */
public abstract class MiningDrillBlock extends MultiblockControllerBlock {
    protected MiningDrillBlock(Properties properties) {
        super(properties);
    }

    protected abstract BlockEntityType<? extends MiningDrillBlockEntity> blockEntityType();

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        return beType != blockEntityType() ? null : level.isClientSide()
                ? (l, p, s, be) -> MiningDrillBlockEntity.clientTick(l, p, s, (MiningDrillBlockEntity) be)
                : (l, p, s, be) -> MiningDrillBlockEntity.serverTick(l, p, s, (MiningDrillBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof MiningDrillBlockEntity be)) return InteractionResult.PASS;
        player.displayClientMessage(be.status(), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof MiningDrillBlockEntity be) be.invalidateDeposit();
    }
}
