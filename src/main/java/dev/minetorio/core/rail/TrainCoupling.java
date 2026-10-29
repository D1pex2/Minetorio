package dev.minetorio.core.rail;

import java.util.*;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Сцепление изменяет только membership и внутреннюю ось присоединяемой части. */
public final class TrainCoupling {
    private TrainCoupling() {}
    public enum End { FRONT, BACK }
    private record Segment(Traversal traversal,double start,double end) {}
    private record Layout(List<UUID> cars,List<Traversal> tape,double head) {}
    private static Train owner(RailwayState state,UUID car) {
        return state.trains().values().stream().filter(t->t.cars().contains(car)).findFirst().orElse(null);
    }
    private static boolean internalFront(RailwayState state,UUID car,End end) { return (end==End.FRONT)!=state.cars().get(car).reversed(); }
    public static Optional<UUID> split(RailwayState state,UUID car,End end) {
        Objects.requireNonNull(end);
        Train train=owner(state,car);
        if(train==null || train.head().isEmpty()) return Optional.empty();
        int boundary=train.cars().indexOf(car)+(internalFront(state,car,end)?0:1);
        if(boundary==0 || boundary==train.cars().size()) return Optional.empty();
        UUID next=UUID.randomUUID();
        Status status=train.speed()==0?Status.MANUAL_CONTROL_STOP:Status.MANUAL_CONTROL;
        Train front=at(state,train.id(),train.cars().subList(0,boundary),train.tape(),state.headCoordinate(train),train.speed(),Mode.MANUAL,status);
        Train back=at(state,next,train.cars().subList(boundary,train.cars().size()),train.tape(),state.headCoordinate(train)-7*boundary,train.speed(),Mode.MANUAL,status);
        return state.replaceConsists(List.of(train),state.graph().revision(),List.of(front,back),Set.of())?Optional.of(next):Optional.empty();
    }
    public static boolean couple(RailwayState state,UUID a,End ea,UUID b,End eb) {
        Objects.requireNonNull(ea); Objects.requireNonNull(eb);
        Train ta=owner(state,a),tb=owner(state,b);
        if(ta==null || tb==null || ta.id().equals(tb.id()) || ta.head().isEmpty() || tb.head().isEmpty()
                || Math.abs(ta.speed())>1e-6 || Math.abs(tb.speed())>1e-6) return false;
        boolean frontA=internalFront(state,a,ea), frontB=internalFront(state,b,eb);
        if(!a.equals(frontA?ta.cars().getFirst():ta.cars().getLast()) || !b.equals(frontB?tb.cars().getFirst():tb.cars().getLast())) return false;
        Layout la=layout(state,ta,false),lb=layout(state,tb,frontA==frontB);
        double jointA=la.head()+(frontA?3.5:-7*(la.cars().size()-1)-3.5);
        double jointB=lb.head()+(frontA?-7*(lb.cars().size()-1)-3.5:3.5);
        Pose pa=state.pose(la.tape(),jointA,false),pb=state.pose(lb.tape(),jointB,false);
        if(!RailwayState.near(pa.position(),pb.position()) || !RailwayState.near(pa.tangent(),pb.tangent())) return false;
        double offset=jointA-jointB;
        List<Segment> segments=new ArrayList<>();
        segments.addAll(segments(state,la,0)); segments.addAll(segments(state,lb,offset));
        segments.sort(Comparator.comparingDouble(Segment::start));
        List<Segment> combined=new ArrayList<>();
        for(Segment segment:segments) {
            if(combined.isEmpty()) { combined.add(segment); continue; }
            Segment last=combined.getLast();
            if(segment.start()<last.end()-1e-6) {
                if(Math.abs(segment.start()-last.start())>1e-6 || Math.abs(segment.end()-last.end())>1e-6 || !segment.traversal().equals(last.traversal())) return false;
            } else {
                if(Math.abs(segment.start()-last.end())>1e-6 || !state.graph().next(last.traversal()).contains(segment.traversal())) return false;
                combined.add(segment);
            }
        }
        List<UUID> members=new ArrayList<>(frontA?lb.cars():la.cars()); members.addAll(frontA?la.cars():lb.cars());
        double head=(frontA?lb.head()+offset:la.head())-combined.getFirst().start();
        Train merged=at(state,ta.id(),members,combined.stream().map(Segment::traversal).toList(),head,0,ta.mode(),ta.mode()==Mode.MANUAL?Status.MANUAL_CONTROL_STOP:ta.status());
        Set<UUID> flips=frontA==frontB?Set.copyOf(tb.cars()):Set.of();
        return state.replaceConsists(List.of(ta,tb),state.graph().revision(),List.of(merged),flips);
    }
    private static Layout layout(RailwayState state,Train train,boolean flip) {
        if(!flip) return new Layout(train.cars(),train.tape(),state.headCoordinate(train));
        List<UUID> cars=new ArrayList<>(train.cars()); Collections.reverse(cars);
        List<Traversal> tape=new ArrayList<>();
        for(int i=train.tape().size()-1;i>=0;i--) tape.add(RailwayState.reverse(train.tape().get(i)));
        return new Layout(cars,tape,state.tapeLength(tape)-state.headCoordinate(train)+7*(cars.size()-1));
    }
    /** Только занятый телом участок: чужая будущая ветка не заменяет историю под вагоном. */
    private static List<Segment> segments(RailwayState state,Layout layout,double offset) {
        double low=layout.head()-7*(layout.cars().size()-1)-3.5, high=layout.head()+3.5,q=0;
        List<Segment> result=new ArrayList<>();
        for(Traversal t:layout.tape()) {
            double next=q+state.railLength(t);
            if(next>low+1e-9 && q<high-1e-9) result.add(new Segment(t,q+offset,next+offset));
            q=next;
        }
        return result;
    }
    private static Train at(RailwayState state,UUID id,List<UUID> cars,List<Traversal> tape,double q,double speed,Mode mode,Status status) {
        var position=state.locate(tape,q);
        return new Train(id,cars,Optional.of(position.cursor()),position.index(),tape,speed,mode,status,0,0,0);
    }
}
