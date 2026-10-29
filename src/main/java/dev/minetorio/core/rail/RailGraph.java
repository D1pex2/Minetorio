package dev.minetorio.core.rail;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static dev.minetorio.core.rail.RailGeometry.*;

public final class RailGraph {
    static final Comparator<Traversal> TRAVERSAL_ORDER = Comparator.comparing(Traversal::piece)
            .thenComparing(Traversal::forward);

    private record Node(Point position, RailDir outward) {}
    private record Pair(UUID first, UUID second) {}

    private Map<UUID, Piece> pieces = Map.of();
    private Map<Traversal, List<Traversal>> transitions = Map.of();
    private Map<UUID, Set<UUID>> conflicts = Map.of();
    private long revision;
    private boolean readOnly;

    public RailGraph(Collection<Piece> pieces) {
        rebuild(pieces);
    }

    public void rebuild(Collection<Piece> input) {
        if (readOnly) throw new UnsupportedOperationException("Снимок графа доступен только для чтения");
        List<Piece> ordered = input.stream().sorted(Comparator.comparing(Piece::id)).toList();
        Map<UUID, Piece> newPieces = new HashMap<>();
        Map<Node, List<Port>> nodes = new HashMap<>();
        Map<Point, List<UUID>> cellIndex = new HashMap<>();
        Map<UUID, List<Vec>> lines = new HashMap<>();
        Map<UUID, TreeSet<UUID>> physical = new HashMap<>();
        Set<Pair> candidates = new HashSet<>();

        for (Piece piece : ordered) {
            if (newPieces.putIfAbsent(piece.id(), piece) != null) {
                throw new IllegalArgumentException("Повторный UUID рельса: " + piece.id());
            }
            for (Port port : ports(piece)) {
                Node node = new Node(endpoint(piece, port.end()), outward(piece, port.end()));
                nodes.computeIfAbsent(node, key -> new ArrayList<>()).add(port);
            }
            physical.put(piece.id(), new TreeSet<>());
            lines.put(piece.id(), centerline(piece));
            // Общие клетки сужают кандидатов, но сами по себе еще не доказывают конфликт.
            for (Point cell : cells(piece)) {
                List<UUID> occupants = cellIndex.computeIfAbsent(cell, key -> new ArrayList<>());
                for (UUID other : occupants) candidates.add(new Pair(other, piece.id()));
                occupants.add(piece.id());
            }
        }

        for (Pair pair : candidates) {
            Piece first = newPieces.get(pair.first());
            Piece second = newPieces.get(pair.second());
            if (sameTrack(first, second)) {
                throw new IllegalArgumentException("Один физический рельс записан дважды: " + pair);
            }
            if (stripsOverlap(lines.get(pair.first()), lines.get(pair.second()))) {
                physical.get(pair.first()).add(pair.second());
                physical.get(pair.second()).add(pair.first());
            }
        }

        Map<Traversal, List<Traversal>> newTransitions = new HashMap<>();
        for (Piece piece : ordered) for (boolean forward : new boolean[]{false, true}) {
            End exit = forward ? End.B : End.A;
            Node compatible = new Node(endpoint(piece, exit), outward(piece, exit).opposite());
            List<Traversal> next = nodes.getOrDefault(compatible, List.of()).stream()
                    .filter(port -> !port.piece().equals(piece.id()))
                    .map(port -> new Traversal(port.piece(), port.end() == End.A))
                    .sorted(TRAVERSAL_ORDER).toList();
            newTransitions.put(new Traversal(piece.id(), forward), next);
        }
        Map<UUID, Set<UUID>> newConflicts = new HashMap<>();
        physical.forEach((id, others) -> newConflicts.put(id, Collections.unmodifiableSortedSet(others)));

        // Ошибка входных данных не меняет старый граф или его ревизию.
        pieces = Map.copyOf(newPieces);
        transitions = Map.copyOf(newTransitions);
        conflicts = Map.copyOf(newConflicts);
        revision++;
    }

    Collection<Piece> pieces() { return pieces.values(); }

    public long revision() {
        return revision;
    }

    private RailGraph(RailGraph source) {
        pieces = source.pieces;
        transitions = source.transitions;
        conflicts = source.conflicts;
        revision = source.revision;
        readOnly = true;
    }

    /** Дешевый неизменяемый снимок с той же ревизией и готовыми таблицами. */
    public RailGraph snapshot() { return new RailGraph(this); }

    public Optional<Piece> piece(UUID id) {
        return Optional.ofNullable(pieces.get(id));
    }

    public List<Traversal> next(Traversal traversal) {
        return transitions.getOrDefault(traversal, List.of());
    }

    public Set<UUID> conflicts(UUID id) {
        return conflicts.getOrDefault(id, Set.of());
    }
}
