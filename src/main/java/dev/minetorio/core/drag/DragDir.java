package dev.minetorio.core.drag;

/** Horizontal direction of a drag line (x east, z south, like the world grid). */
public enum DragDir {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    private static final DragDir[] VALUES = values();
    private final int dx;
    private final int dz;

    DragDir(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() { return dx; }

    public int dz() { return dz; }

    /** Network id (0..3). */
    public int id() { return ordinal(); }

    /** The direction with network id {@code id}, or null if out of range. */
    public static DragDir byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    public DragDir clockWise() { return VALUES[(ordinal() + 1) & 3]; }

    public DragDir counterClockWise() { return VALUES[(ordinal() + 3) & 3]; }

    public DragDir opposite() { return VALUES[(ordinal() + 2) & 3]; }
}
