package dev.minetorio.core.power;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.Units;
import org.junit.jupiter.api.Test;

class PowerNetworkTest {
    static final class Load implements PowerConsumer {
        final long demand;
        double got = -1;
        Load(long demand) { this.demand = demand; }
        public long demand() { return demand; }
        public void supply(double s) { got = s; }
    }

    static final class Gen implements PowerProducer {
        final long max;
        long produced = -1;
        Gen(long max) { this.max = max; }
        public long maxOutput() { return max; }
        public void produce(long j) { produced = j; }
    }

    @Test void enoughPowerFullSatisfaction() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(100);
        Gen g = new Gen(200);
        n.add(l); n.add(g);
        n.tick();
        assertEquals(1.0, n.satisfaction());
        assertEquals(1.0, l.got);
        assertEquals(100, g.produced);
    }

    @Test void halfPower() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(100);
        Gen g = new Gen(50);
        n.add(l); n.add(g);
        n.tick();
        assertEquals(0.5, n.satisfaction(), 1e-9);
        assertEquals(0.5, l.got, 1e-9);
        assertEquals(50, g.produced);
    }

    @Test void noProducersZero() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(100);
        n.add(l);
        n.tick();
        assertEquals(0.0, n.satisfaction());
        assertEquals(0.0, l.got);
    }

    @Test void noConsumersOne() {
        PowerNetwork n = new PowerNetwork();
        Gen g = new Gen(100);
        n.add(g);
        n.tick();
        assertEquals(1.0, n.satisfaction());
        assertEquals(0, g.produced);
    }

    @Test void producersShareLoad() {
        PowerNetwork n = new PowerNetwork();
        long engine = Units.kw(900);
        Gen a = new Gen(engine), b = new Gen(engine);
        n.add(new Load(45000)); n.add(a); n.add(b);
        n.tick();
        assertEquals(22500, a.produced);
        assertEquals(22500, b.produced);
    }

    @Test void removeStopsSupplying() {
        PowerNetwork n = new PowerNetwork();
        Load l = new Load(100);
        Gen g = new Gen(100);
        n.add(l); n.add(g);
        n.remove(g);
        n.tick();
        assertEquals(0.0, l.got);
    }
}
