package dev.minetorio.core.rail;

import dev.minetorio.core.data.RailData;
import dev.minetorio.core.data.RailData.StockKind;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.fluid.FluidData;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.ToIntFunction;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Единственный владелец грузов и состояния составов в измерении. */
public final class RailwayState {
    public enum Mode { MANUAL, AUTOMATIC }
    // runtime-api 1.1.110, defines.train_state; NO_FUEL - состояние адаптации.
    public enum Status {
        ON_THE_PATH, PATH_LOST, NO_SCHEDULE, NO_PATH, ARRIVE_SIGNAL, WAIT_SIGNAL,
        ARRIVE_STATION, WAIT_STATION, MANUAL_CONTROL_STOP, MANUAL_CONTROL, DESTINATION_FULL, NO_FUEL
    }
    public record Fluid(String id, double amount, double capacity) {}
    public record Train(UUID id, List<UUID> cars, Optional<Cursor> head, int headIndex, List<Traversal> tape,
                        double speed, Mode mode, Status status, long routeRevision, long consistRevision, long motionRevision) {
        public Train {
            Objects.requireNonNull(id);
            cars = List.copyOf(cars);
            Objects.requireNonNull(head);
            tape = List.copyOf(tape);
            Objects.requireNonNull(mode);
            Objects.requireNonNull(status);
            if (routeRevision < 0 || consistRevision < 0 || motionRevision < 0) throw new IllegalArgumentException("Отрицательная ревизия");
            if (speed == 0) speed = 0;
            if (!Double.isFinite(speed)) throw new IllegalArgumentException("Недопустимая скорость");
        }
    }

    public final class Car {
        private final UUID id;
        private final StockKind kind;
        private boolean reversed;
        private double hp;
        private final RailSlots cargo;
        private final RailSlots fuel;
        private final FluidBox fluid;
        private String activeFuel = "";
        private BigDecimal remainingJ = BigDecimal.ZERO;

