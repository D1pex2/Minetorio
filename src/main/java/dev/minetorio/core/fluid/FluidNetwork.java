package dev.minetorio.core.fluid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.IntToDoubleFunction;

/**
 * The fluid system of one world, Factorio 2.0 style: pipes, underground pipes and tanks are
 * <b>nodes</b>; linked nodes form a <b>segment</b> that holds one fluid and one shared amount (its
 * capacity is the sum of its nodes'). Nothing flows from pipe to pipe: a segment is one big tank,
 * so work per tick is per segment and per machine port, never per pipe.
 *
 * <p><b>Machines</b> are not nodes. Each machine port touching a node is an {@link Attachment}
 * (see {@link PortKind} for inputs, outputs and pass-through boxes); two machine ports touching
 * each other are a {@link Contact}. Every tick ({@link #tick}): first every segment takes what its
 * outputs push (pumps first), then gives to its inputs, evenly: each input gets the same share,
 * or what it wants if that is less (water-filling). Pass-through boxes even their fill level with
 * the segment. Flow into and out of a segment is limited by its length
 * ({@link FluidData#segmentLimitPerTick}) and per port ({@link FluidData#CONNECTION_LIMIT_PER_TICK});
 * pump ports are exempt from both, which is why pumps split long lines.
 *
 * <p><b>Topology</b> changes incrementally: adding a node or a link merges segments (sum of contents),
 * removing one splits the segment by a walk over it, sharing the contents by capacity (equal fill
 * level, as Factorio). One segment never holds two fluids: callers check {@link #fluidsOf} before
 * linking ({@link #link} keeps the larger amount and deletes the other if forced).
 *
 * <p>Node keys are opaque longs (the content layer packs block positions). Single-threaded.
 */
public final class FluidNetwork {
    private static final double EPS = 1e-9;
    private static final double FLOW_SMOOTHING = 0.1;

    /** A machine port as seen by the network: the box behind it and how it exchanges. */
    public record Port(FluidBox box, PortKind kind, String filter, DoubleSupplier rate, boolean pump, Runnable onChange) {
        private static final DoubleSupplier UNLIMITED = () -> Double.POSITIVE_INFINITY;
        private static final Runnable NOTHING = () -> {};

        /**
         * @param filter   the only fluid that may pass (null: any)
         * @param rate     most it moves per tick (null: unlimited, besides the connection limit)
         * @param pump     pump port: exempt from the connection and segment limits, served first
         * @param onChange called after fluid moved through the port (mark the machine for saving)
         */
        public Port {
            if (rate == null) rate = UNLIMITED;
            if (onChange == null) onChange = NOTHING;
        }

        public static Port of(FluidBox box, PortKind kind, String filter) {
            return new Port(box, kind, filter, null, false, null);
        }

        public boolean passes(String fluid) {
            return fluid == null || (filter == null || filter.equals(fluid)) && box.accepts(fluid);
        }

        double limit(double connection) {
            double r = Math.max(0, rate.getAsDouble());
            return pump ? r : Math.min(r, connection);
        }
    }

    /** A machine port touching the node with key {@code node}. */
    public record Attachment(long node, Port port) {}

    /** Two machine ports touching each other directly. */
    public record Contact(Port a, Port b) {}

    public static final class Node {
        private final long key;
        private final double capacity;
        private final Set<Node> links = new HashSet<>(4);
        private Segment segment;

        private Node(long key, double capacity) {
            this.key = key;
            this.capacity = capacity;
        }

        public long key() { return key; }

        public double capacity() { return capacity; }

        public Segment segment() { return segment; }

        /** Keys of the linked nodes. */
        public Set<Long> links() {
            Set<Long> out = new HashSet<>();
            for (Node n : links) out.add(n.key);
            return out;
        }
    }

    /** Connected nodes sharing one fluid and amount. Also a {@link FluidStore} for direct access. */
    public final class Segment implements FluidStore {
        private final int id;
        private final Set<Node> nodes = new LinkedHashSet<>();
        private String fluid;
        private double amount;
        private double capacity;
        private double lastIn, lastOut, flow;
        private final List<Port> scratch = new ArrayList<>();

