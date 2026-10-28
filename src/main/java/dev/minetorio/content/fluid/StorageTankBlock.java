package dev.minetorio.content.fluid;

import com.mojang.serialization.MapCodec;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.core.fluid.FluidData;
import dev.minetorio.core.fluid.FluidNetwork;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Storage tank (3×3×2, 25 000): a segment node, so it shares the contents of the pipes it touches
 * (all at the same fill level). Four connections at two opposite corners, as Factorio's tank: the
 * front-right corner opens forward and right, the back-left corner (the controller) back and left.
 * Tanks whose connections touch link directly. Drawn by a block entity renderer.
 */
public class StorageTankBlock extends MultiblockControllerBlock implements FluidNodeBlock {
    public static final MapCodec<StorageTankBlock> CODEC = simpleCodec(StorageTankBlock::new);
    private static final List<FluidPort> PORTS = List.of(
            FluidPort.inputOutput(2, 2, FluidPort.Side.FRONT, null),
            FluidPort.inputOutput(2, 2, FluidPort.Side.RIGHT, null),
            FluidPort.inputOutput(0, 0, FluidPort.Side.BACK, null),
            FluidPort.inputOutput(0, 0, FluidPort.Side.LEFT, null));

    public StorageTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public MultiblockShape shape() { return MultiblockShape.STORAGE_TANK; }

    @Override
    public List<FluidPort> fluidPorts() { return PORTS; }

    @Override
    public double nodeCapacity() { return FluidData.STORAGE_TANK_CAPACITY; }

    /** Refused (with a message) if its connections would join segments holding different fluids. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null || !(ctx.getLevel() instanceof ServerLevel server)) return state;
        Direction facing = state.getValue(FACING);
        List<BlockPos> nodes = new ArrayList<>();
        for (FluidPort p : PORTS) {
            Direction side = p.side().resolve(facing);
            BlockPos cell = p.cell(ctx.getClickedPos(), facing);
            if (FluidConnections.endAt(server, cell.relative(side), side.getOpposite()) instanceof FluidConnections.NodeEnd n) nodes.add(n.pos());
        }
        return PipeBlock.refuseMix(server, nodes, ctx.getPlayer()) ? null : state;
    }

    /** Registers at once (not only when the block entity loads), so the tank is in its segment right after placing. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) FluidNetworkManager.get(server).queueNode(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        FluidNetwork.Segment s = FluidNetworkManager.get(server).segmentAt(pos);
        FluidNetworkManager.get(server).tell(sp, s == null ? Component.translatable("minetorio.fluid.empty") : FluidText.segment(s));
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageTankBlockEntity(pos, state);
    }
}
