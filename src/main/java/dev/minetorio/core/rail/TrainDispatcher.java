package dev.minetorio.core.rail;

import java.util.*;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.RailwayState.Car;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.TrainSchedule.*;

/** Единственный диспетчер измерения. Сохраняемые данные находятся у RailwayState. */
public final class TrainDispatcher {
    public record Claim(UUID trainId, long tripId, int scheduleIndex, int routeOccurrence, UUID stationId) {
        public Claim {
            Objects.requireNonNull(trainId); Objects.requireNonNull(stationId);
            if (tripId < 0 || scheduleIndex < 0 || routeOccurrence < 0) throw new IllegalArgumentException("Неверная бронь станции");
        }
    }
    public record Visit(Optional<UUID> station, Cursor point, int scheduleIndex, long tripId, int occurrence, double remaining) {
        public Visit {
            Objects.requireNonNull(station); Objects.requireNonNull(point);
            if (scheduleIndex < 0 || tripId < 0 || occurrence < 0 || !Double.isFinite(remaining)) throw new IllegalArgumentException("Неверное посещение");
        }
        Visit moved(double distance) { return new Visit(station, point, scheduleIndex, tripId, occurrence, remaining - distance); }
    }
    public record Assignment(int sign, long routeRevision, long graphRevision, long networkRevision,
                             List<Traversal> route, int routeIndex, List<Visit> visits) {
        public Assignment {
            route = List.copyOf(route); visits = List.copyOf(visits);
            if (Math.abs(sign) != 1 || routeRevision < 0 || graphRevision < 0 || networkRevision < 0 || routeIndex < 0
                    || route.isEmpty() || visits.isEmpty()) throw new IllegalArgumentException("Неверное назначение");
            double previous = -Double.MAX_VALUE;
            for (Visit visit : visits) {
                if (visit.remaining() < previous || visit.remaining()>1e-6 && visit.occurrence() < routeIndex) throw new IllegalArgumentException("Неверный порядок посещений");
                previous = visit.remaining();
            }
        }
    }
    public record Runtime(TrainSchedule schedule, long tripId, Optional<Assignment> assignment, Optional<UUID> stoppedStation,
                          OptionalLong arrivalTick, OptionalLong activityTick, boolean waitComplete, Optional<Claim> hold) {
        public Runtime {
            Objects.requireNonNull(schedule); Objects.requireNonNull(assignment); Objects.requireNonNull(stoppedStation);
            Objects.requireNonNull(arrivalTick); Objects.requireNonNull(activityTick); Objects.requireNonNull(hold);
            if (tripId < 0 || arrivalTick.isPresent() != activityTick.isPresent()
                    || arrivalTick.isPresent() && (arrivalTick.getAsLong() < 0 || activityTick.getAsLong() < arrivalTick.getAsLong())
                    || waitComplete && arrivalTick.isEmpty() || hold.isPresent() && !stoppedStation.equals(Optional.of(hold.get().stationId())))
                throw new IllegalArgumentException("Неверные часы или удержание станции");
        }
        public static Runtime empty() { return new Runtime(TrainSchedule.empty(),0,Optional.empty(),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty()); }
    }
    public record StationOutput(int assignedCount, Optional<UUID> stoppedTrain, Map<SignalKey,Integer> cargo) {
        public StationOutput { cargo = Map.copyOf(cargo); }
    }
    record Candidate(Train train, long graphRevision, long networkRevision, long stationRevision, Runtime previous,
                     TrainSchedule schedule, Assignment assignment) {}
    private record Choice(Optional<UUID> station, Cursor point, RailRouter.Route route, int sign) {}
    private record Selection(Optional<Choice> choice, Status failure, boolean skip) {}
    private final RailwayState owner;
    TrainDispatcher(RailwayState owner) { this.owner = owner; }
    public Runtime runtime(UUID train) { return owner.dispatchRuntime.getOrDefault(train, Runtime.empty()); }
    public Map<UUID,Runtime> runtimes() {
        Map<UUID,Runtime> result = new LinkedHashMap<>(); owner.trains().keySet().forEach(id -> result.put(id, runtime(id))); return Map.copyOf(result);
    }
    public List<Claim> claims() {
        List<Claim> result = new ArrayList<>();
        owner.dispatchRuntime.forEach((id, runtime) -> {
            runtime.hold().ifPresent(result::add);
            runtime.assignment().ifPresent(a -> a.visits().forEach(v -> v.station().ifPresent(station -> result.add(claim(id,v)))));
        });
        return List.copyOf(result);
    }
    private static Claim claim(UUID train, Visit visit) { return new Claim(train,visit.tripId(),visit.scheduleIndex(),visit.occurrence(),visit.station().orElseThrow()); }
    private void put(UUID train, Runtime runtime) { owner.dispatchRuntime.put(train, runtime); owner.dispatchChanged(); }
    private Train train(UUID id) { return Objects.requireNonNull(owner.trains().get(id), "Состав отсутствует"); }
    private void status(UUID id, Status status) { Train t = train(id); owner.dispatchTrain(id,t.mode(),status,false); }
    private Runtime cleared(Runtime r, TrainSchedule schedule, long trip) {
        return new Runtime(schedule,trip,Optional.empty(),r.stoppedStation(),OptionalLong.empty(),OptionalLong.empty(),false,r.hold());
    }
    public void setSchedule(UUID id, TrainSchedule schedule) {
        Train t = train(id); Objects.requireNonNull(schedule); Runtime old = runtime(id);
        Runtime next = cleared(old,schedule,Math.addExact(old.tripId(),1));
        if (!schedule.stops().isEmpty() && old.stoppedStation().isPresent()) {
            Station station = owner.stations.get(old.stoppedStation().get());
            if (station != null && schedule.stops().get(schedule.current()).station().equals(Optional.of(station.name())))
                next = new Runtime(schedule,next.tripId(),Optional.empty(),old.stoppedStation(),OptionalLong.of(owner.railClock),OptionalLong.of(owner.railClock),false,old.hold());
        }
        put(id,next);
        owner.dispatchTrain(id,t.mode(),schedule.stops().isEmpty()?Status.NO_SCHEDULE:t.status(),true);
    }
    public void setMode(UUID id, Mode mode) {
        Train t = train(id); Objects.requireNonNull(mode); if (t.mode() == mode) return;
        Runtime r = runtime(id);
        put(id,new Runtime(r.schedule(),Math.addExact(r.tripId(),1),Optional.empty(),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty()));
        owner.dispatchTrain(id,mode,mode == Mode.MANUAL ? t.speed()==0?Status.MANUAL_CONTROL_STOP:Status.MANUAL_CONTROL
                :r.schedule().stops().isEmpty()?Status.NO_SCHEDULE:Status.NO_PATH,true);
    }
    public void upsertStation(Station station) {
        Objects.requireNonNull(station); validatePoint(station.stop());
        if (owner.cars().containsKey(station.id()) || owner.trains().containsKey(station.id()) || owner.graph().piece(station.id()).isPresent()
                || owner.signalDefinitions().stream().anyMatch(s -> s.id().equals(station.id()))) throw new IllegalArgumentException("Повторный UUID станции");
        if (station.equals(owner.stations.get(station.id()))) return;
        owner.stations.put(station.id(),station); owner.effectiveStations.put(station.id(),station); owner.stationRevision++; owner.dispatchChanged();
        for (UUID id : List.copyOf(owner.dispatchRuntime.keySet()))
            if (runtime(id).stoppedStation().equals(Optional.of(station.id()))) reevaluateWaitingStation(id,station);
    }
    private void reevaluateWaitingStation(UUID id, Station station) {
        Runtime r=runtime(id);Train t=train(id);
        if (station==null || t.speed()!=0 || !atPoint(t,station.stop())) {
            // Старый physical hold не относится к новому месту станции. Позы и скорость не меняем.
            put(id,new Runtime(r.schedule(),r.tripId(),Optional.empty(),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty()));
            owner.dispatchTrain(id,t.mode(),Status.NO_PATH,true);
        } else if (r.arrivalTick().isPresent() && !r.waitComplete() && !r.schedule().stops().isEmpty()
                && !r.schedule().stops().get(r.schedule().current()).station().equals(Optional.of(station.name()))) {
            // Имя записи исчезло, но поезд физически стоит здесь до успешного следующего назначения.
            put(id,new Runtime(r.schedule(),r.tripId(),r.assignment(),r.stoppedStation(),r.arrivalTick(),r.activityTick(),true,r.hold()));
        }
    }
    private void validatePoint(Cursor point) {
        if (owner.graph().piece(point.traversal().piece()).isEmpty() || point.distance() > owner.railLength(point.traversal()))
            throw new IllegalArgumentException("Станция вне рельса");
    }
    public void renameStation(UUID id, String name) {
        Station station = Objects.requireNonNull(owner.stations.get(id)); upsertStation(new Station(id,name,station.stop(),station.enabled(),station.trainLimit()));
    }
    public void removeStation(UUID id) {
        if (owner.stations.remove(id) == null) return;
        owner.effectiveStations.remove(id); owner.stationRevision++;
        for (UUID train : List.copyOf(owner.dispatchRuntime.keySet())) {
            Runtime r = runtime(train);
            if (r.stoppedStation().equals(Optional.of(id)))
                put(train,new Runtime(r.schedule(),r.tripId(),Optional.empty(),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty()));
            else if (r.assignment().filter(a -> a.visits().stream().anyMatch(v -> v.station().equals(Optional.of(id)))).isPresent()) invalidate(train);
        }
        owner.dispatchChanged();
    }
    public void cargoChanged(UUID car) { inventoryChanged(car); }
    public void inventoryChanged(UUID car) {
        for (Train t : owner.trains().values()) if (t.cars().contains(car)) {
            Runtime r = runtime(t.id());
            if (r.arrivalTick().isPresent()) put(t.id(),new Runtime(r.schedule(),r.tripId(),r.assignment(),r.stoppedStation(),r.arrivalTick(),OptionalLong.of(owner.railClock),r.waitComplete(),r.hold()));
            return;
        }
    }
    public void passengers(UUID train, boolean present, int robots) {
        train(train); owner.passengerInputs.put(train,new TrainWait.PassengerState(present,robots));
    }
    public TrainWait.Context context(UUID id, RailCircuitInputs inputs) {
        Runtime r = runtime(id); List<List<RailSlots.WaitSlot>> cargo = new ArrayList<>(); List<Fluid> fluids = new ArrayList<>();
        for (UUID carId : train(id).cars()) {
            Car car = owner.cars().get(carId); car.cargo().ifPresent(slots -> cargo.add(slots.waitSnapshot())); car.fluid().ifPresent(fluids::add);
        }
        return new TrainWait.Context(r.arrivalTick().isPresent()?owner.railClock-r.arrivalTick().getAsLong():0,
                r.activityTick().isPresent()?owner.railClock-r.activityTick().getAsLong():0,cargo,fluids,
                Optional.ofNullable(owner.passengerInputs.get(id)),r.stoppedStation().isPresent()?inputs.forTrain(id,r.stoppedStation()):Optional.empty());
    }
    public StationOutput stationOutput(UUID station) {
        Optional<UUID> stopped = owner.dispatchRuntime.entrySet().stream().filter(e -> e.getValue().stoppedStation().equals(Optional.of(station)))
                .map(Map.Entry::getKey).sorted().findFirst();
        Map<SignalKey,Integer> output = new HashMap<>();
        stopped.ifPresent(id -> {
            var context = context(id,RailCircuitInputs.NONE);
            TrainWait.items(context).forEach((key,value) -> output.put(key,(int)Math.min(Integer.MAX_VALUE,value)));
            TrainWait.fluids(context).forEach((key,value) -> output.put(key,(int)Math.min(Integer.MAX_VALUE,Math.max(1,Math.floor(value)))));
        });
        return new StationOutput((int)claims().stream().filter(c -> c.stationId().equals(station)).count(),stopped,output);
    }
    private void effective(RailCircuitInputs inputs) {
        Map<UUID,Station> next = new LinkedHashMap<>();
        owner.stations.forEach((id,s) -> {
            OptionalInt limit = inputs.stationLimit(id);
            next.put(id,new Station(id,s.name(),s.stop(),inputs.stationEnabled(id).orElse(s.enabled()),
                    limit.isPresent()?OptionalInt.of(Math.max(0,limit.getAsInt())):s.trainLimit()));
        });
        if (!next.equals(owner.effectiveStations)) { owner.effectiveStations.clear(); owner.effectiveStations.putAll(next); owner.stationRevision++; owner.dispatchChanged(); }
    }
    public void tick(RailCircuitInputs inputs, double brakingBonus) {
        Objects.requireNonNull(inputs);
        if (!Double.isFinite(brakingBonus) || brakingBonus < 0) throw new IllegalArgumentException("Неверный бонус торможения");
        owner.railClock = Math.addExact(owner.railClock,1); owner.dispatchChanged(); effective(inputs); TrainReservations.refreshOccupancy(owner);
        for (UUID id : owner.trains().keySet().stream().sorted().toList()) {
            Train t = train(id); if (t.mode() != Mode.AUTOMATIC) continue;
            Runtime r = runtime(id);
            if (r.stoppedStation().isPresent()) {
                reevaluateWaitingStation(id,owner.effectiveStations.get(r.stoppedStation().get())); r=runtime(id);
            }
            if (r.assignment().isPresent() && !validAssignment(t,r)) { invalidate(id); r = runtime(id); }
            if (r.arrivalTick().isPresent() && !r.waitComplete()) {
                boolean disabled = r.stoppedStation().map(owner.effectiveStations::get).map(s -> !s.enabled()).orElse(false);
                if (disabled || !r.schedule().stops().isEmpty() && TrainWait.evaluate(r.schedule().stops().get(r.schedule().current()).waits(),context(id,inputs))) {
                    r = new Runtime(r.schedule(),r.tripId(),r.assignment(),r.stoppedStation(),r.arrivalTick(),r.activityTick(),true,r.hold()); put(id,r);
                }
            }
            if (r.waitComplete() || r.assignment().isEmpty() && r.arrivalTick().isEmpty()) assign(id,r.waitComplete()?r.schedule().advance():r.schedule(),brakingBonus);
            r = runtime(id);
            if (r.assignment().isPresent()) {
                extendWaypoints(id,brakingBonus);
                move(id,inputs,brakingBonus);
                arrive(id,inputs);
            } else if (train(id).speed()!=0) TrainMotion.step(owner,id,new TrainMotion.Control(0,true,TrainMotion.Turn.STRAIGHT),brakingBonus);
        }
    }
    private boolean validAssignment(Train train, Runtime runtime) {
        Assignment a = runtime.assignment().orElseThrow();
        if (train.head().isEmpty() || a.graphRevision()!=owner.graph().revision() || a.networkRevision()!=owner.signals().revision() || a.routeRevision()!=train.routeRevision()) return false;
        TrainSchedule schedule = runtime.schedule();
        for (Visit v : a.visits()) {
            if (schedule.stops().isEmpty()) return false;
            Stop stop = schedule.stops().get(schedule.current());
            if (v.station().isPresent()) {
                Station station = owner.effectiveStations.get(v.station().get());
                if (station==null || !station.enabled() || !stop.station().equals(Optional.of(station.name())) || !station.stop().equals(v.point())) return false;
            }
            schedule = schedule.advance();
        }
        return true;
    }
    private void invalidate(UUID id) {
        Runtime r=runtime(id);
        put(id,new Runtime(r.schedule(),r.tripId(),Optional.empty(),r.stoppedStation(),r.arrivalTick(),r.activityTick(),r.waitComplete(),r.hold()));
        owner.dispatchTrain(id,train(id).mode(),Status.NO_PATH,true);
    }
    private boolean room(UUID station, List<Claim> proposed, UUID replacingTrain, boolean releaseHold) {
        Station s=owner.effectiveStations.get(station); if(s==null || !s.enabled()) return false;
        if(s.trainLimit().isEmpty()) return true;
        long count=claims().stream().filter(c -> c.stationId().equals(station) && (!c.trainId().equals(replacingTrain)
                || !releaseHold && runtime(replacingTrain).hold().filter(c::equals).isPresent())).count();
        count+=proposed.stream().filter(c -> c.stationId().equals(station)).count();
        return count < s.trainLimit().getAsInt();
    }
    private Selection select(Train train, Stop stop, List<Claim> proposed, Optional<Cursor> from, int fixedSign, boolean loop) {
        List<Station> stations=stop.station().isPresent()?owner.effectiveStations.values().stream()
                .filter(s -> s.enabled() && s.name().equals(stop.station().get())).sorted(java.util.Comparator.comparing(Station::id)).toList():List.of();
        if(stop.station().isPresent() && stations.isEmpty()) return new Selection(Optional.empty(),Status.NO_PATH,true);
        List<Choice> reachable=new ArrayList<>();
        for(int sign:new int[]{1,-1}) {
            if(fixedSign!=0 && sign!=fixedSign || train.speed()!=0 && Math.signum(train.speed())!=sign
                    || train.cars().stream().map(owner.cars()::get).noneMatch(c -> c.kind()==StockKind.LOCOMOTIVE && (c.reversed()?-1:1)==sign)) continue;
            Cursor start=from.orElseGet(() -> TrainReservations.leading(owner,train.id(),sign).start());
            if(stop.station().isPresent()) for(Station station:stations)
                path(start,station.stop(),loop).ifPresent(route -> reachable.add(new Choice(Optional.of(station.id()),station.stop(),route,sign)));
            else {
                RailTarget target=stop.railTarget().orElseThrow(); List<Cursor> goals=new ArrayList<>(List.of(target.point()));
                if(owner.graph().piece(target.point().traversal().piece()).isEmpty()) continue;
                if(target.eitherDirection()) goals.add(new Cursor(RailwayState.reverse(target.point().traversal()),owner.railLength(target.point().traversal())-target.point().distance()));
                for(Cursor goal:goals) path(start,goal,loop).ifPresent(route -> reachable.add(new Choice(Optional.empty(),goal,route,sign)));
            }
        }
        if(reachable.isEmpty()) return new Selection(Optional.empty(),Status.NO_PATH,false);
        return new Selection(reachable.stream().filter(c -> c.station().isEmpty() || room(c.station().get(),proposed,train.id(),true))
                .min(java.util.Comparator.comparingDouble((Choice c)->c.route().length()).thenComparing(c->c.station().orElse(new UUID(0,0))).thenComparingInt(Choice::sign)),Status.DESTINATION_FULL,false);
    }
    private Optional<RailRouter.Route> path(Cursor start, Cursor end, boolean requireLoop) {
        if(owner.graph().piece(end.traversal().piece()).isEmpty()) return Optional.empty();
        Optional<RailRouter.Route> route=RailRouter.route(owner.graph(),start,Set.of(end),owner.signals()::allowed);
        if(!requireLoop || route.isEmpty() || route.get().length()>1e-9) return route;
        List<RailRouter.Route> cycles=new ArrayList<>();
        for(Traversal next:owner.graph().next(start.traversal())) if(owner.signals().allowed(start.traversal(),next))
            RailRouter.route(owner.graph(),new Cursor(next,0),Set.of(end),owner.signals()::allowed).ifPresent(tail -> {
                var steps=new ArrayList<Traversal>();steps.add(start.traversal());steps.addAll(tail.steps());
                cycles.add(new RailRouter.Route(steps,owner.railLength(start.traversal())-start.distance()+tail.length(),owner.graph().revision()));
            });
        return cycles.stream().min(java.util.Comparator.comparingDouble(RailRouter.Route::length));
    }
    private void assign(UUID id, TrainSchedule desired, double bonus) {
        Train t=train(id); Runtime r=runtime(id);
        if(desired.stops().isEmpty()) {
            put(id,cleared(r,desired,r.tripId()));status(id,Status.NO_SCHEDULE);TrainReservations.releaseFuture(owner,id);return;
        }
        if(t.head().isEmpty()) { status(id,Status.NO_PATH);return; }
        TrainSchedule selected=desired;
        for(int scanned=0;scanned<desired.stops().size();scanned++) {
            Selection selection=select(t,selected.stops().get(selected.current()),List.of(),Optional.empty(),0,r.waitComplete());
            if(selection.skip()) { selected=selected.skip();continue; }
            if(selection.choice().isEmpty()) {
                if(!r.waitComplete()) put(id,cleared(r,selected,r.tripId()));
                status(id,selection.failure());return;
            }
            Choice choice=selection.choice().get();long trip=Math.addExact(r.tripId(),1);
            Visit visit=new Visit(choice.station(),choice.point(),selected.current(),trip,choice.route().steps().size()-1,choice.route().length());
            Assignment assignment=new Assignment(choice.sign(),t.routeRevision()+1,owner.graph().revision(),owner.signals().revision(),choice.route().steps(),0,List.of(visit));
            commitCandidate(new Candidate(t,owner.graph().revision(),owner.signals().revision(),owner.stationRevision,r,selected,assignment));return;
        }
        status(id,Status.NO_PATH);
    }
    boolean commitCandidate(Candidate candidate) {
        UUID id=candidate.train().id();
        if(!candidate.train().equals(owner.trains().get(id)) || !candidate.previous().equals(runtime(id)) || candidate.graphRevision()!=owner.graph().revision()
                || candidate.networkRevision()!=owner.signals().revision() || candidate.stationRevision()!=owner.stationRevision) return false;
        List<Claim> proposed=new ArrayList<>();
        for(Visit v:candidate.assignment().visits()) if(v.station().isPresent()) {
            if(!room(v.station().get(),proposed,id,true)) return false;proposed.add(claim(id,v));
        }
        Runtime next=new Runtime(candidate.schedule(),candidate.assignment().visits().getFirst().tripId(),Optional.of(candidate.assignment()),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty());
        put(id,next);owner.dispatchTrain(id,Mode.AUTOMATIC,Status.ON_THE_PATH,true);return true;
    }
    private void extendWaypoints(UUID id, double bonus) {
        Runtime r=runtime(id);Assignment a=r.assignment().orElseThrow();Train t=train(id);
        TrainSchedule schedule=r.schedule();for(int i=0;i<a.visits().size()-1;i++) schedule=schedule.advance();
        if(schedule.stops().isEmpty() || !schedule.stops().get(schedule.current()).waits().isEmpty()) return;
        double horizon=TrainMotion.lookaheadDistance(t,t.cars().stream().map(owner.cars()::get).toList(),a.sign(),bonus);
        List<Visit> visits=new ArrayList<>(a.visits());List<Traversal> route=new ArrayList<>(a.route());
        List<Claim> proposed=new ArrayList<>();for(Visit v:visits) if(v.station().isPresent()) proposed.add(claim(id,v));
        boolean changed=false;
        // Один круг расписания может быть короче тормозного пути. Каждый дополнительный
        // проход имеет положительную длину (path требует петлю для совпавшей цели).
        while(visits.getLast().remaining()<=horizon+1e-8) {
            if(!schedule.stops().get(schedule.current()).waits().isEmpty()) break;
            TrainSchedule next=schedule.advance();if(next.stops().isEmpty()) break;
            Visit previous=visits.getLast();Selection selection=select(t,next.stops().get(next.current()),proposed,Optional.of(previous.point()),a.sign(),true);
            if(selection.skip() || selection.choice().isEmpty()) break;
            Choice choice=selection.choice().get();int occurrence=a.routeIndex()+route.size()+choice.route().steps().size()-2;
            route.addAll(choice.route().steps().subList(1,choice.route().steps().size()));
            Visit visit=new Visit(choice.station(),choice.point(),next.current(),previous.tripId()+1,occurrence,previous.remaining()+choice.route().length());
            visits.add(visit);if(visit.station().isPresent()) proposed.add(claim(id,visit));schedule=next;changed=true;
        }
        if(changed) {
            Assignment next=new Assignment(a.sign(),t.routeRevision()+1,a.graphRevision(),a.networkRevision(),route,a.routeIndex(),visits);
            put(id,new Runtime(r.schedule(),r.tripId(),Optional.of(next),r.stoppedStation(),r.arrivalTick(),r.activityTick(),r.waitComplete(),r.hold()));
            owner.dispatchTrain(id,t.mode(),t.status(),true);
        }
    }
    private void move(UUID id, RailCircuitInputs inputs, double bonus) {
        Runtime r=runtime(id);Assignment a=r.assignment().orElseThrow();Train t=train(id);
        double limit=Math.max(0,a.visits().getLast().remaining());var leading=TrainReservations.leading(owner,id,a.sign());Cursor start=leading.start();
        int precedingIndex=leading.tapeIndex()-a.sign();
        if(start.distance()==0 && precedingIndex>=0 && precedingIndex<t.tape().size()) {
            Traversal preceding=t.tape().get(precedingIndex);if(a.sign()<0) preceding=RailwayState.reverse(preceding);
            if(owner.signals().at(preceding,start.traversal()).stream().anyMatch(s->inputs.signalClosed(s.id()))) limit=0;
        }
        double offset=-start.distance();
        for(int i=1;i<a.route().size();i++) {
            offset+=owner.railLength(a.route().get(i-1));
            if(owner.signals().at(a.route().get(i-1),a.route().get(i)).stream().anyMatch(s->inputs.signalClosed(s.id()))) { limit=Math.min(limit,offset);break; }
        }
        List<Traversal> movementRoute=new ArrayList<>();double covered=-start.distance();
        for(Traversal step:a.route()) {movementRoute.add(step);covered+=owner.railLength(step);if(covered>=limit) break;}
        var plan=TrainReservations.plan(owner,id,new RailRouter.Route(movementRoute,limit,owner.graph().revision()),a.sign(),bonus);
        boolean committed=TrainReservations.commit(owner,id,plan,a.sign(),bonus);
        if(!committed) { TrainMotion.step(owner,id,new TrainMotion.Control(0,true,TrainMotion.Turn.STRAIGHT),bonus);return; }
        double available=Math.min(limit,plan.safeDistance());
        List<Car> cars=t.cars().stream().map(owner.cars()::get).toList();
        double throttle=1;boolean brake=false;
        if(required(t,cars,a.sign(),1,bonus)>available) {
            if(required(t,cars,a.sign(),0,bonus)>available || available<=1e-12) brake=true;
            else {
                double low=0,high=1;
                for(int i=0;i<60;i++) {double mid=(low+high)/2;if(required(t,cars,a.sign(),mid,bonus)<=available) low=mid;else high=mid;}
                throttle=low;
            }
        }
        TrainMotion.step(owner,id,new TrainMotion.Control(a.sign()*throttle,brake,TrainMotion.Turn.STRAIGHT),bonus);
        if(train(id).speed()==0 && runtime(id).assignment().isPresent() && runtime(id).assignment().get().visits().getLast().remaining()>1e-6
                && plan.safeDistance()+1e-8<a.visits().getLast().remaining()) status(id,Status.WAIT_SIGNAL);
    }
    private double required(Train t,List<Car> cars,int sign,double throttle,double bonus) {
        double speed=TrainMotion.previewSpeed(t,cars,new TrainMotion.Control(sign*throttle,false,TrainMotion.Turn.STRAIGHT),bonus);
        Train after=new Train(t.id(),t.cars(),t.head(),t.headIndex(),t.tape(),sign*speed,t.mode(),t.status(),t.routeRevision(),t.consistRevision(),t.motionRevision());
        return speed+TrainMotion.stoppingDistance(after,cars,bonus);
    }
    void motionCommitted(Train before,Train after,int sign,double distance) {
        Runtime r=runtime(before.id());if(r.assignment().isEmpty() || r.assignment().get().sign()!=sign) return;
        Assignment a=r.assignment().get();Cursor start=TrainReservations.leading(owner,before.id(),sign).start();
        // Владелец уже записал after. Начальный offset выводится из старой неизменяемой ленты.
        double q=owner.headCoordinate(before)+(sign>0?3.5:-7*(before.cars().size()-1)-3.5);
        Cursor old=owner.locate(before.tape(),q).cursor();double left=(sign>0?old.distance():owner.railLength(old.traversal())-old.distance())+distance;int index=0;
        while(index<a.route().size()-1 && left>=owner.railLength(a.route().get(index))) {left-=owner.railLength(a.route().get(index));index++;}
        if(sign<0 && left==0 && index>0 && start.traversal().equals(a.route().get(index-1))) index--;
        Assignment next=new Assignment(sign,after.routeRevision(),a.graphRevision(),a.networkRevision(),a.route().subList(index,a.route().size()),a.routeIndex()+index,a.visits().stream().map(v->v.moved(distance)).toList());
        put(before.id(),new Runtime(r.schedule(),r.tripId(),Optional.of(next),r.stoppedStation(),r.arrivalTick(),r.activityTick(),r.waitComplete(),r.hold()));
    }
    private void arrive(UUID id,RailCircuitInputs inputs) {
        Runtime r=runtime(id);
        for(int n=0;n<r.schedule().stops().size()+1 && r.assignment().isPresent();n++) {
            Assignment a=r.assignment().get();Visit visit=a.visits().getFirst();Stop stop=r.schedule().stops().get(r.schedule().current());
            if(visit.remaining()>1e-6 || a.routeIndex()<visit.occurrence()) return;
            if(!stop.waits().isEmpty()) {
                if(train(id).speed()!=0) return;
                Optional<Claim> hold=visit.station().map(station->claim(id,visit));
                r=new Runtime(r.schedule(),visit.tripId(),Optional.empty(),visit.station(),OptionalLong.of(owner.railClock),OptionalLong.of(owner.railClock),false,hold);
                put(id,r);status(id,Status.WAIT_STATION);return;
            }
            TrainSchedule next=r.schedule().advance();List<Visit> pending=a.visits().subList(1,a.visits().size());
            Optional<Assignment> assignment=pending.isEmpty()?Optional.empty():Optional.of(new Assignment(a.sign(),a.routeRevision(),a.graphRevision(),a.networkRevision(),a.route(),a.routeIndex(),pending));
            r=new Runtime(next,visit.tripId(),assignment,Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty());put(id,r);
            if(next.stops().isEmpty()) { status(id,Status.NO_SCHEDULE);return; }
        }
    }
    void consistsChanged(List<Train> previous,List<Train> replacements) {
        TrainSchedule schedule=previous.isEmpty()?TrainSchedule.empty():runtime(previous.getFirst().id()).schedule();
        long trip=previous.stream().mapToLong(t->runtime(t.id()).tripId()).max().orElse(0)+1;
        previous.forEach(t->{owner.dispatchRuntime.remove(t.id());owner.passengerInputs.remove(t.id());});
        for(Train t:replacements) put(t.id(),new Runtime(schedule,trip,Optional.empty(),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty()));
    }
    /** Загрузка после физических вагонов/графа. Невалидный путь не уничтожает расписание и груз. */
    public void restore(long clock, long revision, Collection<Station> stations, Map<UUID,Runtime> runtimes) {
        if(clock<0 || revision<0 || !owner.dispatchRuntime.isEmpty() || !owner.stations.isEmpty()) throw new IllegalArgumentException("Неверная загрузка диспетчера");
        Set<UUID> ids=new HashSet<>();
        for(Station station:stations) { if(!ids.add(station.id())) throw new IllegalArgumentException("Повторная станция");upsertStation(station); }
        owner.railClock=clock;owner.stationRevision=revision;
        for(var entry:runtimes.entrySet()) {
            UUID id=entry.getKey();Train t=train(id);Runtime r=entry.getValue();
            if(r.arrivalTick().isPresent() && (r.arrivalTick().getAsLong()>clock || r.activityTick().getAsLong()>clock)) throw new IllegalArgumentException("Часы из будущего");
            if(r.hold().filter(c->!c.trainId().equals(id)).isPresent()) throw new IllegalArgumentException("Чужой hold");
            boolean stale=t.head().isEmpty() && (r.assignment().isPresent() || r.arrivalTick().isPresent())
                    || t.mode()==Mode.MANUAL && (r.assignment().isPresent() || r.arrivalTick().isPresent() || r.hold().isPresent());
            if(r.stoppedStation().isPresent()) {
                Station station=owner.stations.get(r.stoppedStation().get());
                stale |= station==null || t.speed()!=0 || t.head().isEmpty() || !atPoint(t,station.stop());
            }
            if(r.arrivalTick().isPresent() && r.stoppedStation().isEmpty()) {
                Optional<RailTarget> target=r.schedule().stops().isEmpty()?Optional.empty():r.schedule().stops().get(r.schedule().current()).railTarget();
                stale |= target.isEmpty() || !atRailTarget(t,target.get());
            }
            if(r.assignment().isPresent() && !stale) {
                Assignment a=r.assignment().get();
                // Graph revision живет в текущем экземпляре; сохраненный маршрут проверяется целиком перед перепривязкой.
                stale=!restorable(t,a);
                if(!stale) r=new Runtime(r.schedule(),r.tripId(),Optional.of(new Assignment(a.sign(),a.routeRevision(),owner.graph().revision(),a.networkRevision(),a.route(),a.routeIndex(),a.visits())),r.stoppedStation(),r.arrivalTick(),r.activityTick(),r.waitComplete(),r.hold());
            }
            if(stale) {
                r=new Runtime(r.schedule(),r.tripId(),Optional.empty(),Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),false,Optional.empty());
                if(t.mode()==Mode.AUTOMATIC && t.head().isPresent()) owner.dispatchTrain(id,t.mode(),Status.NO_PATH,true);
            }
            put(id,r);
        }
        owner.passengerInputs.clear();
    }
    private boolean atPoint(Train t,Cursor point) {
        if(t.head().isEmpty()) return false;
        for(int sign:new int[]{1,-1}) {
            Cursor leading=TrainReservations.leading(owner,t.id(),sign).start();
            if(leading.traversal().equals(point.traversal()) && Math.abs(leading.distance()-point.distance())<=1e-6) return true;
        }
        return false;
    }
    private boolean atRailTarget(Train t,RailTarget target) {
        Cursor point=target.point();
        if(t.speed()!=0 || t.head().isEmpty() || owner.graph().piece(point.traversal().piece()).isEmpty()
                || point.distance()>owner.railLength(point.traversal())) return false;
        if(atPoint(t,point)) return true;
        return target.eitherDirection() && atPoint(t,new Cursor(RailwayState.reverse(point.traversal()),owner.railLength(point.traversal())-point.distance()));
    }
    private boolean restorable(Train t,Assignment a) {
        if(a.routeRevision()!=t.routeRevision() || a.networkRevision()!=owner.signals().revision() || t.speed()!=0 && Math.signum(t.speed())!=a.sign()) return false;
        Cursor start=TrainReservations.leading(owner,t.id(),a.sign()).start();
        if(!a.route().getFirst().equals(start.traversal())) return false;
        for(int i=0;i<a.route().size();i++) if(owner.graph().piece(a.route().get(i).piece()).isEmpty()
                || i>0 && !owner.signals().allowed(a.route().get(i-1),a.route().get(i))) return false;
        for(Visit v:a.visits()) {
            if(v.station().isPresent() && !owner.stations.containsKey(v.station().get())) return false;
            int index=v.occurrence()-a.routeIndex();
            if(index<0 || index>=a.route().size() || !a.route().get(index).equals(v.point().traversal())) return false;
            double distance=-start.distance()+v.point().distance();
            for(int i=0;i<index;i++) distance+=owner.railLength(a.route().get(i));
            if(Math.abs(distance-v.remaining())>1e-6) return false;
        }
        return true;
    }
}
