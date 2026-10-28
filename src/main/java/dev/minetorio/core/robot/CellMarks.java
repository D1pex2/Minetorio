package dev.minetorio.core.robot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

/**
 * Orders on cells (deconstruction, upgrade), one value per cell, indexed by chunk so a chunk's
 * orders can be sent to the clients that see it. Listeners hear every change (value null:
 * removed). Kept in marking order.
 */
public class CellMarks<V> {
    /** Hears a cell's order change: {@code value} is the new one, null when it was removed. */
    public interface Listener<V> {
        void changed(Cell cell, @Nullable V value);
    }

    private final Map<Cell, V> marks = new LinkedHashMap<>();
    private final Map<Long, Set<Cell>> byChunk = new HashMap<>();
    private final List<Listener<V>> listeners = new CopyOnWriteArrayList<>();

    /** Chunk key of the chunk holding {@code cell} (as Minecraft's {@code ChunkPos.toLong}). */
    public static long chunkKey(Cell cell) {
        return chunkKey(cell.x() >> 4, cell.z() >> 4);
    }

    public static long chunkKey(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | ((cz & 0xFFFFFFFFL) << 32);
    }

    /** Sets the order on {@code cell}; false if it already had exactly this one. */
    protected boolean put(Cell cell, V value) {
        Objects.requireNonNull(value);
        V old = marks.put(cell, value);
        if (value.equals(old)) return false;
        if (old == null) byChunk.computeIfAbsent(chunkKey(cell), k -> new java.util.LinkedHashSet<>()).add(cell);
        for (Listener<V> l : listeners) l.changed(cell, value);
        return true;
    }

    /** Removes the order on {@code cell}; false if there was none. */
    public boolean unmark(Cell cell) {
        if (marks.remove(cell) == null) return false;
        long k = chunkKey(cell);
        Set<Cell> s = byChunk.get(k);
        if (s != null) {
            s.remove(cell);
            if (s.isEmpty()) byChunk.remove(k);
        }
        for (Listener<V> l : listeners) l.changed(cell, null);
        return true;
    }

    public boolean isMarked(Cell cell) { return marks.containsKey(cell); }

    @Nullable
    public V get(Cell cell) { return marks.get(cell); }

    public int size() { return marks.size(); }

    public boolean isEmpty() { return marks.isEmpty(); }

    /** Every marked cell, in marking order (a copy). */
    public List<Cell> cells() { return new ArrayList<>(marks.keySet()); }

    /** The marked cells in chunk {@code (cx, cz)} (a copy). */
    public List<Cell> inChunk(int cx, int cz) {
        Set<Cell> s = byChunk.get(chunkKey(cx, cz));
        return s == null ? List.of() : new ArrayList<>(s);
    }

    /** Chunk keys holding at least one order. */
    public Set<Long> chunks() { return Set.copyOf(byChunk.keySet()); }

    public void addListener(Listener<V> l) { listeners.add(l); }

    public void removeListener(Listener<V> l) { listeners.remove(l); }

    /** Removes every order (listeners hear each). */
    public void clear() {
        for (Cell c : cells()) unmark(c);
    }
}
