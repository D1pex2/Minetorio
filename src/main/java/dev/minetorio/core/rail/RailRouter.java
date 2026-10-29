package dev.minetorio.core.rail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiPredicate;

import static dev.minetorio.core.rail.RailGeometry.*;

public final class RailRouter {
    private RailRouter() {}

    public record Route(List<Traversal> steps, double length, long graphRevision) {
        public Route {
            steps = List.copyOf(steps);
            if (!Double.isFinite(length) || length < 0) {
                throw new IllegalArgumentException("Длина маршрута должна быть конечной и неотрицательной");
            }
        }
    }

    private record State(Traversal traversal, double distance, List<Traversal> path) {}

    private static final Comparator<State> STATE_ORDER = Comparator.comparingDouble(State::distance)
            .thenComparing(State::path, RailRouter::comparePaths);

    public static Optional<Route> route(RailGraph graph, Cursor start, Set<Cursor> goals,
                                        BiPredicate<Traversal, Traversal> allowed) {
        Objects.requireNonNull(allowed, "allowed");
        validateCursor(graph, start);
        List<Cursor> orderedGoals = goals.stream()
                .sorted(Comparator.comparing(Cursor::traversal, RailGraph.TRAVERSAL_ORDER)
                        .thenComparingDouble(Cursor::distance)).toList();
        Map<Traversal, List<Cursor>> targets = new HashMap<>();
        for (Cursor goal : orderedGoals) {
            validateCursor(graph, goal);
            targets.computeIfAbsent(goal.traversal(), key -> new ArrayList<>()).add(goal);
        }
        if (goals.isEmpty()) return Optional.empty();

        long revision = graph.revision();
        Route result = null;
        for (Cursor goal : targets.getOrDefault(start.traversal(), List.of())) {
            if (goal.distance() >= start.distance()) {
                Route candidate = new Route(List.of(start.traversal()), goal.distance() - start.distance(), revision);
                result = better(result, candidate);
            }
        }
        double remaining = length(graph.piece(start.traversal().piece()).orElseThrow()) - start.distance();
        // Цель внутри исходного рельса достигается до любого настоящего перехода.
        if (result != null && result.length() <= remaining) return Optional.of(result);

        PriorityQueue<State> queue = new PriorityQueue<>(STATE_ORDER);
        Map<Traversal, State> best = new HashMap<>();
        // Старт посередине рельса - отдельное начальное состояние, а не закрытый вход A/B.
        // Поэтому после полного цикла можно вновь войти в тот же directed рельс.
        for (Traversal next : graph.next(start.traversal())) {
            if (allowed.test(start.traversal(), next)) {
                offer(queue, best, new State(next, remaining, List.of(start.traversal(), next)));
            }
        }
        while (!queue.isEmpty()) {
            State state = queue.remove();
            if (best.get(state.traversal()) != state) continue;
            if (result != null && state.distance() > result.length()) break;
            for (Cursor goal : targets.getOrDefault(state.traversal(), List.of())) {
                Route candidate = new Route(state.path(), state.distance() + goal.distance(), revision);
                result = better(result, candidate);
            }
            double exitDistance = state.distance() + length(graph.piece(state.traversal().piece()).orElseThrow());
            if (result != null && exitDistance > result.length()) continue;
            for (Traversal next : graph.next(state.traversal())) {
                if (!allowed.test(state.traversal(), next)) continue;
                List<Traversal> path = new ArrayList<>(state.path());
                path.add(next);
                offer(queue, best, new State(next, exitDistance, List.copyOf(path)));
            }
        }
        return Optional.ofNullable(result);
    }

    private static void validateCursor(RailGraph graph, Cursor cursor) {
        Piece piece = graph.piece(cursor.traversal().piece())
                .orElseThrow(() -> new IllegalArgumentException("Рельс курсора отсутствует в графе"));
        if (cursor.distance() > length(piece)) {
            throw new IllegalArgumentException("Расстояние курсора находится за пределами рельса");
        }
    }

    private static void offer(PriorityQueue<State> queue, Map<Traversal, State> best, State candidate) {
        State previous = best.get(candidate.traversal());
        if (previous == null || STATE_ORDER.compare(candidate, previous) < 0) {
            best.put(candidate.traversal(), candidate);
            queue.add(candidate);
        }
    }

    private static Route better(Route current, Route candidate) {
        if (current == null) return candidate;
        int lengthComparison = Double.compare(candidate.length(), current.length());
        if (lengthComparison < 0 || lengthComparison == 0 && comparePaths(candidate.steps(), current.steps()) < 0) {
            return candidate;
        }
        return current;
    }

    private static int comparePaths(List<Traversal> first, List<Traversal> second) {
        int count = Math.min(first.size(), second.size());
        for (int i = 0; i < count; i++) {
            int comparison = RailGraph.TRAVERSAL_ORDER.compare(first.get(i), second.get(i));
            if (comparison != 0) return comparison;
        }
        return Integer.compare(first.size(), second.size());
    }
}