        private Segment(int id) { this.id = id; }

        /** Stable while the segment exists; a split gives the new parts new ids. */
        public int id() { return id; }

        @Override
        public String fluid() { return fluid; }

        @Override
        public double amount() { return amount; }

        @Override
        public double capacity() { return capacity; }

        /** Number of nodes (pipes, underground pipes, tanks): the length for the throughput limit. */
        public int length() { return nodes.size(); }

        /** Throughput limit per tick for the current length. */
        public double limitPerTick() { return limit.applyAsDouble(nodes.size()); }

        /** Amount that entered the segment last tick. */
        public double lastIn() { return lastIn; }

        /** Amount that left the segment last tick. */
        public double lastOut() { return lastOut; }

        /** Smoothed flow per tick through the segment (the larger of in and out). */
        public double flow() { return flow; }

        /** Keys of the segment's nodes. */
        public List<Long> nodeKeys() {
            List<Long> out = new ArrayList<>(nodes.size());
            for (Node n : nodes) out.add(n.key);
            return out;
        }

        /** What the node with capacity {@code nodeCapacity} holds of the contents (equal fill level). */
        public double shareOf(double nodeCapacity) {
            return capacity <= 0 ? 0 : amount * nodeCapacity / capacity;
        }

        @Override
        public double insert(String fluid, double amount) {
            if (fluid == null || amount <= 0 || (this.fluid != null && !this.fluid.equals(fluid))) return 0;
            double inserted = Math.min(amount, capacity - this.amount);
            if (inserted <= 0) return 0;
            this.fluid = fluid;
            this.amount += inserted;
            return inserted;
        }

        @Override
        public double extract(double amount) {
            if (amount <= 0 || this.amount <= 0) return 0;
            double removed = Math.min(amount, this.amount);
            this.amount -= removed;
            clearIfEmpty();
            return removed;
        }

        /** Empties the segment (Factorio's flush). */
        public void flush() {
            amount = 0;
            fluid = null;
        }

        private void clearIfEmpty() {
            if (amount <= EPS) {
                amount = 0;
                fluid = null;
            }
        }
    }

    private final Map<Long, Node> nodes = new HashMap<>();
    private final Set<Segment> segments = new LinkedHashSet<>();
    private final IntToDoubleFunction limit;
    private final double connectionLimit;
    private List<Attachment> attachments = List.of();
    private List<Contact> contacts = List.of();
    private final List<Segment> active = new ArrayList<>();
    private double[] a = new double[8], b = new double[8];
    private int nextId = 1;

    /** A network with Factorio's limits ({@link FluidData}). */
    public FluidNetwork() {
        this(FluidData::segmentLimitPerTick, FluidData.CONNECTION_LIMIT_PER_TICK);
    }

    /**
     * @param limit           segment throughput per tick by segment length
     * @param connectionLimit most one (non-pump) machine port moves per tick
     */
    public FluidNetwork(IntToDoubleFunction limit, double connectionLimit) {
        this.limit = limit;
        this.connectionLimit = connectionLimit;
    }

    // --- queries ---------------------------------------------------------------------------------------

    public boolean has(long key) { return nodes.containsKey(key); }

    public Node node(long key) { return nodes.get(key); }

    /** Segment of the node {@code key}, or null. */
    public Segment segment(long key) {
        Node n = nodes.get(key);
        return n == null ? null : n.segment;
    }

    /** What the node {@code key} holds of its segment's contents (0 if absent). */
    public double share(long key) {
        Node n = nodes.get(key);
        return n == null ? 0 : n.segment.shareOf(n.capacity);
    }

    public Collection<Segment> segments() { return Collections.unmodifiableSet(segments); }

    public int nodeCount() { return nodes.size(); }

