package dev.minetorio.core.drag;

import java.util.ArrayList;
import java.util.List;

/**
 * Factorio-style drag line on the horizontal grid: a start cell and a direction, extended one
 * cell at a time. A step along the current direction continues the line; a step to either side
 * turns it, and the cell where it turns (the previous head) is re-pointed the new way so a belt
 * there becomes a curve. Stepping back the way the line came is refused.
 *
 * <p>The head's facing stays open until the next step: whoever places cells from this line
 * places a cell only once a later cell exists (or when the drag ends).
 */
public final class DragLine {
    private final int maxCells;
    private final List<DragCell> cells = new ArrayList<>();
    private DragDir direction;

    public DragLine(int x, int z, DragDir direction, int maxCells) {
        if (maxCells < 1) throw new IllegalArgumentException("maxCells " + maxCells);
        this.maxCells = maxCells;
        this.direction = direction;
        cells.add(new DragCell(x, z, direction));
    }

    public int size() { return cells.size(); }

    /** Number of steps taken (cells after the start). */
    public int steps() { return cells.size() - 1; }

    public DragCell cell(int i) { return cells.get(i); }

    public DragCell head() { return cells.getLast(); }

    /** The direction the line currently runs (and the head faces). */
    public DragDir direction() { return direction; }

    /**
     * Extends the line by one cell in {@code d}: false (and unchanged) if {@code d} reverses the
     * line or the line is full.
     */
    public boolean step(DragDir d) {
        if (d == null || d == direction.opposite() || cells.size() >= maxCells) return false;
        DragCell head = head();
        cells.set(cells.size() - 1, new DragCell(head.x(), head.z(), d));
        cells.add(new DragCell(head.x() + d.dx(), head.z() + d.dz(), d));
        direction = d;
        return true;
    }

    /**
     * The steps that bring the head toward the target column ({@code tx}, {@code tz}), at most
     * {@code max} and never past the length limit: first along the current direction up to the
     * target's position on that axis, then, when the target lies at least {@code turnThreshold}
     * cells to a side, a turn and the steps along the new axis. A target behind the head and
     * within the threshold of the line adds nothing.
     */
    public List<DragDir> plan(int tx, int tz, int turnThreshold, int max) {
        int dx = tx - head().x(), dz = tz - head().z();
        int along = dx * direction.dx() + dz * direction.dz();
        DragDir right = direction.clockWise();
        int side = dx * right.dx() + dz * right.dz();
        int limit = Math.min(max, maxCells - cells.size());
        List<DragDir> out = new ArrayList<>();
        for (int i = 0; i < along && out.size() < limit; i++) out.add(direction);
        if (Math.abs(side) >= Math.max(1, turnThreshold)) {
            DragDir turn = side > 0 ? right : direction.counterClockWise();
            for (int i = 0; i < Math.abs(side) && out.size() < limit; i++) out.add(turn);
        }
        return out;
    }
}
