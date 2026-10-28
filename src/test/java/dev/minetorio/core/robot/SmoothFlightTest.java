package dev.minetorio.core.robot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmoothFlightTest {
    private static final double SPEED = RobotStats.SPEED_BLOCKS_PER_TICK;

    private static Flight.Vec v(double x, double y, double z) { return new Flight.Vec(x, y, z); }

    @Test
    void fliesAtExactSpeedAndStopsOnTarget() {
        SmoothFlight f = new SmoothFlight(v(0, 64, 0), 0);
        f.setTarget(v(10, 64, 0), SPEED);
        Flight.Vec last = f.pos();
        int ticks = 0;
        while (Flight.distance(f.pos(), v(10, 64, 0)) > 0 && ticks < 1000) {
            f.tick();
            double d = Flight.distance(last, f.pos());
            assertTrue(d <= SPEED + 1e-9, "step " + d);
            last = f.pos();
            ticks++;
        }
        assertEquals(Flight.ticks(10, SPEED), ticks);
        f.tick();
        assertEquals(v(10, 64, 0), f.pos());
    }

    /** Server flies a route; the client hears targets and position samples a few ticks late. */
    @Test
    void latencyNeverCausesJumpsAndEndsExactlyOnServerPosition() {
        List<Flight.Vec> route = List.of(v(5, 66, 0), v(5, 66, 9), v(-4, 67, 3), v(0, 64, 0));
        Flight.Vec server = v(0, 64, 0);
        int leg = 0;
        int latency = 4;
        ArrayDeque<Runnable> wire = new ArrayDeque<>();
        List<List<Runnable>> inFlight = new ArrayList<>();
        SmoothFlight client = new SmoothFlight(server, 0);
        Flight.Vec last = client.pos();
        for (int tick = 0; tick < 600; tick++) {
            // server tick
            List<Runnable> sent = new ArrayList<>();
            if (leg < route.size()) {
                Flight.Vec t = route.get(leg);
                server = Flight.step(server, t, SPEED);
                Flight.Vec s = server;
                if (tick % 10 == 0) sent.add(() -> client.sample(s));
                sent.add(() -> client.setTarget(t, SPEED));
                if (server.equals(t)) leg++;
            }
            inFlight.add(sent);
            if (inFlight.size() > latency) inFlight.remove(0).forEach(wire::add);
            while (!wire.isEmpty()) wire.poll().run();
            // client tick
            client.tick();
            double d = Flight.distance(last, client.pos());
            assertTrue(d <= SPEED + 1e-9, "tick " + tick + " jumped " + d);
            last = client.pos();
        }
        assertEquals(server, client.pos());
    }

    @Test
    void realDriftIsBlendedAwayWithoutJumps() {
        SmoothFlight f = new SmoothFlight(v(3, 64, 0), 0); // 3 blocks off
        f.setTarget(v(0, 64, 20), SPEED);
        f.sample(v(0, 64, 0));
        Flight.Vec server = v(0, 64, 0);
        Flight.Vec last = f.pos();
        for (int i = 0; i < 40; i++) {
            f.tick();
            server = Flight.step(server, v(0, 64, 20), SPEED);
            assertTrue(Flight.distance(last, f.pos()) <= SPEED + 3 * SmoothFlight.BLEND_RATE + 1e-9);
            last = f.pos();
        }
        assertTrue(Flight.distance(server, f.pos()) < 0.15, "converged: " + f.pos());
        for (int i = 0; i < 200; i++) f.tick();
        assertEquals(v(0, 64, 20), f.pos());
    }

    @Test
    void farOffSnapsToServer() {
        SmoothFlight f = new SmoothFlight(v(0, 64, 0), 0);
        f.sample(v(20, 64, 0));
        f.tick();
        assertEquals(v(20, 64, 0), f.pos());
    }

    @Test
    void hoveringRobotIsNudgedOntoTheServerPosition() {
        SmoothFlight f = new SmoothFlight(v(0, 64, 0), 0);
        f.setTarget(v(0, 64, 0), 0);
        f.sample(v(0.3, 64, 0));
        for (int i = 0; i < 60; i++) f.tick();
        assertEquals(0.3, f.pos().x(), 1e-6);
    }

    @Test
    void smallTimeShiftInFlightIsLeftAlone() {
        SmoothFlight f = new SmoothFlight(v(0, 64, 0), 0);
        f.setTarget(v(0, 64, 20), SPEED);
        f.sample(v(0, 64, 3 * SPEED)); // server a few ticks ahead on the same path
        f.tick();
        assertEquals(v(0, 64, SPEED), f.pos());
    }

    @Test
    void turnsGraduallyAcrossTheWrap() {
        assertEquals(175f, SmoothFlight.turnToward(170f, -170f, 0.25f, 30f), 1e-4); // 20 deg gap the short way
        assertEquals(-175f, SmoothFlight.turnToward(-170f, 170f, 0.25f, 30f), 1e-4);
        assertEquals(30f, SmoothFlight.turnToward(0f, 90f, 0.5f, 30f), 1e-4); // capped
        assertEquals(90f, SmoothFlight.turnToward(89.98f, 90f, 0.3f, 30f), 1e-4); // settles exactly
        assertEquals(-180f, SmoothFlight.wrapDegrees(180f));
        assertEquals(10f, SmoothFlight.wrapDegrees(370f));
    }

    @Test
    void headsTowardTravelAndBanksIntoTurns() {
        SmoothFlight f = new SmoothFlight(v(0, 64, 0), 0); // facing +Z (south)
        f.setTarget(v(-20, 64, 0), SPEED); // west: yaw 90, a right turn
        f.tick();
        assertTrue(f.yaw() > 0 && f.yaw() < 90, "turning, not snapping: " + f.yaw());
        assertTrue(f.bank() > 0, "banks right");
        for (int i = 0; i < 60; i++) f.tick();
        assertEquals(90f, f.yaw(), 1e-3);
        assertEquals(0f, f.bank(), 1e-2);
    }
}
