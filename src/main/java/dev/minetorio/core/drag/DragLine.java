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
    /** Direction of each step (step k leads from cell k to cell k + 1). */
    private final List<DragDir> stepDirs = new ArrayList<>();
    private final DragDir startDirection;
    private DragDir direction;

    public DragLine(int x, int z, DragDir direction, int maxCells) {
        if (maxCells < 1) throw new IllegalArgumentException("maxCells " + maxCells);
        this.maxCells = maxCells;
        this.direction = direction;
        this.startDirection = direction;
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
        stepDirs.add(d);
        direction = d;
        return true;
    }

    /**
     * Cuts the line back to its first {@code size} cells (1 .. size()); the new head faces, and
     * the line runs, the way the head was entered again (the start direction for the start).
     */
    public void truncate(int size) {
        if (size < 1 || size > cells.size()) throw new IllegalArgumentException("size " + size + " of " + cells.size());
        while (cells.size() > size) {
            cells.removeLast();
            stepDirs.removeLast();
        }
        direction = stepDirs.isEmpty() ? startDirection : stepDirs.getLast();
        DragCell head = head();
        cells.set(cells.size() - 1, new DragCell(head.x(), head.z(), direction));
    }

    /**
     * How many cells to drop before turning toward the target column ({@code tx}, {@code tz}) when
     * the turn lies behind the head: the target is at least {@code turnThreshold} cells to a side
     * and behind the head along the line. Then the corner moves back to the target's position on
     * the line, but never past the start of the current straight run (the previous corner) and
     * never below {@code minSize} cells (the ones already placed). 0 otherwise.
     */
    public int retraction(int tx, int tz, int turnThreshold, int minSize) {
        int dx = tx - head().x(), dz = tz - head().z();
        int along = dx * direction.dx() + dz * direction.dz();
        DragDir right = direction.clockWise();
        int side = dx * right.dx() + dz * right.dz();
        if (along >= 0 || Math.abs(side) < Math.max(1, turnThreshold)) return 0;
        int run = 0;
        for (int k = stepDirs.size() - 1; k >= 0 && stepDirs.get(k) == direction; k--) run++;
        return Math.max(0, Math.min(Math.min(-along, run), cells.size() - minSize));
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
