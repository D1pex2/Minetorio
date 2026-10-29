package dev.minetorio.content.oil;

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

public final class SteelChestBlock extends BaseEntityBlock {
    public static final MapCodec<SteelChestBlock> CODEC=simpleCodec(SteelChestBlock::new);
    public SteelChestBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) { return new SteelChestBlockEntity(p,s); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(pos) instanceof SteelChestBlockEntity be))return InteractionResult.PASS;
        player.openMenu(new SimpleMenuProvider((id,inv,p)->new SteelChestMenu(id,inv,pos,be.inventory()),Component.translatable(getDescriptionId())),pos);
        return InteractionResult.CONSUME;
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        if(!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof SteelChestBlockEntity be) Containers.dropContents(level,pos,be.inventory());
        super.onRemove(state,level,pos,next,moved);
    }
}
