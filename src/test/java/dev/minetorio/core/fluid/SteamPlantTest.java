package dev.minetorio.core.fluid;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.machine.Burner;
import dev.minetorio.core.power.PowerConsumer;
import dev.minetorio.core.power.PowerNetwork;
import org.junit.jupiter.api.Test;

class SteamPlantTest {
    @Test void flowEqualizes() {
        FluidBox a = new FluidBox(100), b = new FluidBox(100);
        a.insert("water", 100);
        for (int i = 0; i < 50; i++) FluidFlow.flow(a, b);
        assertTrue(Math.abs(a.amount() - b.amount()) < 1);
        assertEquals(100, a.amount() + b.amount(), 1e-6);
    }

    @Test void noMixing() {
        FluidBox water = new FluidBox(100), steam = new FluidBox(100);
        water.insert("water", 100);
        steam.insert("steam", 10);
        FluidFlow.flow(water, steam);
        assertEquals(100, water.amount());
        assertEquals(10, steam.amount());
        assertEquals(0, steam.insert("water", 5));
    }

    @Test void emptyingResetsFluid() {
        FluidBox a = new FluidBox(100);
        a.insert("water", 5);
        assertEquals(5, a.extract(50));
        assertNull(a.fluid());
    }

    @Test void boilerFeedsTwoEngines() {
        Burner burner = new Burner(Units.kw(1800), Phase1Data.fuelValues());
        burner.insertFuel("minetorio:coal", 50, false);
        SteamPlant.Boiler boiler = new SteamPlant.Boiler(burner);
        SteamPlant.Engine e1 = new SteamPlant.Engine(), e2 = new SteamPlant.Engine();
        PowerNetwork net = new PowerNetwork();
        net.add(e1);
        net.add(e2);
        net.add(new PowerConsumer() {
            @Override public long demand() { return 2 * Units.kw(900); }
            @Override public void supply(double s) {}
        });
        for (int t = 0; t < 220; t++) {
            boiler.water().insert("water", SteamPlant.OFFSHORE_WATER_PER_TICK);
            boiler.tick();
            FluidFlow.flow(boiler.steam(), e1.steam());
            FluidFlow.flow(e1.steam(), e2.steam());
            net.tick();
            // steam propagation is asymptotic: exact 1.0 is not reached with flow factor 0.4
            if (t >= 20) assertTrue(net.satisfaction() >= 0.99, "tick " + t);
            if (t >= 100) assertTrue(net.satisfaction() >= 0.9999, "tick " + t);
        }
    }

    @Test void engineWithoutSteamProducesNothing() {
        assertEquals(0, new SteamPlant.Engine().maxOutput());
    }

    @Test void boilerStopsWithoutFuel() {
        SteamPlant.Boiler boiler = new SteamPlant.Boiler(new Burner(Units.kw(1800), Phase1Data.fuelValues()));
        boiler.water().insert("water", 100);
        for (int i = 0; i < 10; i++) boiler.tick();
        assertEquals(0, boiler.steam().amount());
        assertEquals(100, boiler.water().amount());
    }

    private record FuelRun(int ticks, double bufferedJoules) {}

    private static FuelRun ticksCoalLasts(boolean withEngine) {
        Burner burner = new Burner(Units.kw(1800), Phase1Data.fuelValues());
        burner.insertFuel("minetorio:coal", 50, false); // large stock: the 200-unit steam buffer fills first
        SteamPlant.Boiler boiler = new SteamPlant.Boiler(burner);
        SteamPlant.Engine engine = new SteamPlant.Engine();
        for (int t = 1; t < 10000; t++) {
            boiler.water().insert("water", SteamPlant.OFFSHORE_WATER_PER_TICK);
            boiler.tick();
            if (withEngine) {
                FluidFlow.flow(boiler.steam(), engine.steam());
                engine.produce(Units.kw(900));
            } else {
                boiler.steam().extract(100);
            }
            if (burner.remainingJ() == 0 && burner.fuel().isEmpty()) return new FuelRun(t, (boiler.steam().amount()+engine.steam().amount())*SteamPlant.JOULES_PER_STEAM);
        }
        throw new AssertionError("fuel did not run out");
    }

    @Test void boilerBurnsLessAtPartialLoad() {
        FuelRun full = ticksCoalLasts(false);
        FuelRun half = ticksCoalLasts(true);
        assertEquals(200_000_000.0 / Units.kw(1800), full.ticks(), 1.0);
        // Startup fills two steam buffers: retained heat also consumes fuel.
        assertEquals(200_000_000.0, half.ticks()*Units.kw(900)+half.bufferedJoules(), Units.kw(900));
        assertTrue(half.ticks() > full.ticks(), "partial load must extend fuel life");
    }

    @Test void idleBoilerBurnsNothing() {
        Burner burner = new Burner(Units.kw(1800), Phase1Data.fuelValues());
        burner.insertFuel("minetorio:coal", 2, false);
        SteamPlant.Boiler boiler = new SteamPlant.Boiler(burner);
        for (int i = 0; i < 100; i++) boiler.tick();
        assertEquals(0, burner.remainingJ());
        assertEquals(2, burner.fuel().get("minetorio:coal"));
        boiler.steam().insert("steam", 200);
        boiler.water().insert("water", 100);
        for (int i = 0; i < 100; i++) boiler.tick();
        assertEquals(0, burner.remainingJ());
        assertEquals(2, burner.fuel().get("minetorio:coal"));
    }
}
