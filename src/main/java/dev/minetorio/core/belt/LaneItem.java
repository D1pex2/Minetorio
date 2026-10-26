package dev.minetorio.core.belt;

/** An item on a lane; {@code pos} in [0, length], 0 is the lane entrance. */
public record LaneItem(String item, double pos) {}
