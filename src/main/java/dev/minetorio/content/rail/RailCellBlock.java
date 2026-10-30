package dev.minetorio.content.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Клетка хранит только проекцию ссылок; выгрузка не удаляет мировой путь. */
public final class RailCellBlock extends BaseEntityBlock {
    public static final MapCodec<RailCellBlock> CODEC = simpleCodec(RailCellBlock::new);
    public RailCellBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RailCellBlockEntity(pos,state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(0,0,0,16,2,16); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server && !RailwayManager.reconciling(server)) RailPlacement.destroyCell(server,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}
