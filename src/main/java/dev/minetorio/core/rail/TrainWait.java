package dev.minetorio.core.rail;

import java.util.*;
import static dev.minetorio.core.rail.TrainSchedule.*;

public final class TrainWait {
    private TrainWait() {}
    public record PassengerState(boolean present, OptionalInt activePersonalRobots) {
        public PassengerState { Objects.requireNonNull(activePersonalRobots);if(activePersonalRobots.isPresent()&&activePersonalRobots.getAsInt()<0)throw new IllegalArgumentException("Отрицательное число роботов"); }
        public PassengerState(boolean present,int robots) { this(present,OptionalInt.of(robots)); }
    }
    public record Context(long elapsed, long inactive, List<List<RailSlots.WaitSlot>> cargo,
                          List<RailwayState.Fluid> fluids, Optional<PassengerState> passengers,
                          Optional<Map<SignalKey,Integer>> circuit) {
        public Context {
            if (elapsed < 0 || inactive < 0) throw new IllegalArgumentException("Отрицательные часы");
            cargo = cargo.stream().map(List::copyOf).toList(); fluids = List.copyOf(fluids);
            Objects.requireNonNull(passengers); circuit = circuit.map(Map::copyOf);
        }
    }
    public static boolean evaluate(List<Wait> waits, Context context) {
        if (waits.isEmpty()) return true;
        boolean result = false, group = single(waits.getFirst(), context);
        for (int i = 1; i < waits.size(); i++) {
            Wait wait = waits.get(i); boolean value = single(wait, context);
            if (wait.join() == Join.OR) { result |= group; group = value; }
            else group &= value;
        }
        return result || group;
    }
    private static boolean single(Wait wait, Context c) {
        return switch (wait.type()) {
            case TIME -> c.elapsed() >= wait.ticks();
            case INACTIVITY -> c.inactive() >= wait.ticks();
            case FULL -> c.cargo().stream().flatMap(List::stream).noneMatch(RailSlots.WaitSlot::canInsert)
                    && c.fluids().stream().allMatch(f -> f.amount() >= f.capacity());
            case EMPTY -> c.cargo().stream().flatMap(List::stream).allMatch(s -> s.slot().count() == 0)
                    && c.fluids().stream().allMatch(f -> f.amount() == 0);
            case PASSENGER_PRESENT -> c.passengers().map(PassengerState::present).orElse(false);
            case PASSENGER_NOT_PRESENT -> c.passengers().map(p -> !p.present()).orElse(false);
            case ROBOTS_INACTIVE -> c.passengers().map(p -> p.activePersonalRobots().isPresent() && p.activePersonalRobots().getAsInt() == 0).orElse(false);
            case CIRCUIT -> c.circuit().map(sample -> compare(wait, sample, null)).orElse(false);
            case ITEM_COUNT -> compare(wait, items(c), SignalKind.ITEM);
            case FLUID_COUNT -> compare(wait, fluids(c), SignalKind.FLUID);
        };
    }
    static Map<SignalKey,Long> items(Context c) {
        Map<SignalKey,Long> result = new HashMap<>();
        c.cargo().stream().flatMap(List::stream).map(RailSlots.WaitSlot::slot).filter(s -> s.count() > 0)
                .forEach(s -> result.merge(new SignalKey(SignalKind.ITEM, RailSlots.baseId(s.key())), (long) s.count(), Math::addExact));
        return result;
    }
    static Map<SignalKey,Double> fluids(Context c) {
        Map<SignalKey,Double> result = new HashMap<>();
        c.fluids().stream().filter(f -> f.amount() > 0).forEach(f -> result.merge(new SignalKey(SignalKind.FLUID, f.id()), f.amount(), Double::sum));
        return result;
    }
    private static boolean compare(Wait wait, Map<SignalKey,? extends Number> values, SignalKind domain) {
        if (wait.condition().isEmpty() || wait.condition().get().first().isEmpty()) return false;
        Condition condition = wait.condition().get(); SignalKey first = condition.first().orElseThrow();
        if (domain != null && ((!first.wildcard() && first.kind() != domain)
                || condition.second().filter(key -> key.kind() != domain).isPresent())) return false;
        double rhs = condition.second().map(key -> number(values, key)).orElse((double) condition.constant());
        if (!first.wildcard()) return comparison(number(values, first), condition.comparator(), rhs);
        var input = values.entrySet().stream().filter(e -> !e.getKey().wildcard() && e.getValue().doubleValue() != 0
                && !condition.second().filter(e.getKey()::equals).isPresent());
        return first.name().equals("signal-everything")
                ? input.allMatch(e -> comparison(e.getValue().doubleValue(), condition.comparator(), rhs))
                : input.anyMatch(e -> comparison(e.getValue().doubleValue(), condition.comparator(), rhs));
    }
    private static double number(Map<SignalKey,? extends Number> values, SignalKey key) {
        Number value = values.get(key); return value == null ? 0 : value.doubleValue();
    }
    private static boolean comparison(double lhs, TrainSchedule.Comparator cmp, double rhs) {
        return switch (cmp) { case LT -> lhs < rhs; case LE -> lhs <= rhs; case EQ -> lhs == rhs;
            case NE -> lhs != rhs; case GE -> lhs >= rhs; case GT -> lhs > rhs; };
    }
}
