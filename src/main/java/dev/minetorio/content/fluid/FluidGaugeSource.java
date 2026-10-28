package dev.minetorio.content.fluid;

import dev.minetorio.core.fluid.FluidGauge;

/**
 * A block entity alt mode shows a fluid gauge over (fluid colour and level): pipes, underground
 * pipes, tanks and fluid machines. On the client it returns the last gauge received; the
 * {@link FluidNetworkManager} sends a block update when the gauge's step changes. Machines put
 * {@code FluidNode.writeGauge(tag, FluidGauge.of(altBox()))} into their update tag and read it back
 * with {@code FluidNode.readGauge}.
 */
public interface FluidGaugeSource {
    FluidGauge fluidGauge();
}
