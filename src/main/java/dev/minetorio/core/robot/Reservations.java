package dev.minetorio.core.robot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Cells reserved by ghosts that cover more than their own cell (a multiblock machine's footprint, a
 * pole's upper blocks): each reserved cell belongs to one owner, the ghost's own cell. Nothing else
 * may be placed into a reserved cell while its owner's ghost stands; the owner's own cell is
 * reserved too, so a check can treat all footprint cells alike.
 */
public final class Reservations {
    private final Map<Cell, Cell> ownerOf = new HashMap<>();
    private final Map<Cell, List<Cell>> cellsOf = new HashMap<>();

    /** Reserves {@code cells} for {@code owner}, replacing what it had; cells another owner holds are taken over. */
    public void reserve(Cell owner, List<Cell> cells) {
        release(owner);
        List<Cell> copy = List.copyOf(cells);
        for (Cell c : copy) {
            Cell prev = ownerOf.put(c, owner);
            if (prev != null && !prev.equals(owner)) {
                List<Cell> theirs = cellsOf.get(prev);
                if (theirs != null) cellsOf.put(prev, theirs.stream().filter(x -> !x.equals(c)).toList());
            }
        }
        cellsOf.put(owner, copy);
    }

    /** Frees every cell {@code owner} holds. */
    public void release(Cell owner) {
        List<Cell> cells = cellsOf.remove(owner);
        if (cells == null) return;
        for (Cell c : cells) ownerOf.remove(c, owner);
    }

    /** Owner of {@code cell}, or null when it is not reserved. */
    @Nullable
    public Cell owner(Cell cell) {
        return ownerOf.get(cell);
    }

    /** Whether {@code cell} may be used by {@code by}: not reserved, or reserved by {@code by} itself. */
    public boolean freeFor(Cell cell, @Nullable Cell by) {
        Cell o = ownerOf.get(cell);
        return o == null || o.equals(by);
    }

    /** Cells {@code owner} holds (empty if none). */
    public List<Cell> cells(Cell owner) {
        return cellsOf.getOrDefault(owner, List.of());
    }

    public boolean isEmpty() {
        return ownerOf.isEmpty();
    }
}
