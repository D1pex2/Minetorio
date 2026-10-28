package dev.minetorio.core.robot;

/**
 * What a deconstruction planner marks (Factorio's "trees and rocks" toggle): Minetorio blocks only
 * (default), Minetorio blocks plus trees and rocks, or trees and rocks only. "Trees and rocks" are
 * the natural blocks of the {@code minetorio:deconstructible_nature} block tag (logs, leaves,
 * boulders, huge mushrooms), never plain terrain.
 */
public enum DeconstructionFilter {
    MACHINES(true, false),
    MACHINES_AND_NATURE(true, true),
    NATURE_ONLY(false, true);

    private final boolean machines;
    private final boolean nature;

    DeconstructionFilter(boolean machines, boolean nature) {
        this.machines = machines;
        this.nature = nature;
    }

    /** Whether Minetorio blocks (machines, belts, inserters, poles ...) are marked. */
    public boolean machines() { return machines; }

    /** Whether trees and rocks are marked. */
    public boolean nature() { return nature; }

    /** The next mode of the GUI's cycle button. */
    public DeconstructionFilter next() { return values()[(ordinal() + 1) % values().length]; }

    public static DeconstructionFilter byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : MACHINES;
    }

    /** Whether a block of that kind is marked. */
    public boolean accepts(boolean isMinetorioBlock, boolean isNature) {
        return (machines && isMinetorioBlock) || (nature && isNature);
    }
}
