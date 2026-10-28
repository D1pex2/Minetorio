package dev.minetorio.core.robot;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Blocks marked for deconstruction (Factorio's red X): the cell of the block (a machine's
 * controller) and the id of the block that was marked there, so a mark whose block changed in the
 * meantime can be dropped ({@link #stillValid}). An empty id means "whatever stands there".
 *
 * <p>Marked by the deconstruction planner and by Shift blueprint pastes (the vanilla blocks in the
 * way). Construction robots take a deconstruction job for every mark ({@link ConstructionQueue});
 * whoever removes a marked block unmarks it. The server keeps one instance per level and saves it
 * (content: {@code ConstructionMarks}).
 */
public final class DeconstructionMarks extends CellMarks<String> {
    /** Marks {@code cell}, whatever block stands there; false if it was marked already. */
    public boolean mark(Cell cell) {
        if (isMarked(cell)) return false;
        return mark(cell, "");
    }

    /** Marks {@code cell} holding block {@code blockId}; false if it was marked already with that id. */
    public boolean mark(Cell cell, String blockId) {
        return put(cell, blockId == null ? "" : blockId);
    }

    /** Block id the mark was made for ("" for any). */
    public String block(Cell cell) {
        String b = get(cell);
        return b == null ? "" : b;
    }

    /** Whether the mark on {@code cell} still fits the block now there ({@code currentBlockId}). */
    public boolean stillValid(Cell cell, String currentBlockId) {
        String b = get(cell);
        return b != null && (b.isEmpty() || b.equals(currentBlockId));
    }

    /** Every marked cell, in marking order (read-only snapshot). */
    public Set<Cell> all() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(cells()));
    }
}
