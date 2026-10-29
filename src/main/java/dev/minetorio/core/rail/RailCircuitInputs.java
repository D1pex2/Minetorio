package dev.minetorio.core.rail;

import java.util.*;
import dev.minetorio.core.rail.TrainSchedule.SignalKey;

/** Порт измерения. Отсутствие связи отличается от подключенного нулевого сигнала. */
public interface RailCircuitInputs {
    Optional<Map<SignalKey,Integer>> forTrain(UUID train, Optional<UUID> station);
    Optional<Boolean> stationEnabled(UUID station);
    OptionalInt stationLimit(UUID station);
    boolean signalClosed(UUID signal);
    RailCircuitInputs NONE = new RailCircuitInputs() {
        public Optional<Map<SignalKey,Integer>> forTrain(UUID train, Optional<UUID> station) { return Optional.empty(); }
        public Optional<Boolean> stationEnabled(UUID station) { return Optional.empty(); }
        public OptionalInt stationLimit(UUID station) { return OptionalInt.empty(); }
        public boolean signalClosed(UUID signal) { return false; }
    };
}
