package dev.minetorio.content.power;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.FluidFlow;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Fluid ports of multiblock machines. A machine's controller block lists its ports
 * ({@link Machine#fluidPorts()}) as (local cell, side, fluid filter) in the controller's frame; its
 * block entity ({@link Box}) maps each port to one of its {@link FluidBox}es. A port at cell C on
 * side S connects to whatever is at {@code C.relative(S)}: a pipe, or another machine's port at that
 * cell facing back (S opposite). Connections need only block states and part links, never the
 * machine's block entity, so pipes can update their shape while a machine is being placed.
 *
 * <p><b>Flow.</b> Once per tick per connected pair: pipes flow with neighbor pipes (the pipe with
 * the smaller position does it) and with ports (the pipe does it); two machines whose ports touch
 * flow from the machine with the smaller controller position ({@link #flowToMachines}). A port's
 * filter (e.g. "steam") blocks any other fluid, so water can never enter a steam box and vice versa.
 */
public final class FluidPorts {
    private FluidPorts() {}

    /** A port side relative to the machine's facing. */
    public enum Side {
        FRONT, BACK, LEFT, RIGHT;

        public Direction resolve(Direction facing) {
            return switch (this) {
                case FRONT -> facing;
                case BACK -> facing.getOpposite();
                case LEFT -> facing.getCounterClockWise();
                case RIGHT -> facing.getClockWise();
            };
        }
    }

    /**
     * A port in the controller's frame: cell {@code w} blocks to the right and {@code d} blocks
     * forward of the controller (bottom layer, see {@code MultiblockShape#footprint}), opening to
     * {@code side}; only {@code filter} may pass (null: any fluid).
     */
    public record Spec(int w, int d, Side side, @Nullable String filter) {}

    /** Implemented by a machine's controller block. */
    public interface Machine {
        List<Spec> fluidPorts();
    }

    /** Implemented by that machine's controller block entity: the box behind each port (index into {@link Machine#fluidPorts()}). */
    public interface Box {
        FluidBox fluidBox(int port);

        /** Called after fluid moved through one of the ports (mark for saving). */
        void fluidChanged();
    }

    /** A resolved port: the machine's controller position, the port's index, cell and side in the world. */
    public record Port(BlockPos controller, int index, BlockPos cell, Direction side, @Nullable String filter) {
        public BlockPos neighbor() { return cell.relative(side); }
    }

    /** World cell of a port of the machine with its controller at {@code origin} facing {@code facing}. */
    public static BlockPos cell(BlockPos origin, Direction facing, Spec spec) {
        return origin.relative(facing.getClockWise(), spec.w()).relative(facing, spec.d());
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
        if (!(state.getBlock() instanceof Machine machine)) return null;
        Direction facing = state.getValue(MultiblockControllerBlock.FACING);
        List<Spec> specs = machine.fluidPorts();
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            if (s.side().resolve(facing) == side && cell(controller, facing, s).equals(cell)) {
                return new Port(controller, i, cell, side, s.filter());
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

    /** The box behind {@code port}, or null if its block entity is missing. */
    @Nullable
    public static Box owner(BlockGetter level, Port port) {
        return level.getBlockEntity(port.controller()) instanceof Box b ? b : null;
    }

    /** Whether {@code fluid} may pass a port with {@code filter}. */
    public static boolean passes(@Nullable String filter, @Nullable String fluid) {
        return filter == null || fluid == null || filter.equals(fluid);
    }

    /**
     * {@link FluidFlow#flow} between {@code a} and {@code b} if the fluid that would move passes
     * both filters. Returns whether anything moved.
     */
    public static boolean flow(FluidBox a, @Nullable String filterA, FluidBox b, @Nullable String filterB) {
        String fluid = a.fluid() != null ? a.fluid() : b.fluid();
        if (fluid == null || !passes(filterA, fluid) || !passes(filterB, fluid)) return false;
        double before = a.amount();
        FluidFlow.flow(a, b);
        return a.amount() != before;
    }

    /**
     * Flows each port of the machine at {@code controller} with a touching port of another machine,
     * if this machine's controller position is the smaller one (each pair once per tick).
     */
    public static void flowToMachines(Level level, BlockPos controller, Direction facing, Machine machine, Box self) {
        List<Spec> specs = machine.fluidPorts();
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            Direction side = s.side().resolve(facing);
            BlockPos neighbor = cell(controller, facing, s).relative(side);
            if (!level.isLoaded(neighbor)) continue;
            Port other = portAt(level, neighbor, side.getOpposite());
            if (other == null || other.controller().equals(controller) || controller.compareTo(other.controller()) > 0) continue;
            Box otherBox = owner(level, other);
            if (otherBox == null) continue;
            if (flow(self.fluidBox(i), s.filter(), otherBox.fluidBox(other.index()), other.filter())) {
                self.fluidChanged();
                otherBox.fluidChanged();
            }
        }
    }

    /** Fluid handler of the port at {@code cell} on {@code side} (any port of that cell if side is null), or null. */
    @Nullable
    public static IFluidHandler handler(Level level, BlockPos cell, @Nullable Direction side) {
        Port port = side == null ? anyPortAt(level, cell) : portAt(level, cell, side);
        if (port == null) return null;
        Box box = owner(level, port);
        return box == null ? null : new FluidBoxHandler(box.fluidBox(port.index()), port.filter(), box::fluidChanged);
    }
}
