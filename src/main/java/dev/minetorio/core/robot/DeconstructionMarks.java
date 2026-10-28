package dev.minetorio.core.robot;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Cells marked for deconstruction (Factorio's red cross), one set per level. Minimal on purpose:
 * mark, unmark, query. Blueprint pastes with Shift mark the vanilla blocks in their way here;
 * whoever removes a marked block (a player by hand, construction robots once they deconstruct)
 * unmarks it. The deconstruction planner builds on this.
 */
public final class DeconstructionMarks {
    private final Set<Cell> marked = new LinkedHashSet<>();

    /** Marks {@code c}; false if it was marked already. */
    public boolean mark(Cell c) {
        return marked.add(c);
    }

    /** Unmarks {@code c}; false if it was not marked. */
    public boolean unmark(Cell c) {
        return marked.remove(c);
    }

    public boolean isMarked(Cell c) {
        return marked.contains(c);
    }

    public Set<Cell> all() {
        return Collections.unmodifiableSet(marked);
    }

    public int size() {
        return marked.size();
    }
}
