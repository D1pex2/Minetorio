package dev.minetorio.content.inserter;

import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;

/**
 * Implemented by a machine's block entity (a multiblock controller) to talk to inserters and
 * drills directly instead of through an {@code IItemHandler}. Items are stack keys (see
 * {@code LaneStacks}). The source offers only the machine's outputs; the target accepts inputs,
 * fuel and science packs with the machine's automated limits ({@code automated = true}).
 * Implemented by the machines of Tasks 13–16.
 */
public interface MachineEndpoint {
    PickupSource asSource();

    DropTarget asTarget();
}
