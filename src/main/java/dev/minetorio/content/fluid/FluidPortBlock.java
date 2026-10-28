package dev.minetorio.content.fluid;

import java.util.List;

/**
 * Implemented by the controller block of a multiblock machine with fluid ports (it must be a
 * {@link dev.minetorio.content.multiblock.MultiblockControllerBlock}, whose FACING turns the ports).
 * Pipes connect to the machine only through these ports.
 *
 * <p>Its block entity implements {@link FluidMachine} (a machine with boxes), unless the block is
 * a {@link FluidNodeBlock} (a storage tank: the ports lead into a segment node).
 */
public interface FluidPortBlock {
    /** The ports, in a fixed order (indices are what {@link FluidMachine#fluidBox} gets). */
    List<FluidPort> fluidPorts();
}
