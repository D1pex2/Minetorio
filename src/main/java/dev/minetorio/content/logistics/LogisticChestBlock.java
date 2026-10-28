package dev.minetorio.content.logistics;

import com.mojang.serialization.MapCodec;
import dev.minetorio.core.logistics.LogisticMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Factorio logistic chest (1×1): passive provider, active provider, storage, requester or buffer
 * ({@link LogisticMode}). An item container like a vanilla chest (inserters and construction robots
 * use it too); right-click opens its inventory with the request slots, the storage filter or the
 * "request from buffer chests" toggle ({@link LogisticChestMenu}).
 */
public class LogisticChestBlock extends BaseEntityBlock {
    /** The model's outline: the box is a pixel in from the sides and 15 px tall. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    private final LogisticMode mode;

    public LogisticChestBlock(LogisticMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    public LogisticMode mode() { return mode; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new LogisticChestBlock(mode, p));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticChestBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LogisticChestBlockEntity) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        sp.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LogisticChestMenu(id, inventory, pos),
                Component.translatable(getDescriptionId())), pos);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LogisticChestBlockEntity be) {
            Containers.dropContents(level, pos, be.container());
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LogisticChestBlockEntity be
                ? net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(be.container()) : 0;
    }
}
