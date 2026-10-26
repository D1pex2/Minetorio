package dev.minetorio.core.fluid;

import dev.minetorio.core.Units;
import dev.minetorio.core.machine.Burner;
import dev.minetorio.core.power.PowerProducer;

public final class SteamPlant {
    public static final double OFFSHORE_WATER_PER_TICK = 60.0;
    public static final double WATER_PER_FULL_BOILER_TICK = 3.0;
    public static final double JOULES_PER_STEAM = 30_000.0;
    public static final double PIPE_CAPACITY = 100;
    public static final double PUMP_CAPACITY = 100;
    public static final double BOILER_CAPACITY = 200;
    public static final double ENGINE_CAPACITY = 200;
    public static final long BOILER_POWER_J = Units.kw(1800);
    public static final long ENGINE_MAX_OUTPUT_J = Units.kw(900);

    private SteamPlant() {}

    public static final class Boiler {
        private final Burner burner;
        private final FluidBox water = new FluidBox(BOILER_CAPACITY);
        private final FluidBox steam = new FluidBox(BOILER_CAPACITY);

        public Boiler(Burner burner) { this.burner = burner; }

        public FluidBox water() { return water; }

        public FluidBox steam() { return steam; }

        public void tick() {
            double steamFree = steam.capacity() - steam.amount();
            if (water.amount() <= 0 || steamFree <= 0) return;
            double f = burner.draw();
            double c = Math.min(WATER_PER_FULL_BOILER_TICK * f, Math.min(water.amount(), steamFree));
            if (c <= 0) return;
            steam.insert("steam", water.extract(c));
        }
    }

    public static final class Engine implements PowerProducer {
        private final FluidBox steam = new FluidBox(ENGINE_CAPACITY);

        public FluidBox steam() { return steam; }

        @Override public long maxOutput() {
            return Math.min(ENGINE_MAX_OUTPUT_J, (long) (steam.amount() * JOULES_PER_STEAM));
        }

        @Override public void produce(long joules) { steam.extract(joules / JOULES_PER_STEAM); }
    }
}
