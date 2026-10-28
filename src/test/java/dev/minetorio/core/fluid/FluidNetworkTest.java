package dev.minetorio.core.fluid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FluidNetworkTest {
    private static final double PIPE = FluidData.PIPE_CAPACITY;

    /** A straight line of {@code n} pipes with keys from..from+n-1, linked in order. */
    private static void line(FluidNetwork net, long from, int n) {
        for (int i = 0; i < n; i++) {
            net.addNode(from + i, PIPE, null, 0);
            if (i > 0) net.link(from + i - 1, from + i);
        }
    }

    private static FluidNetwork.Port port(FluidBox box, PortKind kind) {
        return FluidNetwork.Port.of(box, kind, null);
    }

    // --- segments -------------------------------------------------------------------------------------

    @Test
    void linkedPipesFormOneSegmentWithSummedCapacityAndContents() {
        FluidNetwork net = new FluidNetwork();
        net.addNode(1, PIPE, "water", 40);
        net.addNode(2, PIPE, "water", 60);
        net.addNode(3, PIPE, null, 0);
        net.link(1, 2);
        net.link(2, 3);
        FluidNetwork.Segment s = net.segment(1);
        assertSame(s, net.segment(3));
        assertEquals(3, s.length());
        assertEquals(300, s.capacity(), 1e-9);
        assertEquals(100, s.amount(), 1e-9);
        assertEquals("water", s.fluid());
        assertEquals(100.0 / 3, net.share(2), 1e-9, "equal fill level per node");
        assertEquals(1, net.segments().size());
    }

    @Test
    void breakingAPipeSplitsTheSegmentAndSharesByCapacity() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 5); // 0-1-2-3-4
        net.segment(0).insert("water", 500);
        double lost = net.removeNode(2);
        assertEquals(100, lost, 1e-9, "the broken pipe's share is gone");
        assertNotSame(net.segment(0), net.segment(4));
        assertEquals(200, net.segment(0).amount(), 1e-9);
        assertEquals(200, net.segment(4).amount(), 1e-9);
        assertEquals("water", net.segment(4).fluid());
        assertEquals(2, net.segments().size());
        assertEquals(2, net.segment(3).length());
    }

    @Test
    void removingAnEndPipeKeepsOneSegment() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 4);
        int id = net.segment(0).id();
        net.removeNode(3);
        assertEquals(1, net.segments().size());
        assertEquals(id, net.segment(0).id());
        assertEquals(3, net.segment(2).length());
    }

    @Test
    void sideToggleUnlinksAndRelinks() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 4);
        net.segment(0).insert("water", 200);
        net.unlink(1, 2); // side of pipe 1 toward 2 closed
        assertNotSame(net.segment(1), net.segment(2));
        assertEquals(100, net.segment(0).amount(), 1e-9);
        assertEquals(100, net.segment(3).amount(), 1e-9);
        net.setLinks(1, Set.of(0L, 2L)); // reopened
        assertSame(net.segment(0), net.segment(3));
        assertEquals(200, net.segment(0).amount(), 1e-9);
    }

    @Test
    void loopStaysOneSegmentWhenOneLinkIsCut() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 4);
        net.link(3, 0); // ring
        net.unlink(1, 2);
        assertSame(net.segment(1), net.segment(2), "still connected the other way round");
        assertEquals(1, net.segments().size());
    }

    @Test
    void fluidsOfReportsAMixBeforeLinking() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 2);
        line(net, 10, 2);
        net.segment(0).insert("water", 50);
        net.segment(10).insert("steam", 50);
        net.addNode(5, PIPE, null, 0); // a pipe that would join both lines
        assertEquals(Set.of("water", "steam"), net.fluidsOf(List.of(1L, 10L)), "placing it would mix: refused by the caller");
        assertEquals(Set.of("water"), net.fluidsOf(List.of(1L, 5L)));
        net.link(5, 1);
        assertEquals("water", net.segment(5).fluid());
    }

    @Test
    void forcedMixKeepsTheLargerFluid() {
        FluidNetwork net = new FluidNetwork();
        net.addNode(1, PIPE, "water", 80);
        net.addNode(2, PIPE, "steam", 30);
        net.link(1, 2);
        assertEquals("water", net.segment(1).fluid());
        assertEquals(80, net.segment(1).amount(), 1e-9);
    }

    @Test
    void segmentRefusesAnotherFluid() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 3);
        FluidNetwork.Segment s = net.segment(0);
        assertEquals(100, s.insert("water", 100), 1e-9);
        assertEquals(0, s.insert("steam", 10), 1e-9);
        s.extract(100);
        assertNull(s.fluid());
        assertEquals(10, s.insert("steam", 10), 1e-9);
    }

    // --- flow -----------------------------------------------------------------------------------------

    @Test
    void inputsGetEqualShares() {
        FluidNetwork net = new FluidNetwork(len -> 1e9, 1e9);
        line(net, 0, 3);
        net.segment(0).insert("water", 90);
        FluidBox x = new FluidBox(100), y = new FluidBox(100), z = new FluidBox(10);
        net.setAttachments(List.of(new FluidNetwork.Attachment(0, port(x, PortKind.INPUT)),
                new FluidNetwork.Attachment(2, port(y, PortKind.INPUT)),
                new FluidNetwork.Attachment(1, port(z, PortKind.INPUT))));
        net.tick();
        assertEquals(10, z.amount(), 1e-9, "the small one is full");
        assertEquals(40, x.amount(), 1e-9, "the rest is shared evenly");
        assertEquals(40, y.amount(), 1e-9);
        assertEquals(0, net.segment(0).amount(), 1e-9);
        assertNull(net.segment(0).fluid());
    }

    @Test
    void consumerAtTheEndOfALongLineIsFed() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 100);
        FluidBox source = new FluidBox(100), sink = new FluidBox(200);
        net.setAttachments(List.of(new FluidNetwork.Attachment(0, port(source, PortKind.OUTPUT)),
                new FluidNetwork.Attachment(99, port(sink, PortKind.INPUT))));
        for (int t = 0; t < 5; t++) {
            source.insert("water", 60);
            net.tick();
            sink.extract(3);
        }
        assertTrue(sink.amount() > 0, "nothing arrived at the end of the line");
    }

    @Test
    void throughputDropsWithLength() {
        assertEquals(6000, FluidData.segmentLimitPerSecond(1), 1e-9);
        assertEquals(1000, FluidData.segmentLimitPerSecond(261), 1e-9);
        double prev = Double.MAX_VALUE;
        for (int len = 1; len <= 1200; len++) {
            double v = FluidData.segmentLimitPerSecond(len);
            assertTrue(v <= prev + 1e-9, "not monotone at " + len);
            prev = v;
        }
        assertEquals(FluidData.segmentLimitPerSecond(400) / 20, FluidData.segmentLimitPerTick(400), 1e-9);
    }

    @Test
    void segmentLimitCapsFlowOfALongLine() {
        int len = 200;
        FluidNetwork net = new FluidNetwork(FluidData::segmentLimitPerTick, 1e9);
        line(net, 0, len);
        net.segment(0).insert("water", 10_000);
        FluidBox sink = new FluidBox(10_000);
        net.setAttachments(List.of(new FluidNetwork.Attachment(len - 1, port(sink, PortKind.INPUT))));
        net.tick();
        assertEquals(FluidData.segmentLimitPerTick(len), sink.amount(), 1e-9);
        FluidNetwork shortNet = new FluidNetwork(FluidData::segmentLimitPerTick, 1e9);
        line(shortNet, 0, 2);
        shortNet.segment(0).insert("water", 200);
        FluidBox sink2 = new FluidBox(10_000);
        shortNet.setAttachments(List.of(new FluidNetwork.Attachment(1, port(sink2, PortKind.INPUT))));
        shortNet.tick();
        assertTrue(sink2.amount() > sink.amount(), "a short segment moves more per tick");
    }

    @Test
    void connectionLimitPerPort() {
        FluidNetwork net = new FluidNetwork(len -> 1e9, 100);
        line(net, 0, 10);
        net.segment(0).insert("water", 1000);
        FluidBox sink = new FluidBox(1000);
        net.setAttachments(List.of(new FluidNetwork.Attachment(0, port(sink, PortKind.INPUT))));
        net.tick();
        assertEquals(100, sink.amount(), 1e-9);
    }

    @Test
    void pumpMovesBetweenSegmentsIgnoringLimits() {
        FluidNetwork net = new FluidNetwork(FluidData::segmentLimitPerTick, 100);
        line(net, 0, 300);       // input segment
        line(net, 1000, 300);    // output segment
        net.segment(0).insert("water", 20_000);
        FluidBox pumpBox = new FluidBox(FluidData.PUMP_CAPACITY);
        double[] rate = {FluidData.PUMP_RATE_PER_TICK};
        FluidNetwork.Port in = new FluidNetwork.Port(pumpBox, PortKind.INPUT, null, () -> rate[0], true, null);
        FluidNetwork.Port out = new FluidNetwork.Port(pumpBox, PortKind.OUTPUT, null, () -> rate[0], true, null);
        net.setAttachments(List.of(new FluidNetwork.Attachment(299, in), new FluidNetwork.Attachment(1000, out)));
        assertNotSame(net.segment(0), net.segment(1000), "a pump splits segments");
        net.tick();
        net.tick();
        assertEquals(FluidData.PUMP_RATE_PER_TICK, net.segment(1000).amount(), 1e-6, "one tick's pumping arrived");
        assertEquals(FluidData.PUMP_RATE_PER_TICK, pumpBox.amount(), 1e-6);
        assertTrue(FluidData.PUMP_RATE_PER_TICK > net.segment(0).limitPerTick(), "faster than the pipes alone");
        rate[0] = 0; // unpowered
        double before = net.segment(1000).amount();
        net.tick();
        assertEquals(before, net.segment(1000).amount(), 1e-9);
    }

    @Test
    void outputsDoNotPushAnotherFluid() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 2);
        net.segment(0).insert("water", 10);
        FluidBox steam = new FluidBox(100);
        steam.insert("steam", 50);
        net.setAttachments(List.of(new FluidNetwork.Attachment(0, port(steam, PortKind.OUTPUT))));
        net.tick();
        assertEquals(50, steam.amount(), 1e-9);
        assertEquals("water", net.segment(0).fluid());
    }

    @Test
    void filterBlocksOtherFluids() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 2);
        net.segment(0).insert("water", 100);
        FluidBox box = new FluidBox(100);
        net.setAttachments(List.of(new FluidNetwork.Attachment(0, FluidNetwork.Port.of(box, PortKind.INPUT, "steam"))));
        net.tick();
        assertEquals(0, box.amount(), 1e-9);
    }

    @Test
    void passThroughEvensLevelsBothWays() {
        FluidNetwork net = new FluidNetwork(len -> 1e9, 1e9);
        line(net, 0, 2);   // segment A: full
        line(net, 10, 2);  // segment B: empty
        net.segment(0).insert("water", 200);
        FluidBox boiler = new FluidBox(200);
        FluidNetwork.Port p = port(boiler, PortKind.INPUT_OUTPUT);
        net.setAttachments(List.of(new FluidNetwork.Attachment(1, p), new FluidNetwork.Attachment(10, p)));
        for (int i = 0; i < 50; i++) net.tick();
        double fa = net.segment(0).fill(), fb = net.segment(10).fill(), fx = boiler.fill();
        assertEquals(fa, fx, 0.02);
        assertEquals(fb, fx, 0.02);
        assertEquals(200, net.segment(0).amount() + net.segment(10).amount() + boiler.amount(), 1e-6, "conserved");
    }

    @Test
    void directContactsChainMachines() {
        FluidNetwork net = new FluidNetwork();
        FluidBox boilerSteam = new FluidBox(200), engine1 = new FluidBox(200), engine2 = new FluidBox(200);
        boilerSteam.insert("steam", 200);
        net.setContacts(List.of(new FluidNetwork.Contact(port(boilerSteam, PortKind.OUTPUT), port(engine1, PortKind.INPUT_OUTPUT)),
                new FluidNetwork.Contact(port(engine1, PortKind.INPUT_OUTPUT), port(engine2, PortKind.INPUT_OUTPUT))));
        for (int i = 0; i < 10; i++) net.tick();
        assertTrue(engine2.amount() > 0, "steam did not pass the first engine");
        assertEquals(200, boilerSteam.amount() + engine1.amount() + engine2.amount(), 1e-6);
    }

    @Test
    void manyPipesTickCheaply() {
        FluidNetwork net = new FluidNetwork();
        line(net, 0, 20_000);
        FluidBox source = new FluidBox(100), sink = new FluidBox(100);
        net.setAttachments(List.of(new FluidNetwork.Attachment(0, port(source, PortKind.OUTPUT)),
                new FluidNetwork.Attachment(19_999, port(sink, PortKind.INPUT))));
        long t0 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            source.insert("water", 60);
            net.tick();
            sink.extract(sink.amount());
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 500, "1000 ticks of a 20 000-pipe segment took " + ms + " ms");
    }

    @Test
    void waterfillSharesEvenly() {
        double[] want = {5, 50, 50, 0}, out = new double[4];
        FluidNetwork.waterfill(want, out, 4, 65);
        assertArrayEquals(new double[] {5, 30, 30, 0}, out, 1e-9);
        List<Double> sums = new ArrayList<>();
        for (double v : out) sums.add(v);
        assertEquals(65, sums.stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }
}
