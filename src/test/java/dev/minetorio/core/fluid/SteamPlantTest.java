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
        burner.insertFuel("minecraft:coal", 64, false);
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
}
