package dev.minetorio.content.fluid;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.core.fluid.FluidStore;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Resolving the {@link FluidPort}s of placed machines. A port at cell C opening to side S connects
 * to whatever is at {@code C.relative(S)} facing back. Resolution needs only block states and part
 * links, never the machine's block entity, so pipes can update their shape while a machine is
 * being placed.
 */
public final class FluidPorts {
    private FluidPorts() {}

    /** A resolved port: the machine's controller position, the port's index, cell and side in the world. */
    public record Port(BlockPos controller, int index, BlockPos cell, Direction side, FluidPort spec) {
        public BlockPos neighbor() { return cell.relative(side); }

        @Nullable
        public String filter() { return spec.filter(); }
    }

    /** World cell of {@code spec} for the machine at {@code origin} facing {@code facing}. */
    public static BlockPos cell(BlockPos origin, Direction facing, FluidPort spec) {
        return spec.cell(origin, facing);
    }

    /** The machine port at {@code cell} opening to {@code side}, or null (no machine, no such port, chunk not loaded). */
    @Nullable
    public static Port portAt(BlockGetter level, BlockPos cell, Direction side) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(cell)) return null;
        BlockState state = level.getBlockState(cell);
        BlockPos controller;
        if (state.getBlock() instanceof MultiblockControllerBlock) {
            controller = cell;
        } else if (state.getBlock() instanceof MultiblockPartBlock) {
            controller = MultiblockPartBlock.controllerPos(level, cell);
            if (controller == null || level instanceof LevelReader reader && !reader.hasChunkAt(controller)) return null;
            state = level.getBlockState(controller);
        } else {
            return null;
        }
        if (!(state.getBlock() instanceof FluidPortBlock machine) || !state.hasProperty(MultiblockControllerBlock.FACING)) return null;
        Direction facing = state.getValue(MultiblockControllerBlock.FACING);
        List<FluidPort> specs = machine.fluidPorts();
        for (int i = 0; i < specs.size(); i++) {
            FluidPort s = specs.get(i);
            if (s.side().resolve(facing) == side && s.cell(controller, facing).equals(cell)) {
                return new Port(controller, i, cell, side, s);
            }
        }
        return null;
    }

    /** The first port at {@code cell} (any side), or null. */
    @Nullable
    public static Port anyPortAt(BlockGetter level, BlockPos cell) {
        for (Direction d : Direction.values()) {
            Port p = portAt(level, cell, d);
            if (p != null) return p;
        }
        return null;
    }

    /** All ports of the machine at {@code controller}, resolved (empty if it has none). */
    public static List<Port> ports(BlockGetter level, BlockPos controller) {
        BlockState state = level.getBlockState(controller);
        if (!(state.getBlock() instanceof FluidPortBlock machine) || !state.hasProperty(MultiblockControllerBlock.FACING)) return List.of();
        Direction facing = state.getValue(MultiblockControllerBlock.FACING);
        List<FluidPort> specs = machine.fluidPorts();
        Port[] out = new Port[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            FluidPort s = specs.get(i);
            out[i] = new Port(controller, i, s.cell(controller, facing), s.side().resolve(facing), s);
        }
        return List.of(out);
    }

    /** The machine behind {@code port}, or null (a tank, or its block entity is missing). */
    @Nullable
    public static FluidMachine machine(BlockGetter level, Port port) {
        return level.getBlockEntity(port.controller()) instanceof FluidMachine m ? m : null;
    }

    /** Whether {@code fluid} may pass a port with {@code filter}. */
    public static boolean passes(@Nullable String filter, @Nullable String fluid) {
        return filter == null || fluid == null || filter.equals(fluid);
    }

    /**
     * Fluid handler (for other mods) of the port at {@code cell} on {@code side} (any port of that
     * cell if side is null): the machine's box, or a tank's segment. Null if there is no port.
     */
    @Nullable
    public static IFluidHandler handler(Level level, BlockPos cell, @Nullable Direction side) {
        Port port = side == null ? anyPortAt(level, cell) : portAt(level, cell, side);
        if (port == null) return null;
        BlockEntity be = level.getBlockEntity(port.controller());
        if (be instanceof FluidMachine m) {
            return new FluidBoxHandler(() -> m.fluidBox(port.index()), port.filter(), m::fluidChanged);
        }
        if (be instanceof FluidNodeEntity node && level instanceof ServerLevel) {
            Supplier<FluidStore> store = () -> node.fluidNode().liveSegment();
            return new FluidBoxHandler(store, port.filter(), be::setChanged);
        }
        return null;
    }

    /** Registers the fluid capability of a machine's controller cell (part cells are covered by the part block entity). */
    public static <B extends BlockEntity> void registerCapability(RegisterCapabilitiesEvent event, BlockEntityType<B> type) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type,
                (be, side) -> be.getLevel() == null ? null : handler(be.getLevel(), be.getBlockPos(), side));
    }
}
