package dev.minetorio.content.oil;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.fluid.FluidPort;
import dev.minetorio.content.fluid.FluidPortBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class PumpjackBlock extends MultiblockControllerBlock implements FluidPortBlock {
    public static final MapCodec<PumpjackBlock> CODEC = simpleCodec(PumpjackBlock::new);
    public static final MultiblockShape SHAPE = new MultiblockShape(3, 3, 2);
    private static final List<FluidPort> PORTS = List.of(FluidPort.output(2, 2, FluidPort.Side.FRONT, "crude_oil"));
    public PumpjackBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public MultiblockShape shape() { return SHAPE; }
    @Override public List<FluidPort> fluidPorts() { return PORTS; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PumpjackBlockEntity(pos, state); }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        BlockPos center = SHAPE.anchorOf(ctx.getClickedPos(), state.getValue(FACING));
        return ctx.getLevel().getBlockState(center.below()).is(OilRegistry.OIL_SOURCE.get()) ? state : null;
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, OilRegistry.PUMPJACK_BE.get(), PumpjackBlockEntity::serverTick);
    }
}