        private Car(UUID id, StockKind kind, boolean reversed) {
            this.id = id;
            this.kind = kind;
            this.reversed = reversed;
            var spec = RailData.stock(kind);
            hp = spec.health();
            cargo = kind == StockKind.CARGO ? new RailSlots(spec.slots(), keyLimit, changed) : null;
            fuel = kind == StockKind.LOCOMOTIVE ? new RailSlots(spec.slots(),
                    key -> RailData.fuel(RailSlots.baseId(key)).isPresent() ? keyLimit.applyAsInt(key) : 0, changed) : null;
            fluid = kind == StockKind.FLUID ? new FluidBox(spec.fluidCapacity()) : null;
        }
        public UUID id() { return id; }
        public StockKind kind() { return kind; }
        public boolean reversed() { return reversed; }
        public double hp() { return hp; }
        public Optional<RailSlots> cargo() { return Optional.ofNullable(cargo); }
        public Optional<RailSlots> fuel() { return Optional.ofNullable(fuel); }
        public Optional<Fluid> fluid() {
            return fluid == null ? Optional.empty() : Optional.of(new Fluid(fluid.fluid(), fluid.amount(), fluid.capacity()));
        }
        public String activeFuel() { return activeFuel; }
        public BigDecimal remainingJ() { return remainingJ; }
        public void setHp(double hp) {
            if (!Double.isFinite(hp) || hp < 0 || hp > RailData.stock(kind).health())
                throw new IllegalArgumentException("Недопустимое HP вагона " + id);
            if (this.hp != hp) { this.hp = hp; changed.run(); }
        }
        public void setActiveFuel(String id, BigDecimal remaining) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(remaining);
            if (kind != StockKind.LOCOMOTIVE || remaining.signum() < 0
                    || id.isEmpty() && remaining.signum() != 0
                    || !id.isEmpty() && (RailData.fuel(id).isEmpty() || keyLimit.applyAsInt(id) <= 0
                    || remaining.compareTo(BigDecimal.valueOf(RailData.fuel(id).orElseThrow().joules())) > 0))
                throw new IllegalArgumentException("Недопустимое активное топливо вагона " + this.id);
            if (!activeFuel.equals(id) || !remainingJ.equals(remaining)) {
                activeFuel = id;
                remainingJ = remaining;
                changed.run();
            }
        }
        public double insertFluid(String id, double amount) {
            if (fluid == null || !FluidData.isFluid(id)) return 0;
            double accepted = fluid.insert(id, amount);
            if (accepted > 0) changed.run();
            return accepted;
        }
        public double extractFluid(double amount) {
            if (fluid == null) return 0;
            double removed = fluid.extract(amount);
            if (removed > 0) changed.run();
            return removed;
        }
    }

    private final ToIntFunction<String> keyLimit;
    private final Runnable changed;
    private final Map<UUID, Car> cars = new LinkedHashMap<>();
    private final Map<UUID, Train> trains = new LinkedHashMap<>();
    private final RailGraph graph = new RailGraph(List.of());
    private List<Piece> rails = List.of();

    public RailwayState(ToIntFunction<String> keyLimit) { this(keyLimit, () -> {}); }
    public RailwayState(ToIntFunction<String> keyLimit, Runnable changed) {
        this.keyLimit = Objects.requireNonNull(keyLimit);
        this.changed = Objects.requireNonNull(changed);
    }
    public RailGraph graph() { return graph.snapshot(); }
    public List<Piece> rails() { return rails; }
    public Map<UUID, Car> cars() { return Collections.unmodifiableMap(cars); }
    public Map<UUID, Train> trains() { return Collections.unmodifiableMap(trains); }
    public Car createCar(UUID id, StockKind kind, boolean reversed) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        if (cars.containsKey(id) || trains.containsKey(id) || graph.piece(id).isPresent())
            throw new IllegalArgumentException("Повторный UUID вагона " + id);
        Car car = new Car(id, kind, reversed);
        cars.put(id, car);
        changed.run();
        return car;
    }
    public void replaceRails(Collection<Piece> pieces) {
        List<Piece> next = List.copyOf(pieces);
        for (Piece piece : next) {
            if (cars.containsKey(piece.id()) || trains.containsKey(piece.id()))
                throw new IllegalArgumentException("Повторный UUID рельса " + piece.id());
            Point p = piece.origin();
            if (Math.abs((long) p.x()) > 30_000_000 || Math.abs((long) p.z()) > 30_000_000 || p.y() < -2048 || p.y() > 2047)
                throw new IllegalArgumentException("Позиция рельса вне диапазона мира: " + p);
        }
        graph.rebuild(next);
        rails = next;
        trains.replaceAll((id, train) -> {
            Train nextTrain = normalizeRoute(train);
            return nextTrain.equals(train) ? train : revisions(nextTrain, train.routeRevision()+1, train.consistRevision(), train.motionRevision()+1);
        });
        changed.run();
    }
    /** Неверный сохраненный маршрут не уничтожает вагоны и груз. */
    public Train createTrain(Train initial) {
        UUID id = initial.id();
        if (trains.containsKey(id) || cars.containsKey(id) || graph.piece(id).isPresent())
            throw new IllegalArgumentException("Повторный UUID состава " + id);
        validateMembers(initial);
        for (Train existing : trains.values()) if (!Collections.disjoint(existing.cars(), initial.cars()))
            throw new IllegalArgumentException("Вагон уже принадлежит другому составу " + id);
        Train train = normalizeRoute(initial);
        trains.put(id, train);
        changed.run();
        return train;
    }
    public record MotionUpdate(Optional<Cursor> head, int headIndex, List<Traversal> tape, double speed, Status status) {
        public MotionUpdate { head = Objects.requireNonNull(head); tape = List.copyOf(tape); Objects.requireNonNull(status); }
    }
    public boolean commitMotion(Train expected, long graphRevision, MotionUpdate next) {
        if (!Objects.equals(trains.get(expected.id()), expected) || graph.revision() != graphRevision) return false;
        Train candidate = new Train(expected.id(), expected.cars(), next.head(), next.headIndex(), next.tape(),
                next.speed(), expected.mode(), next.status(), expected.routeRevision(), expected.consistRevision(), expected.motionRevision());
        if (!validLayout(candidate)) throw new IllegalArgumentException("Неверный layout движения");
        candidate = canonical(candidate);
        if (candidate.equals(expected)) return true;
        boolean routeChanged = Math.signum(expected.speed()) != Math.signum(candidate.speed()) || changedBranch(expected.tape(), candidate.tape());
        trains.put(candidate.id(), revisions(candidate, expected.routeRevision() + (routeChanged ? 1 : 0),
                expected.consistRevision(), expected.motionRevision() + 1));
        changed.run();
        return true;
    }
    private boolean changedBranch(List<Traversal> before, List<Traversal> after) {
        for (int i = 1; i < after.size(); i++) {
            Traversal a = after.get(i - 1), b = after.get(i);
            if (graph.next(a).size() <= 1 && graph.next(reverse(b)).size() <= 1) continue;
            boolean existed = false;
            for (int j = 1; j < before.size(); j++) if (before.get(j - 1).equals(a) && before.get(j).equals(b)) existed = true;
            if (!existed) return true;
        }
        return false;
    }
    boolean replaceConsists(List<Train> expected, long graphRevision, List<Train> replacements, Set<UUID> flipCars) {
        if (graph.revision() != graphRevision || expected.stream().anyMatch(t -> !Objects.equals(trains.get(t.id()), t))) return false;
        Set<UUID> oldIds = new HashSet<>(), oldCars = new HashSet<>(), newIds = new HashSet<>(), newCars = new HashSet<>();
        Map<UUID, Pose> oldPoses = new HashMap<>();
        for (Train t : expected) {
            if (!oldIds.add(t.id())) throw new IllegalArgumentException("Повторный состав");
            oldCars.addAll(t.cars()); oldPoses.putAll(poses(t, Set.of()));
        }
        List<Train> canonical = new ArrayList<>();
        for (Train t : replacements) {
            validateMembers(t);
            if (!newIds.add(t.id()) || trains.containsKey(t.id()) && !oldIds.contains(t.id())
                    || cars.containsKey(t.id()) || graph.piece(t.id()).isPresent() || !validLayout(t))
                throw new IllegalArgumentException("Неверная замена состава");
            for (UUID id : t.cars()) if (!newCars.add(id)) throw new IllegalArgumentException("Повторный вагон");
            canonical.add(canonical(t));
        }
        if (!oldCars.equals(newCars) || !oldCars.containsAll(flipCars)) throw new IllegalArgumentException("Потеря состава");
        for (Train t : canonical) for (var entry : poses(t, flipCars).entrySet()) {
            Pose old = oldPoses.get(entry.getKey()), next = entry.getValue();
            if (old == null || !near(old.position(), next.position()) || !near(old.tangent(), next.tangent()))
                throw new IllegalArgumentException("Замена перемещает кузов");
        }
        for (Train t : canonical) if (poses(t, flipCars).size() != t.cars().size()) throw new IllegalArgumentException("Состав не размещен");
        oldIds.forEach(trains::remove);
        for (Train t : canonical) {
            Train old = expected.stream().filter(e -> e.id().equals(t.id())).findFirst().orElse(null);
            trains.put(t.id(), revisions(t, old == null ? 1 : old.routeRevision() + 1,
                    old == null ? 1 : old.consistRevision() + 1, old == null ? 1 : old.motionRevision() + 1));
        }
        flipCars.forEach(id -> cars.get(id).reversed = !cars.get(id).reversed);
        changed.run();
        return true;
    }
    static boolean near(Vec a, Vec b) { return Math.abs(a.x()-b.x()) <= 1e-6 && Math.abs(a.y()-b.y()) <= 1e-6 && Math.abs(a.z()-b.z()) <= 1e-6; }
    private void validateMembers(Train t) {
        if (t.cars().isEmpty() || new HashSet<>(t.cars()).size() != t.cars().size() || !cars.keySet().containsAll(t.cars()))
            throw new IllegalArgumentException("Недопустимые вагоны состава " + t.id());
    }
    private Train normalizeRoute(Train t) {
        if (validLayout(t)) return canonical(t);
        return new Train(t.id(), t.cars(), Optional.empty(), -1, List.of(), 0, t.mode(), Status.PATH_LOST,
                t.routeRevision(), t.consistRevision(), t.motionRevision());
    }
    boolean validLayout(Train t) {
        if (t.head().isEmpty()) return t.tape().isEmpty() && t.headIndex() == -1 && t.speed() == 0;
        if (t.status() == Status.PATH_LOST) return false;
        if (t.headIndex() < 0 || t.headIndex() >= t.tape().size() || !t.tape().get(t.headIndex()).equals(t.head().get().traversal())) return false;
        for (int i = 0; i < t.tape().size(); i++) {
            Traversal step = t.tape().get(i);
            if (graph.piece(step.piece()).isEmpty() || i > 0 && !graph.next(t.tape().get(i-1)).contains(step)) return false;
        }
        if (t.head().get().distance() > railLength(t.head().get().traversal())) return false;
        double q = headCoordinate(t);
        return q - 7 * (t.cars().size()-1) - 3.5 >= -1e-9 && q + 3.5 <= tapeLength(t.tape()) + 1e-9;
    }
    static Train revisions(Train t, long route, long consist, long motion) {
        return new Train(t.id(), t.cars(), t.head(), t.headIndex(), t.tape(), t.speed(), t.mode(), t.status(), route, consist, motion);
    }
    private Train canonical(Train t) {
        if (t.head().isEmpty()) return t;
        Indexed position = locate(t.tape(), headCoordinate(t));
        return new Train(t.id(), t.cars(), Optional.of(position.cursor()), position.index(), t.tape(), t.speed(), t.mode(), t.status(),
                t.routeRevision(), t.consistRevision(), t.motionRevision());
    }
    record Indexed(Cursor cursor, int index) {}
    double railLength(Traversal t) { return length(graph.piece(t.piece()).orElseThrow()); }
    double tapeLength(List<Traversal> tape) { return tape.stream().mapToDouble(this::railLength).sum(); }
    double headCoordinate(Train t) {
        double q = t.head().orElseThrow().distance();
        for (int i=0; i<t.headIndex(); i++) q += railLength(t.tape().get(i));
        return q;
    }
    Indexed locate(List<Traversal> tape, double q) {
        if (!Double.isFinite(q) || q < -1e-9) throw new IllegalArgumentException("Координата вне ленты");
        q = Math.max(0, q);
        for (int i=0; i<tape.size(); i++) {
            double size = railLength(tape.get(i));
            if (q < size || i == tape.size()-1 && q <= size + 1e-9) return new Indexed(new Cursor(tape.get(i), Math.min(q,size)), i);
            q -= size;
        }
        throw new IllegalArgumentException("Координата вне ленты");
    }
    static Traversal reverse(Traversal t) { return new Traversal(t.piece(), !t.forward()); }
    Pose pose(List<Traversal> tape, double q, boolean reversed) {
        Cursor cursor = locate(tape,q).cursor();
        Piece piece = graph.piece(cursor.traversal().piece()).orElseThrow();
        Pose raw = sample(piece, cursor.traversal().forward() ? cursor.distance() : length(piece)-cursor.distance());
        boolean flip = !cursor.traversal().forward() ^ reversed;
        return new Pose(raw.position(), flip ? new Vec(-raw.tangent().x(), -raw.tangent().y(), -raw.tangent().z()) : raw.tangent());
    }
    Map<UUID,Pose> poses(Train t, Set<UUID> flips) {
        if (t.head().isEmpty()) return Map.of();
        Map<UUID,Pose> result = new LinkedHashMap<>();
        for (int i=0;i<t.cars().size();i++) {
            UUID id = t.cars().get(i);
            result.put(id,pose(t.tape(),headCoordinate(t)-7*i,cars.get(id).reversed() ^ flips.contains(id)));
        }
        return Collections.unmodifiableMap(result);
    }
}
