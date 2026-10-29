package dev.minetorio.core.rail;

import java.util.*;
import dev.minetorio.core.rail.RailGeometry.Cursor;

public record TrainSchedule(List<Stop> stops, int current) {
    public TrainSchedule {
        stops = List.copyOf(stops);
        if (current < 0 || (stops.isEmpty() ? current != 0 : current >= stops.size()))
            throw new IllegalArgumentException("Недопустимая запись расписания");
    }
    public static TrainSchedule empty() { return new TrainSchedule(List.of(), 0); }
    public enum Type { TIME, FULL, EMPTY, ITEM_COUNT, CIRCUIT, INACTIVITY, ROBOTS_INACTIVE, FLUID_COUNT, PASSENGER_PRESENT, PASSENGER_NOT_PRESENT }
    public enum Join { AND, OR }
    public enum Comparator { LT, LE, EQ, NE, GE, GT }
    public enum SignalKind { ITEM, FLUID, VIRTUAL }
    public record SignalKey(SignalKind kind, String name) {
        public SignalKey { Objects.requireNonNull(kind); if (name == null || name.isBlank()) throw new IllegalArgumentException("Пустой сигнал"); }
        public boolean wildcard() { return kind == SignalKind.VIRTUAL && (name.equals("signal-anything") || name.equals("signal-everything")); }
        private boolean each() { return kind == SignalKind.VIRTUAL && name.equals("signal-each"); }
    }
    public record Condition(Optional<SignalKey> first, Comparator comparator, Optional<SignalKey> second, int constant) {
        public Condition {
            Objects.requireNonNull(first); Objects.requireNonNull(comparator); Objects.requireNonNull(second);
            if (first.filter(SignalKey::each).isPresent() || second.filter(key -> key.wildcard() || key.each()).isPresent())
                throw new IllegalArgumentException("Недопустимый операнд ожидания");
        }
    }
    public record Wait(Type type, Join join, long ticks, Optional<Condition> condition) {
        public Wait {
            Objects.requireNonNull(type); Objects.requireNonNull(join); Objects.requireNonNull(condition);
            if (ticks < 0) throw new IllegalArgumentException("Отрицательное время ожидания");
        }
    }
    public record RailTarget(Cursor point, boolean eitherDirection) { public RailTarget { Objects.requireNonNull(point); } }
    public record Stop(Optional<String> station, Optional<RailTarget> railTarget, boolean temporary, List<Wait> waits) {
        public Stop {
            Objects.requireNonNull(station); Objects.requireNonNull(railTarget); waits = List.copyOf(waits);
            if (station.isPresent() == railTarget.isPresent() || station.filter(String::isBlank).isPresent())
                throw new IllegalArgumentException("Нужна ровно одна цель записи");
        }
    }
    public TrainSchedule advance() {
        if (stops.isEmpty()) return this;
        if (!stops.get(current).temporary()) return new TrainSchedule(stops, (current + 1) % stops.size());
        var next = new ArrayList<>(stops); next.remove(current);
        return new TrainSchedule(next, next.isEmpty() ? 0 : current % next.size());
    }
    TrainSchedule skip() { return stops.isEmpty() ? this : new TrainSchedule(stops, (current + 1) % stops.size()); }
}