    /** Distinct fluids held by the segments of the given nodes (absent nodes and empty segments ignored). */
    public Set<String> fluidsOf(Iterable<Long> keys) {
        Set<String> out = new LinkedHashSet<>();
        for (long k : keys) {
            Segment s = segment(k);
            if (s != null && s.fluid != null) out.add(s.fluid);
        }
        return out;
    }

    // --- topology ------------------------------------------------------------------------------------

    /**
     * Adds a node in a segment of its own holding {@code amount} of {@code fluid} (what it held when
     * saved). Returns the node; an existing node with that key is returned unchanged.
     */
    public Node addNode(long key, double capacity, String fluid, double amount) {
        Node existing = nodes.get(key);
        if (existing != null) return existing;
        Node n = new Node(key, capacity);
        nodes.put(key, n);
        Segment s = new Segment(nextId++);
        s.nodes.add(n);
        s.capacity = capacity;
        if (fluid != null && amount > EPS) {
            s.fluid = fluid;
            s.amount = Math.min(amount, capacity);
        }
        n.segment = s;
        segments.add(s);
        return n;
    }

    /** Removes a node; returns the fluid it held (its share), which is gone. Splits its segment if needed. */
    public double removeNode(long key) {
        Node n = nodes.remove(key);
        if (n == null) return 0;
        Segment s = n.segment;
        double share = s.shareOf(n.capacity);
        s.amount -= share;
        s.capacity -= n.capacity;
        s.nodes.remove(n);
        List<Node> neighbors = new ArrayList<>(n.links);
        for (Node m : neighbors) m.links.remove(n);
        n.links.clear();
        if (s.nodes.isEmpty()) {
            segments.remove(s);
        } else {
            s.clearIfEmpty();
            split(s, neighbors);
        }
        return share;
    }

    /**
     * Links two nodes (both must exist); merges their segments. If their fluids differ (callers
     * should have refused that, see {@link #fluidsOf}), the larger amount wins and the other is
     * deleted, as in Factorio. Returns whether a new link was made.
     */
    public boolean link(long ka, long kb) {
        Node x = nodes.get(ka), y = nodes.get(kb);
        if (x == null || y == null || x == y || x.links.contains(y)) return false;
        x.links.add(y);
        y.links.add(x);
        if (x.segment != y.segment) merge(x.segment, y.segment);
        return true;
    }

    /** Removes the link between two nodes; splits the segment if they are no longer connected. */
    public void unlink(long ka, long kb) {
        Node x = nodes.get(ka), y = nodes.get(kb);
        if (x == null || y == null || !x.links.remove(y)) return;
        y.links.remove(x);
        split(x.segment, List.of(x, y));
    }

    /** Makes the links of node {@code key} exactly {@code desired} (absent keys ignored). */
    public void setLinks(long key, Set<Long> desired) {
        Node n = nodes.get(key);
        if (n == null) return;
        for (Node m : new ArrayList<>(n.links)) {
            if (!desired.contains(m.key)) unlink(key, m.key);
        }
        for (long d : desired) link(key, d);
    }

    private void merge(Segment s, Segment t) {
        Segment big = s.nodes.size() >= t.nodes.size() ? s : t;
        Segment small = big == s ? t : s;
        if (big.fluid != null && small.fluid != null && !big.fluid.equals(small.fluid)) {
            if (small.amount > big.amount) {
                big.fluid = small.fluid;
                big.amount = small.amount;
            }
        } else {
            if (big.fluid == null) big.fluid = small.fluid;
            big.amount += small.amount;
        }
        for (Node n : small.nodes) n.segment = big;
        big.nodes.addAll(small.nodes);
        big.capacity += small.capacity;
        big.amount = Math.min(big.amount, big.capacity);
        segments.remove(small);
    }

