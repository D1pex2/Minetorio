package dev.minetorio.content.fluid;

import dev.minetorio.core.fluid.FluidBox;
import java.util.function.DoubleSupplier;
import javax.annotation.Nullable;

/**
 * Implemented by the controller block entity of a machine whose block is a {@link FluidPortBlock}.
 * The machine owns one {@link FluidBox} per port (ports may share a box) and only works on its
 * boxes: it {@code insert}s what it produces into output boxes and {@code extract}s what it
 * consumes from input boxes, in its own tick. The {@link FluidNetworkManager} moves fluid between
 * the boxes and the pipe segments (or touching machine ports) after all block entities ticked,
 * following each port's {@link dev.minetorio.core.fluid.PortKind}.
 *
 * <p><b>How to make a fluid machine</b> (pumpjack, refinery, chemical plant):
 * <ol>
 *   <li>The controller block implements {@link FluidPortBlock} and lists its {@link FluidPort}s.</li>
 *   <li>The block entity implements this interface and creates a {@link FluidMachineHelper}: call
 *       its {@code onLoad} from {@code onLoad}, {@code onRemoved} from {@code setRemoved} and
 *       {@code onChunkUnloaded}. That registers the machine; ports attach automatically whenever
 *       pipes around it change.</li>
 *   <li>Save the boxes ({@code CoreNbt.writeFluid}/{@code loadFluidInto}).</li>
 *   <li>Register the fluid capability for other mods: {@code FluidPorts.registerCapability}.</li>
 *   <li>Alt-mode gauge: {@link #altBox()} (the default shows the first port's box).</li>
 * </ol>
 * Helpers for recipes: {@link FluidAccess}.
 */
public interface FluidMachine {
    /** The box behind port {@code port} (index into {@link FluidPortBlock#fluidPorts()}). */
    FluidBox fluidBox(int port);

    /** Called after fluid moved through one of the ports (mark the block entity for saving). */
    void fluidChanged();

    /**
     * Most the port moves per tick, read every tick (null: no limit of its own). E.g. a pump's
     * rate times its power satisfaction.
     */
    @Nullable
    default DoubleSupplier portRate(int port) { return null; }

    /** A pump port: exempt from connection and segment throughput limits, served first. */
    default boolean pumpPort(int port) { return false; }

    /** The box whose fluid and level alt mode shows over the machine, or null for none. */
    @Nullable
    default FluidBox altBox() { return fluidBox(0); }
}
