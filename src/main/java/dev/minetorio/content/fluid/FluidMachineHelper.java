package dev.minetorio.content.fluid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Registers a {@link FluidMachine} block entity with the {@link FluidNetworkManager} of its level.
 * The owner calls {@link #onLoad} from {@code onLoad} and {@link #onRemoved} from {@code setRemoved}
 * and {@code onChunkUnloaded}. Server side only; harmless on the client.
 */
public final class FluidMachineHelper {
    private final BlockEntity owner;

    public FluidMachineHelper(BlockEntity owner) {
        if (!(owner instanceof FluidMachine)) throw new IllegalArgumentException(owner + " is not a FluidMachine");
        this.owner = owner;
    }

    public void onLoad() {
        if (owner.getLevel() instanceof ServerLevel level) FluidNetworkManager.get(level).registerMachine(owner.getBlockPos());
    }

    public void onRemoved() {
        if (owner.getLevel() instanceof ServerLevel level) {
            FluidNetworkManager m = FluidNetworkManager.getIfPresent(level);
            if (m != null) m.unregisterMachine(owner.getBlockPos());
        }
    }
}
