package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Accumulator controller (2×2×2, Factorio's 2×2). Drawn by {@code AccumulatorRenderer} (block model
 * particle only), with charge-level lamps. Right-click shows its charge in the action bar.
 */
public class AccumulatorBlock extends MultiblockControllerBlock {
    public static final MapCodec<AccumulatorBlock> CODEC = simpleCodec(AccumulatorBlock::new);
    public static final MultiblockShape SHAPE = new MultiblockShape(2, 2, 2);

    public AccumulatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccumulatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ACCUMULATOR.get(), (l, p, s, be) -> be.tickServer(l));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof AccumulatorBlockEntity be)) return InteractionResult.PASS;
        player.displayClientMessage(be.status(), true);
        return InteractionResult.CONSUME;
    }
}
