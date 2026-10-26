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
}
