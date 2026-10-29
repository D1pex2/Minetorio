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
    public record Train(UUID id, List<UUID> cars, Optional<Cursor> head, List<Traversal> tape,
                        double speed, Mode mode, Status status) {
        public Train {
            Objects.requireNonNull(id);
            cars = List.copyOf(cars);
            Objects.requireNonNull(head);
            tape = List.copyOf(tape);
            Objects.requireNonNull(mode);
            Objects.requireNonNull(status);
            if (!Double.isFinite(speed) || speed < 0) throw new IllegalArgumentException("Недопустимая скорость");
        }
    }

    public final class Car {
        private final UUID id;
        private final StockKind kind;
        private final boolean reversed;
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
                    || remaining.signum() == 0 && !id.isEmpty()
                    || remaining.signum() > 0 && (RailData.fuel(id).isEmpty() || keyLimit.applyAsInt(id) <= 0
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
        trains.replaceAll((id, train) -> normalizeRoute(train));
        changed.run();
    }
    /** Тот же вход используется загрузчиком; устаревший путь останавливает состав. */
    public Train createTrain(UUID id, List<UUID> carIds, Optional<Cursor> head, List<Traversal> tape,
                             double speed, Mode mode, Status status) {
        if (trains.containsKey(id) || cars.containsKey(id) || graph.piece(id).isPresent())
            throw new IllegalArgumentException("Повторный UUID состава " + id);
        if (carIds.isEmpty() || new HashSet<>(carIds).size() != carIds.size() || !cars.keySet().containsAll(carIds))
            throw new IllegalArgumentException("Недопустимые вагоны состава " + id);
        for (Train existing : trains.values()) if (!Collections.disjoint(existing.cars(), carIds))
            throw new IllegalArgumentException("Вагон уже принадлежит другому составу " + id);
        Train train = normalizeRoute(new Train(id, carIds, head, tape, speed, mode, status));
        trains.put(id, train);
        changed.run();
        return train;
    }
    private Train normalizeRoute(Train train) {
        boolean valid = train.head().isPresent() && !train.tape().isEmpty();
        if (valid) {
            Cursor head = train.head().orElseThrow();
            valid = train.tape().contains(head.traversal()) && graph.piece(head.traversal().piece())
                    .map(piece -> head.distance() <= length(piece)).orElse(false);
            for (int i = 0; valid && i < train.tape().size(); i++) {
                Traversal step = train.tape().get(i);
                valid = graph.piece(step.piece()).isPresent()
                        && (i == 0 || graph.next(train.tape().get(i - 1)).contains(step));
            }
        }
        if (valid || train.head().isEmpty() && train.tape().isEmpty() && train.speed() == 0) return train;
        return new Train(train.id(), train.cars(), Optional.empty(), List.of(), 0, train.mode(), Status.PATH_LOST);
    }
}
