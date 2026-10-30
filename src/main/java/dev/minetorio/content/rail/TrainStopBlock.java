package dev.minetorio.content.rail;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
public final class TrainStopBlock extends Block {
    public TrainStopBlock(Properties properties) { super(properties); }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!state.is(next.getBlock())&&level instanceof ServerLevel server&&!RailwayManager.reconciling(server)) RailPlacement.destroyAttachment(server,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}
