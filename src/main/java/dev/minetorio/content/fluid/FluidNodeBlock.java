package dev.minetorio.content.fluid;

/**
 * A multiblock with {@link FluidPortBlock ports} that is itself part of a pipe segment (a storage
 * tank): its controller block entity is a {@link FluidNodeEntity}, and its ports link to pipes and
 * other tanks instead of attaching boxes.
 */
public interface FluidNodeBlock extends FluidPortBlock {
    double nodeCapacity();
}
