package dev.minetorio.core.blueprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Blueprint book and library rules (Factorio's blueprint book): the limits of a book and of one
 * stored record (a blueprint or a book with everything in it), names, and the list operations the
 * book and the library share: which entry is active, cycling it with the wheel, moving and removing
 * entries.
 */
public final class BlueprintBooks {
    /** Most entries one book holds. */
    public static final int MAX_ENTRIES = 64;
    /** Deepest nesting: a book in a book in a book (the outer one is level 1). */
    public static final int MAX_DEPTH = 3;
    /** Most parts in one record (every blueprint in a book and its sub-books together). */
    public static final int MAX_TOTAL_PARTS = 4 * Blueprints.MAX_PARTS;
    /** Largest record, in bytes as NBT counts them (a book is synced and saved as one item). */
    public static final int MAX_RECORD_BYTES = 512 * 1024;
    /** Longest name, in characters. */
    public static final int MAX_LABEL = 64;
    /** Most icons one blueprint or book shows (Factorio: 4). */
    public static final int MAX_ICONS = 4;
    /** Most entries in the blueprint library. */
    public static final int MAX_LIBRARY_ENTRIES = 1024;

    private BlueprintBooks() {}

    /** The active entry after a wheel step of {@code delta} (wrapping); -1 for an empty book. */
    public static int cycle(int active, int size, int delta) {
        if (size <= 0) return -1;
        int a = active < 0 || active >= size ? 0 : active;
        return Math.floorMod(a + delta, size);
    }

    /** {@code active} kept in 0..size-1 (-1 for an empty book). */
    public static int clamp(int active, int size) {
        if (size <= 0) return -1;
        return Math.max(0, Math.min(size - 1, active));
    }

    /** The active entry after entry {@code removed} is taken out of a book that had {@code oldSize} entries. */
    public static int activeAfterRemove(int active, int removed, int oldSize) {
        int size = oldSize - 1;
        if (size <= 0) return -1;
        if (removed < active) return active - 1;
        return clamp(active, size);
    }

    /** The active entry after an entry is inserted at {@code at}: the same entry stays active (the new one if the book was empty). */
    public static int activeAfterInsert(int active, int at, int oldSize) {
        if (oldSize <= 0 || active < 0) return 0;
        return at <= active ? active + 1 : active;
    }

    /** The active entry after the entry at {@code from} moves to {@code to} (indices in the list after the move): it follows its entry. */
    public static int activeAfterMove(int active, int from, int to) {
        if (active == from) return to;
        if (from < active && to >= active) return active - 1;
        if (from > active && to <= active) return active + 1;
        return active;
    }

    /** {@code list} with the entry at {@code from} moved to index {@code to} (clamped). */
    public static <T> List<T> moved(List<T> list, int from, int to) {
        if (from < 0 || from >= list.size()) throw new IndexOutOfBoundsException(from);
        List<T> out = new ArrayList<>(list);
        T e = out.remove(from);
        out.add(Math.max(0, Math.min(out.size(), to)), e);
        return out;
    }

    /** Whether a path (indices from the outer book in) is usable: at most {@link #MAX_DEPTH} - 1 steps, none negative. */
    public static boolean validPath(List<Integer> path) {
        if (path.size() >= MAX_DEPTH) return false;
        for (int i : path) if (i < 0 || i >= MAX_ENTRIES) return false;
        return true;
    }

    /** A name as typed: control characters dropped, trimmed, cut to {@link #MAX_LABEL}. */
    public static String cleanLabel(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(Math.min(s.length(), MAX_LABEL));
        s.codePoints().filter(c -> !Character.isISOControl(c) && c != 0xA7).forEach(b::appendCodePoint); // § would format
        String out = b.toString().strip();
        if (out.length() > MAX_LABEL) {
            int end = MAX_LABEL;
            if (Character.isHighSurrogate(out.charAt(end - 1))) end--;
            out = out.substring(0, end).strip();
        }
        return out;
    }

    /** Whether {@code s} is a name {@link #cleanLabel} keeps as it is. */
    public static boolean validLabel(String s) {
        return s != null && s.equals(cleanLabel(s));
    }

    /**
     * The default icons of a captured blueprint (Factorio picks them from the contents): up to
     * {@link #MAX_ICONS} of the most frequent items, ties by first seen. {@code counts} in first-seen order.
     */
    public static List<String> defaultIcons(java.util.Map<String, Integer> counts) {
        List<java.util.Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue())); // stable: ties keep first-seen order
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size() && out.size() < MAX_ICONS; i++) out.add(list.get(i).getKey());
        return out;
    }
}
