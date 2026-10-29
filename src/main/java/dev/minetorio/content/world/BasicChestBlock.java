package dev.minetorio.content.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BasicChestBlock extends BaseEntityBlock {
    private final int size;
    public BasicChestBlock(int size, Properties properties) { super(properties); this.size = size; }
    public int size() { return size; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new BasicChestBlock(size, p)); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BasicChestBlockEntity(pos, state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BasicChestBlockEntity be)) return InteractionResult.PASS;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BasicChestMenu(id, inv, pos, be.inventory()), Component.translatable(getDescriptionId())), pos);
        return InteractionResult.CONSUME;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof BasicChestBlockEntity be) Containers.dropContents(level, pos, be.inventory());
        super.onRemove(state, level, pos, next, moving);
    }
}
