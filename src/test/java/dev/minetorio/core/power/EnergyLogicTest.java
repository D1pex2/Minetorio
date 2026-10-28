package dev.minetorio.core.power;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.Units;
import org.junit.jupiter.api.Test;

/** Solar curve, accumulator math and the network's generator → storage priority. */
class EnergyLogicTest {
    static final class Load implements PowerConsumer {
        final long demand;
        double got = -1;
        Load(long demand) { this.demand = demand; }
        public long demand() { return demand; }
        public void supply(double s) { got = s; }
    }

    static final class Gen implements PowerProducer {
        final long max;
        final Priority priority;
        long produced = -1;
        Gen(long max, Priority priority) { this.max = max; this.priority = priority; }
        public long maxOutput() { return max; }
        public void produce(long j) { produced = j; }
        public Priority priority() { return priority; }
    }

    // --- solar ------------------------------------------------------------------------------------

    @Test void solarPeakIs60kW() {
        assertEquals(3000, SolarLogic.PEAK);
        assertEquals(SolarLogic.PEAK, SolarLogic.output(0, 0, 0, true)); // noon
    }

    @Test void solarIsZeroAtNightAndCovered() {
        assertEquals(0, SolarLogic.output(0.5, 0, 0, true)); // midnight
        assertEquals(0, SolarLogic.output(0, 0, 0, false));
    }

    @Test void solarRampIsSmoothAndMonotonic() {
        // celestial angle 0 = noon .. 0.5 = midnight: output never rises and never jumps by more than a sliver
        long prev = SolarLogic.output(0, 0, 0, true);
        boolean sawPartial = false;
        for (int i = 1; i <= 500; i++) {
            long now = SolarLogic.output(i / 1000.0, 0, 0, true);
            assertTrue(now <= prev, "rises at " + i);
            assertTrue(prev - now <= SolarLogic.PEAK / 20, "jumps at " + i);
            if (now > 0 && now < SolarLogic.PEAK) sawPartial = true;
            prev = now;
        }
        assertTrue(sawPartial, "no sunset ramp");
        assertEquals(0.5, SolarLogic.daylight(Math.acos(0) / (2 * Math.PI)), 1e-9); // cos = 0: the middle of the ramp
        assertEquals(SolarLogic.daylight(0.2), SolarLogic.daylight(0.8), 1e-9); // dawn mirrors dusk
    }

    @Test void rainLowersSolarABit() {
        long clear = SolarLogic.output(0, 0, 0, true);
        long rain = SolarLogic.output(0, 1, 0, true);
        long storm = SolarLogic.output(0, 1, 1, true);
        assertEquals(Math.round(clear * 0.7), rain);
        assertTrue(storm < rain && storm > 0);
    }

    // --- accumulator ------------------------------------------------------------------------------

    @Test void accumulatorIs5MJAt300kW() {
        assertEquals(5_000_000, Accumulator.CAPACITY);
        assertEquals(15_000, Accumulator.RATE);
        Accumulator a = new Accumulator();
        assertEquals(Accumulator.RATE, a.maxCharge());
        assertEquals(0, a.maxDischarge());
        a.charge(1_000_000); // capped at the rate
        assertEquals(Accumulator.RATE, a.energy());
        a.setEnergy(Accumulator.CAPACITY - 10);
        assertEquals(10, a.maxCharge());
        a.charge(100);
        assertEquals(Accumulator.CAPACITY, a.energy());
        a.discharge(Accumulator.RATE * 2);
        assertEquals(Accumulator.CAPACITY - Accumulator.RATE, a.energy());
    }

    @Test void accumulatorFillsIn333Ticks() {
        Accumulator a = new Accumulator();
        int ticks = 0;
        while (a.energy() < Accumulator.CAPACITY) {
            a.charge(a.maxCharge());
            ticks++;
        }
        assertEquals(334, ticks); // 5 MJ / 15 kJ per tick = 333.3
    }

