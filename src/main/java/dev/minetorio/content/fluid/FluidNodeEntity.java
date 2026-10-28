package dev.minetorio.content.fluid;

/**
 * A block entity that is a node of a pipe segment (pipe, underground pipe, storage tank
 * controller). Its {@link FluidNode} keeps the saved share and registers it with the manager.
 */
public interface FluidNodeEntity {
    FluidNode fluidNode();
}
