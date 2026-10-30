package dev.minetorio.content.rail;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
public final class RailSignalBlock extends Block {
    public static final IntegerProperty ASPECT=IntegerProperty.create("aspect",0,5);
    public RailSignalBlock(Properties properties) { super(properties);registerDefaultState(stateDefinition.any().setValue(ASPECT,0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(ASPECT); }
    @Override public void playerDestroy(Level level,net.minecraft.world.entity.player.Player player,BlockPos pos,BlockState state,net.minecraft.world.level.block.entity.BlockEntity entity,net.minecraft.world.item.ItemStack tool) {
        super.playerDestroy(level,player,pos,state,entity,tool);
        if(!level.isClientSide&&!player.isCreative())player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(state.is(RailRegistry.CHAIN_BLOCK.get())?RailRegistry.RAIL_CHAIN_SIGNAL.get():RailRegistry.RAIL_SIGNAL.get()));
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(!state.is(next.getBlock())&&level instanceof ServerLevel server&&!RailwayManager.reconciling(server)) RailPlacement.destroyAttachment(server,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}