    @Test void flowAndLamps() {
        Accumulator a = new Accumulator();
        a.charge(100);
        a.endTick();
        assertEquals(Accumulator.Flow.CHARGING, a.flow());
        a.discharge(40);
        a.endTick();
        assertEquals(Accumulator.Flow.DISCHARGING, a.flow());
        a.endTick();
        assertEquals(Accumulator.Flow.IDLE, a.flow());
        assertEquals(0, Accumulator.litLamps(0, 5));
        assertEquals(1, Accumulator.litLamps(1, 5));
        assertEquals(3, Accumulator.litLamps(Accumulator.CAPACITY / 2, 5));
        assertEquals(5, Accumulator.litLamps(Accumulator.CAPACITY, 5));
    }

    // --- grid priority ----------------------------------------------------------------------------

    @Test void surplusChargesAccumulators() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(1000);
        Gen g = new Gen(Units.kw(900), PowerProducer.Priority.SECONDARY); // 45 kJ/tick
        Accumulator a = new Accumulator();
        n.add(l); n.add(g); n.add(a);
        n.tick();
        assertEquals(1.0, l.got);
        assertEquals(1000 + Accumulator.RATE, g.produced, "engine also fills the accumulator");
        assertEquals(Accumulator.RATE, a.energy());
        assertEquals(Accumulator.RATE, n.lastCharged());
    }

    @Test void deficitDrainsAccumulators() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(20_000);
        Gen g = new Gen(10_000, PowerProducer.Priority.PRIMARY);
        Accumulator a = new Accumulator();
        a.setEnergy(Accumulator.CAPACITY);
        n.add(l); n.add(g); n.add(a);
        n.tick();
        assertEquals(10_000, g.produced);
        assertEquals(1.0, l.got, 1e-9);
        assertEquals(Accumulator.CAPACITY - 10_000, a.energy());
        assertEquals(10_000, n.lastDischarged());
    }

    @Test void accumulatorsAloneCarryTheNightUpToTheirRate() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(30_000);
        Gen sun = new Gen(0, PowerProducer.Priority.PRIMARY);
        Accumulator a = new Accumulator();
        a.setEnergy(Accumulator.CAPACITY);
        n.add(l); n.add(sun); n.add(a);
        n.tick();
        assertEquals(0.5, l.got, 1e-9); // 15 kJ of 30 kJ
        assertEquals(0, sun.produced);
    }

    @Test void solarIsUsedBeforeSteam() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(5000);
        Gen solar = new Gen(3000, PowerProducer.Priority.PRIMARY);
        Gen steam = new Gen(45_000, PowerProducer.Priority.SECONDARY);
        n.add(l); n.add(steam); n.add(solar);
        n.tick();
        assertEquals(3000, solar.produced);
        assertEquals(2000, steam.produced);
        assertEquals(1.0, l.got);
    }

    @Test void accumulatorsDoNotFeedEachOther() {
        PowerNetwork n = new PowerNetwork();
        Accumulator full = new Accumulator(), empty = new Accumulator();
        full.setEnergy(Accumulator.CAPACITY);
        n.add(full); n.add(empty);
        n.tick();
        assertEquals(Accumulator.CAPACITY, full.energy());
        assertEquals(0, empty.energy());
    }

    @Test void chargeIsSplitByRoom() {
        PowerNetwork n = new PowerNetwork();
        Gen g = new Gen(20_001, PowerProducer.Priority.PRIMARY);
        Accumulator a = new Accumulator(), b = new Accumulator();
        n.add(g); n.add(a); n.add(b);
        n.tick();
        assertEquals(20_001, a.energy() + b.energy(), "every joule produced is stored");
        assertTrue(Math.abs(a.energy() - b.energy()) <= 1);
    }

    @Test void splitIsExact() {
        long[] out = PowerNetwork.split(10, new long[] {3, 3, 3, 100});
        long s = 0;
        for (long v : out) s += v;
        assertEquals(10, s);
        for (int i = 0; i < 3; i++) assertTrue(out[i] <= 3);
    }
}
