package dev.minetorio.content.fluid;

import dev.minetorio.core.fluid.PortKind;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One fluid port of a multiblock machine, declared in the controller's frame (facing north):
 * the cell {@code w} blocks to the right, {@code d} blocks forward (toward the facing) and {@code h}
 * blocks up from the controller (see {@code MultiblockShape#footprint}), opening to {@code side}.
 * A pipe, underground pipe, tank port or another machine's port in the neighbouring cell on that
 * side, facing back, connects to it. Only {@code filter} may pass (null: any fluid).
 *
 * <p>Ports turn with the machine: {@link #cell} and {@link Side#resolve} give the world cell and
 * side for a facing. Two ports may share one box (a pass-through like the boiler's water): the
 * machine maps each port index to a box ({@link FluidMachine#fluidBox}).
 *
 * <p>Example (a 3×3 chemical plant, Factorio layout facing north): inputs at the back corners,
 * outputs at the front corners:
 * <pre>{@code
 * List.of(FluidPort.input(0, 0, Side.BACK, null), FluidPort.input(2, 0, Side.BACK, null),
 *         FluidPort.output(0, 2, Side.FRONT, null), FluidPort.output(2, 2, Side.FRONT, null));
 * }</pre>
 */
public record FluidPort(int w, int d, int h, Side side, PortKind kind, @Nullable String filter) {
    /** A port side relative to the machine's facing. */
    public enum Side {
        FRONT, BACK, LEFT, RIGHT, UP, DOWN;

        public Direction resolve(Direction facing) {
            return switch (this) {
                case FRONT -> facing;
                case BACK -> facing.getOpposite();
                case LEFT -> facing.getCounterClockWise();
                case RIGHT -> facing.getClockWise();
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
        }
    }

    public static FluidPort input(int w, int d, Side side, @Nullable String filter) {
        return new FluidPort(w, d, 0, side, PortKind.INPUT, filter);
    }

    public static FluidPort output(int w, int d, Side side, @Nullable String filter) {
        return new FluidPort(w, d, 0, side, PortKind.OUTPUT, filter);
    }

    public static FluidPort inputOutput(int w, int d, Side side, @Nullable String filter) {
        return new FluidPort(w, d, 0, side, PortKind.INPUT_OUTPUT, filter);
    }

    /** The same port on layer {@code h}. */
    public FluidPort atHeight(int h) {
        return new FluidPort(w, d, h, side, kind, filter);
    }

    /** World cell of this port for a machine with its controller at {@code origin} facing {@code facing}. */
    public BlockPos cell(BlockPos origin, Direction facing) {
        return origin.relative(facing.getClockWise(), w).relative(facing, d).above(h);
    }
}
