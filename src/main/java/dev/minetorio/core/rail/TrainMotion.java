package dev.minetorio.core.rail;

import dev.minetorio.core.data.RailData;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.rail.RailwayState.Car;
import java.math.BigDecimal;
import java.util.*;
import static dev.minetorio.core.rail.RailwayState.*;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Один исходный шаг 1/60 с. Оркестратор мира позднее вызывает его трижды за тик. */
public final class TrainMotion {
    private TrainMotion() {}
    public enum Turn { LEFT, STRAIGHT, RIGHT }
    public enum DriverContext { SEATED, ABSENT }
    public record Control(double throttle, boolean brake, Turn turn, DriverContext driver) {
        public Control { Objects.requireNonNull(driver); }
        public Control(double throttle,boolean brake,Turn turn) {this(throttle,brake,turn,DriverContext.SEATED);}
    }
    private record Mover(Car car, double modifier, BigDecimal budget, TrainFuel.Draw preview) {}
    private record Path(List<Traversal> tape, double head, double distance) {}

    public static void step(RailwayState state, UUID id, Control control, double bonus) {
        propose(state,id,control,bonus).ifPresent(Proposal::commit);
    }
    public static Optional<Proposal> propose(RailwayState state, UUID id, Control control, double bonus) {
        Objects.requireNonNull(control); Objects.requireNonNull(control.turn());
        checkBonus(bonus);
        if (!Double.isFinite(control.throttle()) || Math.abs(control.throttle()) > 1) throw new IllegalArgumentException("Недопустимая тяга");
        Train train = Objects.requireNonNull(state.trains().get(id));
        if (train.head().isEmpty()) return Optional.empty();
        List<Car> cars = train.cars().stream().map(state.cars()::get).toList();
        Dynamics dynamics=calculate(train,cars,control,bonus);
        double sign=dynamics.sign(), magnitude=dynamics.magnitude();
        boolean brake=dynamics.brake();
        List<Mover> movers=dynamics.movers();
        // Причина стоянки меняется лишь при установленном препятствии для движения.
        Status stoppedStatus = train.status();
        if (!brake && control.throttle() != 0 && !movers.isEmpty()
                && movers.stream().allMatch(m -> m.preview().deliveredJ().signum() == 0))
            stoppedStatus = Status.NO_FUEL;
        // Выбор ветки и проверка полного тела выполняются до первого расхода топлива.
        long graphRevision = state.graph().revision();
        Path path = extend(state, train, sign, magnitude, control.turn());
        if(train.mode()==Mode.AUTOMATIC && !TrainReservations.permits(state,train,(int)sign,bonus,brake,magnitude,magnitude*sign)) {
            double brakingSpeed=brakeSpeed(Math.abs(train.speed()),cars,bonus,cars.stream().mapToDouble(c->RailData.stock(c.kind()).weight()).sum());
            if(!TrainReservations.permits(state,train,(int)sign,bonus,true,brakingSpeed,brakingSpeed*sign)) return Optional.empty();
            brake=true; magnitude=brakingSpeed;
            path=extend(state,train,sign,magnitude,control.turn());
        }
        if (magnitude > 0 && path.distance() == 0 && train.mode()==Mode.MANUAL) {
            return Optional.of(proposal(state,train,graphRevision,path,0,0,sign,Status.NO_PATH,bonus,brake,control.driver(),()->{}));
        }
        if(train.mode()==Mode.AUTOMATIC && path.distance()+1e-12<magnitude) return Optional.empty();
        double distance = Math.min(magnitude,path.distance());
        boolean blockedByTrackEnd = distance + 1e-12 < magnitude;
        double speed = blockedByTrackEnd ? 0 : magnitude*sign;
        boolean spend=!brake;
        Runnable fuel=()-> { if(spend) for(Mover mover:movers) TrainFuel.draw(mover.car(),mover.budget()); };
        return Optional.of(proposal(state,train,graphRevision,new Path(path.tape(),path.head(),distance),speed,magnitude*sign,sign,
                blockedByTrackEnd ? Status.NO_PATH : stoppedStatus,bonus,brake,control.driver(),fuel));
    }
    private record Dynamics(double sign,double magnitude,boolean brake,List<Mover> movers) {}
    /** Чистый расчет для регулятора прибытия; списания топлива и изменения позы здесь нет. */
    static double previewSpeed(Train train, List<Car> cars, Control control, double bonus) {
        checkBonus(bonus);
        return calculate(train, cars, control, bonus).magnitude();
    }
    private static Dynamics calculate(Train train,List<Car> cars,Control control,double bonus) {
        double weight = cars.stream().mapToDouble(c -> RailData.stock(c.kind()).weight()).sum();
        double sign = train.speed() != 0 ? Math.signum(train.speed()) : Math.signum(control.throttle());
        boolean brake = control.brake() || train.speed() * control.throttle() < 0;
        double magnitude;
        List<Mover> movers = new ArrayList<>();
        double u = 0, air = 1;
        if (brake) magnitude = brakeSpeed(Math.abs(train.speed()), cars, bonus, weight);
        else if (sign == 0) magnitude = 0;
        else {
            u = Math.max(0, Math.abs(train.speed()) - cars.stream().mapToDouble(c -> RailData.stock(c.kind()).friction()).sum()/weight);
            Car leading = cars.get(sign > 0 ? 0 : cars.size()-1);
            air = 1 - RailData.stock(leading.kind()).air()*1000/weight;
            if (!Double.isFinite(air) || air <= 0 || !Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Недопустимые параметры состава");
            if (control.throttle() != 0) for (Car car : cars) if (car.kind() == StockKind.LOCOMOTIVE) {
                double modifier = (car.reversed() ? -1 : 1) == Math.signum(control.throttle()) ? 1 : train.mode() == Mode.MANUAL ? 0.6 : 0;
                if (modifier == 0) continue;
                BigDecimal budget = BigDecimal.valueOf(10000*Math.abs(control.throttle()));
                movers.add(new Mover(car,modifier,budget,TrainFuel.preview(car,budget)));
            }
            Map<UUID,Double> endModifiers = new HashMap<>();
            for (Mover m : movers) {
                TrainFuel.Draw draw = m.preview();
                if (!draw.portions().isEmpty()) endModifiers.put(m.car().id(),draw.portions().getLast().topSpeed());
            }
            magnitude = Math.min((u+force(movers).doubleValue()/weight)*air,speedCap(cars,endModifiers));
        }
        return new Dynamics(sign,magnitude,brake,List.copyOf(movers));
    }
    /** Тот же расчет тяги и топлива, без списания. Нужен только ближайший возможный подшаг. */
    static double lookaheadDistance(Train train,List<Car> cars,int sign,double bonus) {
        double speed=calculate(train,cars,new Control(sign,false,Turn.STRAIGHT),bonus).magnitude();
        Train after=new Train(train.id(),train.cars(),train.head(),train.headIndex(),train.tape(),speed*sign,
                train.mode(),train.status(),train.routeRevision(),train.consistRevision(),train.motionRevision());
        return speed+stoppingDistance(after,cars,bonus);
    }
    private record FuelSnapshot(UUID car, List<RailSlots.Slot> slots, String active, BigDecimal joules) {}
    private static List<FuelSnapshot> fuelSnapshot(RailwayState state,Train train) {
        return train.cars().stream().map(state.cars()::get).filter(c->c.fuel().isPresent())
                .map(c->new FuelSnapshot(c.id(),List.copyOf(c.fuel().orElseThrow().snapshot()),c.activeFuel(),c.remainingJ())).toList();
    }
    /** Неподделываемый одноразовый расчет: ни путь, ни расход не принимаются от клиента. */
    public static final class Proposal {
        private final RailwayState owner;
        private final Train before;
        private final long graphRevision,networkRevision;
        private final Path path;
        private final double speed,contactSpeed,sign,bonus;
        private final Status stopped;
        private final boolean braking;
        private final DriverContext driver;
        private final Runnable fuel;
        private final List<FuelSnapshot> fuelBefore;
        private boolean used;
        private Proposal(RailwayState owner,Train before,long revision,Path path,double speed,double contactSpeed,double sign,Status stopped,double bonus,boolean braking,DriverContext driver,Runnable fuel) {
            this.owner=owner;this.before=before;this.graphRevision=revision;this.networkRevision=owner.signals().revision();this.path=path;
            this.speed=speed;this.contactSpeed=contactSpeed;this.sign=sign;this.stopped=stopped;this.bonus=bonus;this.braking=braking;this.driver=driver;this.fuel=fuel;this.fuelBefore=fuelSnapshot(owner,before);
        }
        public UUID trainId() { return before.id(); }
        public Train before() { return before; }
        public double distance() { return path.distance(); }
        public double speed() { return speed; }
        /** Скорость попытки движения до финального ограничения концом рельса. */
        public double contactSpeed() { return contactSpeed; }
        public Map<UUID,Pose> poses(double fraction) {
            if(!Double.isFinite(fraction)||fraction<0||fraction>1) throw new IllegalArgumentException("Недопустимая доля пути");
            Map<UUID,Pose> result=new LinkedHashMap<>();
            for(int i=0;i<before.cars().size();i++) {
                UUID id=before.cars().get(i);
                result.put(id,owner.pose(path.tape(),path.head()+sign*path.distance()*fraction-7*i,owner.cars().get(id).reversed()));
            }
            return Map.copyOf(result);
        }
        /** Точки тел и сцепок вычисляются на пройденной ленте, включая повороты. */
        public Pose point(int carIndex,double longitudinal,double fraction) {
            if(carIndex<0||carIndex>=before.cars().size()||Math.abs(longitudinal)>3.5||fraction<0||fraction>1)
                throw new IllegalArgumentException("Точка вне состава");
            return owner.pose(path.tape(),path.head()+sign*path.distance()*fraction-7*carIndex+longitudinal,false);
        }
        /** Проверка права на попытку контакта, без расхода топлива и без consumption proposal. */
        public boolean validFor(RailwayState state) {
            return owner==state&&!used&&before.equals(owner.trains().get(before.id()))&&graphRevision==owner.graph().revision()
                    &&networkRevision==owner.signals().revision()&&fuelBefore.equals(fuelSnapshot(owner,before))
                    &&(before.mode()!=Mode.AUTOMATIC||TrainReservations.permits(owner,before,(int)sign,bonus,braking,path.distance(),speed));
        }
        public boolean commit() { return commit(1,Math.abs(speed)); }
        /** Контакт разрешает лишь префикс уже рассчитанного пути и не увеличивает скорость. */
        public boolean commit(double fraction,double magnitude) {
            if(!Double.isFinite(fraction)||fraction<0||fraction>1||!Double.isFinite(magnitude)||magnitude<0||magnitude>Math.abs(speed)+1e-12)
                throw new IllegalArgumentException("Контакт увеличивает движение");
            boolean valid=validFor(owner);used=true;
            if(!valid)return false;
            var tape=new ArrayList<>(path.tape()); double distance=path.distance()*fraction;
            double q=path.head()+sign*distance;
            if(sign>0) while(tape.size()>1 && q-7*(before.cars().size()-1)-3.5>=owner.railLength(tape.getFirst())) q-=owner.railLength(tape.removeFirst());
            if(sign<0) while(tape.size()>1 && q+3.5<=owner.tapeLength(tape)-owner.railLength(tape.getLast())) tape.removeLast();
            var head=owner.locate(tape,q); double actualSpeed=magnitude*sign;
            Status status=before.mode()==Mode.MANUAL?(driver==DriverContext.ABSENT&&actualSpeed!=0?Status.MANUAL_CONTROL_STOP:Status.MANUAL_CONTROL):(actualSpeed==0?stopped:Status.ON_THE_PATH);
            return owner.commitMotion(before,graphRevision,new MotionUpdate(Optional.of(head.cursor()),head.index(),tape,actualSpeed,status),
                    (int)sign,distance,bonus,braking,fuel);
        }
    }
    private static Proposal proposal(RailwayState state,Train train,long revision,Path path,double speed,double contactSpeed,double sign,
                                     Status stopped,double bonus,boolean braking,DriverContext driver,Runnable fuel) {
        return new Proposal(state,train,revision,path,speed,contactSpeed,sign,stopped,bonus,braking,driver,fuel);
    }
    /** Полный бюджет и на cap (native1.1.110 coal/full throttle); сила - только из доступных порций. */
    private static BigDecimal force(List<Mover> movers) {
        BigDecimal total=BigDecimal.ZERO;
        for(Mover mover:movers) for(var portion:mover.preview().portions())
            total=total.add(portion.joules().multiply(BigDecimal.valueOf(portion.acceleration()))
                    .multiply(BigDecimal.valueOf(mover.modifier())).movePointLeft(3));
        return total;
    }
    static double speedCap(List<Car> cars,Map<UUID,Double> modifiers) {
        double base=cars.stream().mapToDouble(c->RailData.stock(c.kind()).maxSpeed()).min().orElseThrow();
        double average=cars.stream().filter(c->c.kind()==StockKind.LOCOMOTIVE).mapToDouble(c->modifiers.getOrDefault(c.id(),TrainFuel.activeTopSpeed(c))).average().orElse(1);
        return Math.min(7386.3/216,base*average);
    }
    private static Path extend(RailwayState state,Train train,double sign,double movement,Turn turn) {
        List<Traversal> tape=new ArrayList<>(train.tape());
        double q=state.headCoordinate(train), total=state.tapeLength(tape), tail=7*(train.cars().size()-1)+3.5;
        List<Traversal> authority=train.mode()==Mode.AUTOMATIC?state.reservations().continuation(train.id(),(int)sign):List.of();
        int routeIndex=1;
        if(!authority.isEmpty()) {
            var leading=TrainReservations.leading(state,train.id(),(int)sign);
            // За внешней сцепкой нет занятого тела. Его будущая ветка берется из authority;
            // вся лента под телом, в том числе ветка хвоста, остается прежней.
            if(sign>0) tape=new ArrayList<>(tape.subList(0,leading.tapeIndex()+1));
            else {
                for(int i=0;i<leading.tapeIndex();i++) q-=state.railLength(tape.get(i));
                tape=new ArrayList<>(tape.subList(leading.tapeIndex(),tape.size()));
            }
            total=state.tapeLength(tape);
        }
        while(sign>0 && q+movement+3.5>total || sign<0 && q-movement-tail<0) {
            Traversal leading=sign>0?tape.getLast():RailwayState.reverse(tape.getFirst());
            List<Traversal> next=state.graph().next(leading);
            if(next.isEmpty()) break;
            Traversal chosen;
            if(train.mode()==Mode.AUTOMATIC) {
                if(routeIndex>=authority.size()) break;
                chosen=authority.get(routeIndex++);
                if(!next.contains(chosen)) break;
            } else chosen=choose(state,leading,next,turn);
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
    public static Set<UUID> occupiedRails(RailwayState state,Train train) {
        if(train.head().isEmpty())return Set.of();Set<UUID> result=new HashSet<>();
        double high=state.headCoordinate(train)+3.5,low=high-7*train.cars().size(),q=0;
        for(Traversal step:train.tape()) {double end=q+state.railLength(step);if(Math.min(end,high)>Math.max(q,low))result.add(step.piece());q=end;}
        return Set.copyOf(result);
    }
    public static Pose bodyPoint(RailwayState state,Train train,int index,double longitudinal) {
        if(train.head().isPresent())return state.pose(train.tape(),state.headCoordinate(train)-7*index+longitudinal,false);
        Pose pose=state.lastPoses().get(train.cars().get(index));
        if(pose==null)throw new IllegalStateException("Последняя поза неизвестна");
        double direction=state.cars().get(train.cars().get(index)).reversed()?-1:1;
        return new Pose(new Vec(pose.position().x()+pose.tangent().x()*longitudinal*direction,pose.position().y(),pose.position().z()+pose.tangent().z()*longitudinal*direction),pose.tangent());
    }
    public static Map<UUID,Pose> carPoses(RailwayState state,UUID id) { return state.poses(Objects.requireNonNull(state.trains().get(id)),Set.of()); }
}
