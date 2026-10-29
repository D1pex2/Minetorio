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

    /** On an explicit recipe change, return incompatible residual fluid to the connected pipe if it fits. */
    public void returnIncompatibleFluids(java.util.Set<String> nextInputs, java.util.Set<String> nextOutputs) {
        if (!(owner.getLevel() instanceof ServerLevel level) || !(owner instanceof FluidMachine machine)) return;
        FluidNetworkManager manager = FluidNetworkManager.get(level);
        for (FluidPorts.Port port : FluidPorts.ports(level, owner.getBlockPos())) {
            var box = machine.fluidBox(port.index());
            if (box == null || box.fluid() == null) continue;
            boolean keep = switch (port.spec().kind()) {
                case INPUT -> nextInputs.contains(box.fluid());
                case OUTPUT -> nextOutputs.contains(box.fluid());
                case INPUT_OUTPUT -> nextInputs.contains(box.fluid()) || nextOutputs.contains(box.fluid());
            };
            if (keep || !level.isLoaded(port.neighbor())) continue;
            // segmentAt validates an actual connected node; the side still has to be open to this port.
            FluidConnections.End end = FluidConnections.endAt(level, port.neighbor(), port.side().getOpposite());
            if (!(end instanceof FluidConnections.NodeEnd)) continue;
            var segment = manager.segmentAt(port.neighbor());
            if (segment != null && FluidAccess.transfer(box, segment, box.amount()) > 0) owner.setChanged();
        }
    }
}
