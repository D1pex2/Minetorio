package dev.minetorio.core.rail;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

public final class RailSlots {
    public record Slot(String key, int count, Optional<String> filter) {}
    private final List<Slot> slots;
    private final ToIntFunction<String> keyLimit;
    private final Runnable changed;
    private int bar;

    public RailSlots(int size, ToIntFunction<String> keyLimit) { this(size, keyLimit, () -> {}); }

    RailSlots(int size, ToIntFunction<String> keyLimit, Runnable changed) {
        if (size < 0) throw new IllegalArgumentException("Отрицательное число слотов");
        this.keyLimit = Objects.requireNonNull(keyLimit);
        this.changed = Objects.requireNonNull(changed);
        slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) slots.add(new Slot("", 0, Optional.empty()));
        bar = size;
    }

    public Slot get(int index) { return slots.get(index); }
    public int insert(int index, String key, int count, boolean simulate) {
        get(index);
        return index >= bar ? 0 : insertManual(index, key, count, simulate);
    }
    public int insertManual(int index, String key, int count, boolean simulate) {
        Slot slot = get(index);
        if (count <= 0 || key == null || key.isEmpty()) return 0;
        int limit = keyLimit.applyAsInt(key);
        if (limit <= 0 || slot.count() > 0 && !slot.key().equals(key)
                || slot.filter().isPresent() && !slot.filter().get().equals(baseId(key))) return 0;
        int accepted = Math.min(count, Math.max(0, limit - slot.count()));
        if (accepted > 0 && !simulate) {
            slots.set(index, new Slot(key, slot.count() + accepted, slot.filter()));
            changed.run();
        }
        return accepted;
    }
    public int extract(int index, int count, boolean simulate) {
        Slot slot = get(index);
        int removed = Math.min(Math.max(0, count), slot.count());
        if (removed > 0 && !simulate) {
            int remaining = slot.count() - removed;
            slots.set(index, new Slot(remaining == 0 ? "" : slot.key(), remaining, slot.filter()));
            changed.run();
        }
        return removed;
    }
    public void setFilter(int index, Optional<String> filter) {
        Slot slot = get(index);
        Objects.requireNonNull(filter);
        filter.ifPresent(id -> {
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || keyLimit.applyAsInt(id) <= 0)
                throw new IllegalArgumentException("Недопустимый фильтр: " + id);
        });
        if (!slot.filter().equals(filter)) {
            slots.set(index, new Slot(slot.key(), slot.count(), filter));
            changed.run();
        }
    }
    public void setBar(int bar) {
        if (bar < 0 || bar > slots.size()) throw new IllegalArgumentException("Недопустимый ограничитель слотов");
        if (this.bar != bar) { this.bar = bar; changed.run(); }
    }
    public int bar() { return bar; }
    public List<Slot> snapshot() { return List.copyOf(slots); }
    static String baseId(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }
}
