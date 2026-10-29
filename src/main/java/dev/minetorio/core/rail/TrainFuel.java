package dev.minetorio.core.rail;

import dev.minetorio.core.data.RailData;
import dev.minetorio.core.data.RailData.StockKind;
import java.math.BigDecimal;
import java.util.*;

/** Порции химического топлива S4 (1.1.110); preview не меняет владельца. */
public final class TrainFuel {
    private static final BigDecimal MAX_REQUEST = new BigDecimal("10000");
    private TrainFuel() {}
    public record Portion(String fuel, BigDecimal joules, double acceleration, double topSpeed) {}
    public record Draw(BigDecimal deliveredJ, List<Portion> portions) {
        public Draw { portions = List.copyOf(portions); }
    }
    public static Draw draw(RailwayState.Car car, BigDecimal requestedJ) { return consume(car, requestedJ, false); }
    public static Draw preview(RailwayState.Car car, BigDecimal requestedJ) { return consume(car, requestedJ, true); }
    public static boolean available(RailwayState.Car car) {
        return car.kind() == StockKind.LOCOMOTIVE && (car.remainingJ().signum() > 0
                || car.fuel().orElseThrow().snapshot().stream().anyMatch(s -> s.count() > 0));
    }
    public static double activeTopSpeed(RailwayState.Car car) {
        return RailData.fuel(car.activeFuel()).map(RailData.FuelSpec::topSpeed).orElse(1.0);
    }
    private static Draw consume(RailwayState.Car car, BigDecimal request, boolean simulate) {
        Objects.requireNonNull(car); Objects.requireNonNull(request);
        if (request.signum() < 0 || request.compareTo(MAX_REQUEST) > 0 || car.kind() != StockKind.LOCOMOTIVE)
            throw new IllegalArgumentException("Недопустимый запрос топлива");
        if (request.signum() == 0) return new Draw(BigDecimal.ZERO, List.of());
        var slots = car.fuel().orElseThrow();
        int[] counts = slots.snapshot().stream().mapToInt(RailSlots.Slot::count).toArray();
        String active = car.activeFuel();
        BigDecimal remaining = car.remainingJ(), needed = request;
        List<Portion> portions = new ArrayList<>();
        while (needed.signum() > 0) {
            if (remaining.signum() == 0) {
                int index = 0;
                while (index < counts.length && counts[index] == 0) index++;
                if (index == counts.length) break;
                active = RailSlots.baseId(slots.get(index).key());
                remaining = BigDecimal.valueOf(RailData.fuel(active).orElseThrow().joules());
                counts[index]--;
                if (!simulate) slots.extract(index, 1, false);
            }
            var spec = RailData.fuel(active).orElseThrow();
            BigDecimal amount = needed.min(remaining);
            portions.add(new Portion(active, amount, spec.acceleration(), spec.topSpeed()));
            needed = needed.subtract(amount); remaining = remaining.subtract(amount);
            if (!simulate) car.setActiveFuel(active, remaining);
        }
        return new Draw(request.subtract(needed), portions);
    }
}
