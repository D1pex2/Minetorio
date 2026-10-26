package dev.minetorio.core.machine;

import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BurnerTest {
    private Burner burner() { return new Burner(Units.kw(90), Phase1Data.fuelValues()); }

    @Test void burnerCoalLasts() {
        Burner b = burner();
        assertEquals(1, b.insertFuel("minecraft:coal", 1, false));
        for (int i = 1; i <= 888; i++) assertEquals(1.0, b.draw(), "tick " + i);
        double last = b.draw();
        assertTrue(last > 0.0 && last < 1.0, "tick 889 was " + last);
        assertEquals(0.0, b.draw());
        assertEquals(0L, b.remainingJ());
    }

    @Test void burnerNoFuelZero() { assertEquals(0.0, burner().draw()); }

    @Test void automatedFuelCapsAtFive() {
        Burner b = burner();
        assertEquals(5, b.insertFuel("minecraft:coal", 10, true));
        assertEquals(0, b.insertFuel("minecraft:coal", 1, true));
        assertEquals(5, b.fuel().get("minecraft:coal"));
    }

    @Test void rejectsNonFuel() { assertEquals(0, burner().insertFuel("minecraft:stone", 3, false)); }

    @Test void partialDrawBurnsOnlyRequested() {
        long power = Units.kw(1800);
        Burner full = new Burner(power, Phase1Data.fuelValues());
        Burner half = new Burner(power, Phase1Data.fuelValues());
        full.insertFuel("minecraft:coal", 1, false);
        half.insertFuel("minecraft:coal", 1, false);
        int fullTicks = 0, halfTicks = 0;
        while (full.draw() >= 1.0) fullTicks++;
        while (half.draw(power / 2) >= 1.0) halfTicks++;
        assertTrue(halfTicks >= 2 * fullTicks, halfTicks + " vs " + fullTicks);
        assertEquals(0.0, half.draw(0));
    }

    @Test void loadRestoresFuelAndBurningEnergy() {
        Burner b = burner();
        b.insertFuel("minecraft:coal", 3, false);
        for (int i = 0; i < 10; i++) b.draw();
        Burner copy = burner();
        copy.load(b.fuel(), b.remainingJ());
        assertEquals(b.fuel(), copy.fuel());
        assertEquals(b.remainingJ(), copy.remainingJ());
        for (int i = 0; i < 2000; i++) assertEquals(b.draw(), copy.draw(), "tick " + i);
    }

    @Test void loadSkipsUnknownFuelAndBadCounts() {
        Burner b = burner();
        b.load(java.util.Map.of("minecraft:stone", 3, "minecraft:coal", 0), -5);
        assertTrue(b.fuel().isEmpty());
        assertEquals(0L, b.remainingJ());
    }

    @Test void fuelRoomAndExtract() {
        Burner b = burner();
        assertEquals(5, b.fuelRoom("minecraft:coal", true));
        assertEquals(64, b.fuelRoom("minecraft:coal", false));
        assertEquals(0, b.fuelRoom("minecraft:stone", false));
        b.insertFuel("minecraft:coal", 4, false);
        assertEquals(1, b.fuelRoom("minecraft:coal", true));
        assertEquals(60, b.fuelRoom("minecraft:coal", false));
        assertEquals(3, b.extractFuel("minecraft:coal", 3));
        assertEquals(1, b.extractFuel("minecraft:coal", 5));
        assertTrue(b.fuel().isEmpty());
        assertEquals(0, b.extractFuel("minecraft:coal", 1));
    }
}