    /**
     * Splits {@code s} into its connected parts after links were removed; {@code starts} are nodes
     * of {@code s} that may now lie in different parts. The contents are shared by capacity.
     */
    private void split(Segment s, List<Node> starts) {
        if (starts.size() < 2) return;
        Set<Node> seen = new HashSet<>();
        List<Set<Node>> parts = new ArrayList<>();
        for (Node start : starts) {
            if (start.segment != s || seen.contains(start)) continue;
            Set<Node> part = new HashSet<>();
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                Node n = queue.poll();
                part.add(n);
                for (Node m : n.links) if (seen.add(m)) queue.add(m);
            }
            parts.add(part);
            if (parts.size() == 1 && part.size() == s.nodes.size()) return; // still one piece
        }
        if (parts.size() < 2) return;
        double total = s.amount, cap = s.capacity;
        for (int i = 1; i < parts.size(); i++) {
            Set<Node> part = parts.get(i);
            Segment t = new Segment(nextId++);
            for (Node n : part) {
                n.segment = t;
                t.capacity += n.capacity;
            }
            t.nodes.addAll(part);
            s.nodes.removeAll(part);
            t.fluid = s.fluid;
            t.amount = cap <= 0 ? 0 : total * t.capacity / cap;
            t.flow = s.flow;
            s.capacity -= t.capacity;
            s.amount -= t.amount;
            t.clearIfEmpty();
            segments.add(t);
        }
        s.clearIfEmpty();
    }

    // --- machines ------------------------------------------------------------------------------------

    /** Replaces all machine attachments (the content layer rebuilds them when something changed). */
    public void setAttachments(List<Attachment> attachments) { this.attachments = List.copyOf(attachments); }

    /** Replaces all machine-to-machine contacts. */
    public void setContacts(List<Contact> contacts) { this.contacts = List.copyOf(contacts); }

    public List<Attachment> attachments() { return attachments; }

    // --- ticking -------------------------------------------------------------------------------------

    /** One tick: outputs into segments, segments to inputs, then direct machine contacts. */
    public void tick() {
        for (Segment s : segments) {
            s.lastIn = 0;
            s.lastOut = 0;
        }
        for (Attachment att : attachments) {
            Node n = nodes.get(att.node());
            if (n == null) continue;
            Segment s = n.segment;
            if (s.scratch.isEmpty()) active.add(s);
            s.scratch.add(att.port());
        }
        for (Segment s : active) inflow(s);
        for (Segment s : active) outflow(s);
        for (Segment s : active) s.scratch.clear();
        active.clear();
        for (Contact c : contacts) contact(c.a(), c.b());
        for (Segment s : segments) s.flow += (Math.max(s.lastIn, s.lastOut) - s.flow) * FLOW_SMOOTHING;
    }

    /** Amount to move from a to b so both end at the same fill level (negative: b to a). */
    static double equalize(double aAmount, double aCap, double bAmount, double bCap) {
        if (aCap + bCap <= 0) return 0;
        return (aAmount * bCap - bAmount * aCap) / (aCap + bCap);
    }

    private void ensure(int n) {
        if (a.length < n) {
            a = new double[Math.max(n, a.length * 2)];
            b = new double[a.length];
        }
    }

    private void inflow(Segment s) {
        double room = s.capacity - s.amount;
        if (room <= EPS) return;
        List<Port> ports = s.scratch;
        int n = ports.size();
        ensure(n);
        String fluid = s.fluid;
        double pumps = 0, others = 0;
        for (int i = 0; i < n; i++) {
            a[i] = 0;
            Port p = ports.get(i);
            if (!p.kind().gives()) continue;
            FluidBox box = p.box();
            String f = box.fluid();
            if (f == null || box.amount() <= EPS || !p.passes(f) || (fluid != null && !fluid.equals(f))) continue;
            double offer = p.kind() == PortKind.OUTPUT ? box.amount() : equalize(box.amount(), box.capacity(), s.amount, s.capacity);
            offer = Math.min(offer, p.limit(connectionLimit));
            if (offer <= EPS) continue;
            fluid = f;
            a[i] = offer;
            if (p.pump()) pumps += offer;
            else others += offer;
        }
        if (fluid == null) return;
        if (pumps > 0) room -= give(s, fluid, true, Math.min(1, room / pumps));
        if (others > 0) give(s, fluid, false, Math.min(1, Math.min(room, s.limitPerTick()) / others));
    }

    /** Moves the offers a[i] of the pump (or non-pump) ports scaled by k into the segment; returns the amount. */
    private double give(Segment s, String fluid, boolean pumps, double k) {
        double moved = 0;
        List<Port> ports = s.scratch;
        for (int i = 0; i < ports.size(); i++) {
            Port p = ports.get(i);
            if (a[i] <= 0 || p.pump() != pumps) continue;
            double x = p.box().extract(a[i] * k);
            if (x <= 0) continue;
            s.fluid = fluid;
            s.amount += x;
            s.lastIn += x;
            moved += x;
            p.onChange().run();
        }
        s.amount = Math.min(s.amount, s.capacity);
        return moved;
    }

    private void outflow(Segment s) {
        if (s.amount <= EPS || s.fluid == null) return;
        List<Port> ports = s.scratch;
        int n = ports.size();
        ensure(n);
        String fluid = s.fluid;
        for (int i = 0; i < n; i++) {
            a[i] = 0;
            Port p = ports.get(i);
            if (!p.kind().takes() || !p.passes(fluid)) continue;
            FluidBox box = p.box();
            if (box.fluid() != null && !box.fluid().equals(fluid)) continue;
            double want = p.kind() == PortKind.INPUT ? box.room() : equalize(s.amount, s.capacity, box.amount(), box.capacity());
            a[i] = Math.max(0, Math.min(Math.min(want, box.room()), p.limit(connectionLimit)));
        }
        waterfill(a, b, n, s.amount);
        double others = 0;
        for (int i = 0; i < n; i++) if (!ports.get(i).pump()) others += b[i];
        double limitK = others > s.limitPerTick() ? s.limitPerTick() / others : 1;
        for (int i = 0; i < n; i++) {
            Port p = ports.get(i);
            double x = p.pump() ? b[i] : b[i] * limitK;
            if (x <= EPS) continue;
            double moved = p.box().insert(fluid, Math.min(x, s.amount));
            if (moved <= 0) continue;
            s.amount -= moved;
            s.lastOut += moved;
            p.onChange().run();
        }
        s.clearIfEmpty();
    }

    /**
     * Even distribution: {@code out[i]} = what wanter i gets of {@code budget}. Everybody gets the
     * same share, except who wants less, whose leftover goes to the others.
     */
    static void waterfill(double[] want, double[] out, int n, double budget) {
        Integer[] order = new Integer[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            out[i] = 0;
            if (want[i] > EPS) order[count++] = i;
        }
        Arrays.sort(order, 0, count, (x, y) -> Double.compare(want[x], want[y]));
        double remaining = budget;
        for (int k = 0; k < count && remaining > EPS; k++) {
            int i = order[k];
            double give = Math.min(want[i], remaining / (count - k));
            out[i] = give;
            remaining -= give;
        }
    }

    private void contact(Port p, Port q) {
        if (p.kind() == PortKind.INPUT_OUTPUT && q.kind() == PortKind.INPUT_OUTPUT) {
            double t = equalize(p.box().amount(), p.box().capacity(), q.box().amount(), q.box().capacity());
            if (t > 0) move(p, q, t);
            else if (t < 0) move(q, p, -t);
        } else if (p.kind().gives() && q.kind().takes()) {
            move(p, q, Double.POSITIVE_INFINITY);
        } else if (q.kind().gives() && p.kind().takes()) {
            move(q, p, Double.POSITIVE_INFINITY);
        }
    }

    private void move(Port from, Port to, double max) {
        String f = from.box().fluid();
        if (f == null || !from.passes(f) || !to.passes(f)) return;
        if (to.box().fluid() != null && !to.box().fluid().equals(f)) return;
        double x = Math.min(Math.min(max, from.box().amount()), to.box().room());
        x = Math.min(x, Math.min(from.limit(connectionLimit), to.limit(connectionLimit)));
        if (x <= EPS) return;
        double moved = to.box().insert(f, x);
        if (moved <= EPS) return;
        from.box().extract(moved);
        from.onChange().run();
        to.onChange().run();
    }
}
