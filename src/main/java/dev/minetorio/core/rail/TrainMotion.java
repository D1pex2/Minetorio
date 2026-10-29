package dev.minetorio.core.rail;

import dev.minetorio.core.data.RailData;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.RailwayState.Car;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Один исходный шаг 1/60 с. Оркестратор мира позднее вызывает его трижды за тик. */
public final class TrainMotion {
    private static final MathContext MC = MathContext.DECIMAL128;
    private TrainMotion() {}
    public enum Turn { LEFT, STRAIGHT, RIGHT }
    public record Control(double throttle, boolean brake, Turn turn) {}
    private record Mover(Car car, double modifier, BigDecimal budget, TrainFuel.Draw preview) {}
    private record Path(List<Traversal> tape, double head, double distance) {}

    public static void step(RailwayState state, UUID id, Control control, double bonus) {
        Objects.requireNonNull(control); Objects.requireNonNull(control.turn());
        checkBonus(bonus);
        if (!Double.isFinite(control.throttle()) || Math.abs(control.throttle()) > 1) throw new IllegalArgumentException("Недопустимая тяга");
        Train train = Objects.requireNonNull(state.trains().get(id));
        if (train.head().isEmpty()) return;
        List<Car> cars = train.cars().stream().map(state.cars()::get).toList();
        double weight = cars.stream().mapToDouble(c -> RailData.stock(c.kind()).weight()).sum();
        double sign = train.speed() != 0 ? Math.signum(train.speed()) : Math.signum(control.throttle());
        boolean brake = control.brake() || train.speed() * control.throttle() < 0;
        double magnitude;
        List<Mover> movers = new ArrayList<>();
        BigDecimal lambda = BigDecimal.ZERO;
        double u = 0, air = 1;
        if (brake) magnitude = brakeSpeed(Math.abs(train.speed()), cars, bonus, weight);
        else if (sign == 0) magnitude = 0;
        else {
            u = Math.max(0, Math.abs(train.speed()) - cars.stream().mapToDouble(c -> RailData.stock(c.kind()).friction()).sum()/weight);
            Car leading = cars.get(sign > 0 ? 0 : cars.size()-1);
            air = 1 - RailData.stock(leading.kind()).air()*1000/weight;
            if (!Double.isFinite(air) || air <= 0 || !Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Недопустимые параметры состава");
            double needed = Math.max(0, speedCap(cars,Map.of())/air-u);
            if (control.throttle() != 0) for (Car car : cars) if (car.kind() == StockKind.LOCOMOTIVE) {
                double modifier = (car.reversed() ? -1 : 1) == Math.signum(control.throttle()) ? 1 : train.mode() == Mode.MANUAL ? 0.6 : 0;
                if (modifier == 0) continue;
                BigDecimal budget = BigDecimal.valueOf(10000*Math.abs(control.throttle()));
                movers.add(new Mover(car,modifier,budget,TrainFuel.preview(car,budget)));
            }
            lambda = solve(movers, BigDecimal.valueOf(needed).multiply(BigDecimal.valueOf(weight)), BigDecimal.ONE);
            Map<UUID,Double> endModifiers = new HashMap<>();
            for (Mover m : movers) {
                TrainFuel.Draw draw = TrainFuel.preview(m.car(),request(m,lambda));
                if (!draw.portions().isEmpty()) endModifiers.put(m.car().id(),draw.portions().getLast().topSpeed());
            }
            magnitude = Math.min((u+force(movers,lambda).doubleValue()/weight)*air,speedCap(cars,endModifiers));
        }
        // Причина стоянки меняется лишь при установленном препятствии для движения.
        Status stoppedStatus = train.status();
        if (!brake && control.throttle() != 0 && !movers.isEmpty()
                && movers.stream().allMatch(m -> m.preview().deliveredJ().signum() == 0))
            stoppedStatus = Status.NO_FUEL;
        // Выбор ветки и проверка полного тела выполняются до первого расхода топлива.
        long graphRevision = state.graph().revision();
        Path path = extend(state, train, sign, magnitude, control.turn());
        if (magnitude > 0 && path.distance() == 0) {
            commit(state,train,graphRevision,path,0,sign,Status.NO_PATH);
            return;
        }
        if (!brake && !movers.isEmpty()) {
            double traction = 0;
            for (Mover mover : movers) for (TrainFuel.Portion portion : TrainFuel.draw(mover.car(),request(mover,lambda)).portions())
                traction += 10*portion.acceleration()*mover.modifier()*portion.joules().doubleValue()/10000/weight;
            magnitude = Math.min((u+traction)*air,speedCap(cars,Map.of()));
        }
        double distance = Math.min(magnitude,path.distance());
        boolean blockedByTrackEnd = distance + 1e-12 < magnitude;
        double speed = blockedByTrackEnd ? 0 : magnitude*sign;
        commit(state,train,graphRevision,new Path(path.tape(),path.head(),distance),speed,sign,
                blockedByTrackEnd ? Status.NO_PATH : stoppedStatus);
    }
    private static void commit(RailwayState state,Train train,long revision,Path path,double speed,double sign,
                               Status stoppedStatus) {
        var tape = new ArrayList<>(path.tape());
        double q = path.head()+sign*path.distance();
        // Удаляется лишь полностью вышедшая история позади движущегося состава.
        if (sign > 0) while (tape.size()>1 && q-7*(train.cars().size()-1)-3.5 >= state.railLength(tape.getFirst())) {
            q-=state.railLength(tape.removeFirst());
        }
        if (sign < 0) while (tape.size()>1 && q+3.5 <= state.tapeLength(tape)-state.railLength(tape.getLast())) tape.removeLast();
        var head = state.locate(tape,q);
        Status status = train.mode() == Mode.MANUAL ? speed == 0 ? Status.MANUAL_CONTROL_STOP : Status.MANUAL_CONTROL
                : speed == 0 ? stoppedStatus : Status.ON_THE_PATH;
        if (!state.commitMotion(train,revision,new MotionUpdate(Optional.of(head.cursor()),head.index(),tape,speed,status)))
            throw new IllegalStateException("Владелец изменился внутри последовательного шага");
    }
    private static BigDecimal request(Mover m,BigDecimal lambda) { return m.budget().multiply(lambda,MC).max(BigDecimal.ZERO).min(m.budget()); }
    /** Сила по доступным порциям, с точными границами энергии в decimal. */
    private static BigDecimal force(List<Mover> movers,BigDecimal lambda) {
        BigDecimal total = BigDecimal.ZERO;
        for (Mover m : movers) {
            BigDecimal left=request(m,lambda);
            for (var p:m.preview().portions()) {
                BigDecimal used=left.min(p.joules());
                total=total.add(used.multiply(BigDecimal.valueOf(p.acceleration())).multiply(BigDecimal.valueOf(m.modifier())).movePointLeft(3));
                left=left.subtract(used); if(left.signum()==0) break;
            }
        }
        return total;
    }
    private static BigDecimal solve(List<Mover> movers,BigDecimal needed,BigDecimal one) {
        if(needed.signum()==0) return BigDecimal.ZERO;
        if(force(movers,one).compareTo(needed)<=0) return one;
        TreeSet<BigDecimal> boundaries=new TreeSet<>(); boundaries.add(BigDecimal.ZERO); boundaries.add(one);
        for(Mover m:movers) {
            BigDecimal cumulative=BigDecimal.ZERO;
            for(var p:m.preview().portions()) { cumulative=cumulative.add(p.joules()); boundaries.add(cumulative.divide(m.budget(),MC).min(one)); }
        }
        BigDecimal previous=BigDecimal.ZERO, previousForce=BigDecimal.ZERO;
        for(BigDecimal boundary:boundaries) {
            BigDecimal at=force(movers,boundary);
            if(at.compareTo(needed)>=0) return previous.add(needed.subtract(previousForce).multiply(boundary.subtract(previous)).divide(at.subtract(previousForce),MC)).max(BigDecimal.ZERO).min(one);
            previous=boundary; previousForce=at;
        }
        return one;
    }
    static double speedCap(List<Car> cars,Map<UUID,Double> modifiers) {
        double base=cars.stream().mapToDouble(c->RailData.stock(c.kind()).maxSpeed()).min().orElseThrow();
        double average=cars.stream().filter(c->c.kind()==StockKind.LOCOMOTIVE).mapToDouble(c->modifiers.getOrDefault(c.id(),TrainFuel.activeTopSpeed(c))).average().orElse(1);
        return Math.min(7386.3/216,base*average);
    }
    private static Path extend(RailwayState state,Train train,double sign,double movement,Turn turn) {
        List<Traversal> tape=new ArrayList<>(train.tape());
        double q=state.headCoordinate(train), total=state.tapeLength(tape), tail=7*(train.cars().size()-1)+3.5;
        while(sign>0 && q+movement+3.5>total || sign<0 && q-movement-tail<0) {
            Traversal leading=sign>0?tape.getLast():RailwayState.reverse(tape.getFirst());
            List<Traversal> next=state.graph().next(leading);
            if(next.isEmpty()) break;
            Traversal chosen=choose(state,leading,next,turn);
            double added=state.railLength(chosen);
            if(sign>0) tape.add(chosen); else { tape.addFirst(RailwayState.reverse(chosen)); q+=added; }
            total+=added;
        }
        double available=sign>0?total-q-3.5:sign<0?q-tail:0;
        return new Path(List.copyOf(tape),q,Math.max(0,Math.min(movement,available)));
    }
    private static Traversal choose(RailwayState state,Traversal current,List<Traversal> candidates,Turn turn) {
        Vec from=direction(state,current);
        Comparator<Traversal> byAngle=Comparator.comparingDouble(t->{ Vec to=direction(state,t); return Math.atan2(from.x()*to.z()-from.z()*to.x(),from.x()*to.x()+from.z()*to.z()); });
        return switch(turn) {
            case LEFT -> candidates.stream().min(byAngle).orElseThrow();
            case RIGHT -> candidates.stream().max(byAngle).orElseThrow();
            case STRAIGHT -> candidates.stream().min(Comparator.comparingDouble(t->{Vec to=direction(state,t);return Math.abs(Math.atan2(from.x()*to.z()-from.z()*to.x(),from.x()*to.x()+from.z()*to.z()));})).orElseThrow();
        };
    }
    private static Vec direction(RailwayState state,Traversal traversal) {
        Piece piece=state.graph().piece(traversal.piece()).orElseThrow();
        Vec t=sample(piece,traversal.forward()?length(piece):0).tangent();
        return traversal.forward()?t:new Vec(-t.x(),-t.y(),-t.z());
    }
    private static void checkBonus(double bonus) { if(!Double.isFinite(bonus)||bonus<0) throw new IllegalArgumentException("Недопустимый бонус торможения"); }
    private static double brakeSpeed(double speed,List<Car> cars,double bonus,double weight) {
        return Math.max(0,speed-cars.stream().mapToDouble(c->RailData.stock(c.kind()).brake()).sum()*(1+bonus)/weight);
    }
    public static double stoppingDistance(Train train,List<Car> cars,double bonus) {
        checkBonus(bonus);
        if(cars.isEmpty() || !cars.stream().map(Car::id).toList().equals(train.cars())) throw new IllegalArgumentException("Неверный состав");
        double weight=cars.stream().mapToDouble(c->RailData.stock(c.kind()).weight()).sum(), speed=Math.abs(train.speed()),distance=0;
        while(speed>0) { speed=brakeSpeed(speed,cars,bonus,weight); distance+=speed; }
        return distance;
    }
    public static Map<UUID,Pose> carPoses(RailwayState state,UUID id) { return state.poses(Objects.requireNonNull(state.trains().get(id)),Set.of()); }
}
