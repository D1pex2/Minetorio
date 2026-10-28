package dev.minetorio.content.fluid;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Pump (1×2, placed looking the way the fluid should go): takes fluid in through the back of the
 * controller (back cell) and pushes it out of the front of the front cell, 12 000/s at full power
 * (30 kW). It splits segments: what is behind and what is in front are separate segments, and the
 * pump ignores their throughput limits, so long lines need pumps.
 */
public class PumpBlock extends MultiblockControllerBlock implements FluidPortBlock {
    public static final MapCodec<PumpBlock> CODEC = simpleCodec(PumpBlock::new);
    /** Port indices: 0 input (back), 1 output (front). */
    private static final List<FluidPort> PORTS = List.of(
            FluidPort.input(0, 0, FluidPort.Side.BACK, null),
            FluidPort.output(0, 1, FluidPort.Side.FRONT, null));

    public PumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.PUMP; }

    @Override
    public List<FluidPort> fluidPorts() { return PORTS; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PumpBlockEntity be)) return InteractionResult.PASS;
        player.displayClientMessage(be.status(), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PumpBlockEntity(pos, state);
    }
}
