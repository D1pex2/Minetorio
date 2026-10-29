package dev.minetorio.content.oil;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** A permanent oil-bearing surface cell. A pumpjack sits above it, leaving it intact. */
public final class OilSourceBlock extends BaseEntityBlock {
    public static final MapCodec<OilSourceBlock> CODEC = simpleCodec(OilSourceBlock::new);
    public OilSourceBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OilSourceBlockEntity(pos, state); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server) dev.minetorio.content.oilmap.OilMapData.remove(server,pos);
        super.onRemove(state,level,pos,next,moved);
    }
}
