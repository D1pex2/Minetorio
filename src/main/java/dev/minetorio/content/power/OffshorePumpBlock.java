package dev.minetorio.content.power;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Offshore pump controller (1×2×1). Placed from the shore looking at the water: the facing points
 * toward the water, and the pump can only be placed when the cell just past its far end (its back,
 * {@link #waterPos}) holds a water source. Its output port opens toward the placer (the near cell's
 * side facing away from the water). Block model on the controller (2 long, fits one JSON model).
 */
public class OffshorePumpBlock extends SteamMachineBlock {
    public static final MapCodec<OffshorePumpBlock> CODEC = simpleCodec(OffshorePumpBlock::new);
    private static final List<FluidPorts.Spec> PORTS = List.of(new FluidPorts.Spec(0, 0, FluidPorts.Side.BACK, "water"));

    public OffshorePumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.OFFSHORE_PUMP; }

    @Override
    public List<FluidPorts.Spec> fluidPorts() { return PORTS; }

    /** The cell the pump draws from: just past its far end. */
    public static BlockPos waterPos(BlockPos controller, Direction facing) {
        return controller.relative(facing, MultiblockShape.OFFSHORE_PUMP.depth());
    }

    /** Whether {@code pos} holds a water source block (or a waterlogged block). */
    public static boolean isWaterSource(BlockGetter level, BlockPos pos) {
        FluidState f = level.getFluidState(pos);
        return f.is(FluidTags.WATER) && f.isSource();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        Direction facing = state.getValue(MultiblockControllerBlock.FACING);
        return isWaterSource(ctx.getLevel(), waterPos(ctx.getClickedPos(), facing)) ? state : null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OffshorePumpBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends SteamMachineBlockEntity> blockEntityType() {
        return ModBlockEntities.OFFSHORE_PUMP.get();
    }
}
