package dev.minetorio.core.rail;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Неизменяемые блоки, направления и показания сигналов. Владелец снимка - RailwayState. */
public final class RailSignals {
    public enum Kind { REGULAR, CHAIN }
    public enum Aspect { RED, YELLOW, GREEN, BLUE, INVALID }
    public record Signal(UUID id, Port boundary, boolean intoPiece, Kind kind) {
        public Signal { Objects.requireNonNull(id); Objects.requireNonNull(boundary); Objects.requireNonNull(kind); }
    }
    private record Edge(Traversal from, Traversal to) {}
    private final RailGraph graph;
    private final long revision;
    private final Map<UUID, Signal> definitions;
    private final Map<Edge, List<Signal>> boundaries;
    private final Map<UUID, Set<UUID>> resources;
    private final Map<UUID, UUID> components;

    private RailSignals(RailGraph graph, long revision, Map<UUID, Signal> definitions,
                        Map<Edge,List<Signal>> boundaries, Map<UUID,Set<UUID>> resources, Map<UUID,UUID> components) {
        this.graph=graph.snapshot(); this.revision=revision; this.definitions=Map.copyOf(definitions);
        this.boundaries=Map.copyOf(boundaries); this.resources=Map.copyOf(resources); this.components=Map.copyOf(components);
    }
    public static RailSignals rebuild(RailGraph graph, Collection<Signal> input, long networkRevision) {
        if(networkRevision<0) throw new IllegalArgumentException("Отрицательная ревизия сети");
        Map<UUID,Signal> definitions=new LinkedHashMap<>();
        Set<String> occupied=new HashSet<>();
        for(Signal signal:input) {
            if(graph.piece(signal.boundary().piece()).isEmpty() || definitions.putIfAbsent(signal.id(),signal)!=null
                    || !occupied.add(signal.boundary()+":"+signal.intoPiece())) throw new IllegalArgumentException("Неверный или повторный сигнал");
        }
        Map<Edge,List<Signal>> boundaries=new HashMap<>();
        for(Piece p:graph.pieces()) for(boolean forward:new boolean[]{true,false}) {
            Traversal from=new Traversal(p.id(),forward);
            for(Traversal to:graph.next(from)) {
                List<Signal> signals=definitions.values().stream().filter(s->faces(s,from,to)).toList();
                if(!signals.isEmpty()) boundaries.put(new Edge(from,to),signals);
            }
        }
        Map<UUID,UUID> components=new HashMap<>();
        for(Piece seed:graph.pieces()) if(!components.containsKey(seed.id())) {
            Set<UUID> connected=new HashSet<>(); ArrayDeque<UUID> queue=new ArrayDeque<>(); queue.add(seed.id());
            while(!queue.isEmpty()) {
                UUID id=queue.remove(); if(!connected.add(id)) continue;
                for(boolean forward:new boolean[]{true,false}) {
                    Traversal from=new Traversal(id,forward);
                    for(Traversal to:graph.next(from)) if(!boundaries.containsKey(new Edge(from,to))
                            && !boundaries.containsKey(new Edge(RailwayState.reverse(to),RailwayState.reverse(from)))) queue.add(to.piece());
                }
            }
            UUID block=connected.stream().min(UUID::compareTo).orElseThrow();
            connected.forEach(id->components.put(id,block));
        }
        Map<UUID,Set<UUID>> blockResources=new HashMap<>();
        for(UUID component:components.values()) blockResources.computeIfAbsent(component,k->new HashSet<>()).add(component);
        for(Piece p:graph.pieces()) for(UUID other:graph.conflicts(p.id())) {
            // Совместимый стык дает переход, а не пересечение полос. Развилки и настоящие
            // пересечения без такого перехода сохраняют общий конфликтный ресурс.
            boolean joint=graph.next(new Traversal(p.id(),true)).stream().anyMatch(t->t.piece().equals(other))
                    || graph.next(new Traversal(p.id(),false)).stream().anyMatch(t->t.piece().equals(other));
            UUID a=components.get(p.id()), b=components.get(other);
            if(joint || a.equals(b)) continue;
            String key=a.compareTo(b)<0?a+":"+b:b+":"+a;
            UUID conflict=UUID.nameUUIDFromBytes(("rail-conflict:"+key).getBytes(StandardCharsets.UTF_8));
            blockResources.get(a).add(conflict); blockResources.get(b).add(conflict);
        }
        Map<UUID,Set<UUID>> resources=new HashMap<>();
        components.forEach((piece,block)->resources.put(piece,Set.copyOf(blockResources.get(block))));
        return new RailSignals(graph,networkRevision,definitions,boundaries,resources,components);
    }
    private static boolean faces(Signal s, Traversal from, Traversal to) {
        return s.intoPiece()?s.boundary().equals(new Port(to.piece(),to.forward()?End.A:End.B))
                :s.boundary().equals(new Port(from.piece(),from.forward()?End.B:End.A));
    }
    public long revision() { return revision; }
    public boolean allowed(Traversal from,Traversal to) {
        return graph.next(from).contains(to) && (!at(from,to).isEmpty()
                || at(RailwayState.reverse(to),RailwayState.reverse(from)).isEmpty());
    }
    public Set<UUID> blocks(Traversal traversal) { return resources.getOrDefault(traversal.piece(),Set.of()); }
    List<Signal> at(Traversal from,Traversal to) { return boundaries.getOrDefault(new Edge(from,to),List.of()); }
    public Aspect aspect(UUID id,TrainReservations reservations) {
        Signal signal=definitions.get(id); if(signal==null) return Aspect.INVALID;
        Set<Traversal> starts=new HashSet<>();
        for(var entry:boundaries.entrySet()) if(entry.getValue().contains(signal)) {
            Edge edge=entry.getKey();
            if(Objects.equals(components.get(edge.from().piece()),components.get(edge.to().piece()))) return Aspect.INVALID;
            starts.add(edge.to());
        }
        if(starts.isEmpty()) return Aspect.INVALID;
        List<Aspect> aspects=new ArrayList<>();
        for(Traversal start:starts) {
            Aspect own=blockAspect(blocks(start),reservations);
            if(own==Aspect.YELLOW && !reservations.reservedEntrance(signal,this)) own=Aspect.RED;
            if(signal.kind()==Kind.REGULAR || own!=Aspect.GREEN) aspects.add(own);
            else exits(start,reservations,new HashSet<>(),aspects);
        }
        if(aspects.isEmpty()) return Aspect.RED;
        if(aspects.stream().allMatch(a->a==Aspect.GREEN)) return Aspect.GREEN;
        if(aspects.stream().allMatch(a->a==Aspect.YELLOW)) return Aspect.YELLOW;
        return aspects.contains(Aspect.GREEN)?Aspect.BLUE:Aspect.RED;
    }
    double lengthOf(Traversal traversal) { return length(graph.piece(traversal.piece()).orElseThrow()); }
    boolean startsAt(Signal signal,Traversal traversal) {
        if(signal.intoPiece()) return signal.boundary().equals(new Port(traversal.piece(),traversal.forward()?End.A:End.B));
        Traversal before=new Traversal(signal.boundary().piece(),signal.boundary().end()==End.B);
        return graph.next(before).contains(traversal);
    }
    private void exits(Traversal current,TrainReservations reservations,Set<Traversal> visited,List<Aspect> result) {
        if(!visited.add(current)) return;
        for(Traversal next:graph.next(current)) if(allowed(current,next)) {
            Aspect occupied=blockAspect(blocks(next),reservations);
            if(occupied!=Aspect.GREEN || at(current,next).stream().anyMatch(s->s.kind()==Kind.REGULAR)) result.add(occupied);
            else exits(next,reservations,visited,result);
        }
    }
    private static Aspect blockAspect(Set<UUID> blocks,TrainReservations reservations) {
        if(blocks.stream().anyMatch(b->!reservations.occupants(b).isEmpty())) return Aspect.RED;
        if(blocks.stream().anyMatch(b->!reservations.claimants(b).isEmpty())) return Aspect.YELLOW;
        return Aspect.GREEN;
    }
}
