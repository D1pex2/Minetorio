package dev.minetorio.core.fluid;

/**
 * How a machine's fluid port exchanges with what it is connected to (Factorio's production types).
 * <ul>
 *   <li>{@link #INPUT}: the machine drains fluid into its box until the box is full (a consumer).
 *       All inputs on one segment get equal shares when there is not enough for everyone.</li>
 *   <li>{@link #OUTPUT}: the machine pushes its box into the segment as long as there is room.</li>
 *   <li>{@link #INPUT_OUTPUT}: a pass-through box (boiler water, steam engine): it evens its fill
 *       level with every segment it touches, both ways, like a piece of pipe, so machines can be
 *       chained (boiler to boiler, engine to engine).</li>
 * </ul>
 */
public enum PortKind {
    INPUT, OUTPUT, INPUT_OUTPUT;

    /** Fluid may enter the machine through this port. */
    public boolean takes() { return this != OUTPUT; }

    /** Fluid may leave the machine through this port. */
    public boolean gives() { return this != INPUT; }
}
