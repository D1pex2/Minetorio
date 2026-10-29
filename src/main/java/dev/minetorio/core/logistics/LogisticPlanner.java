package dev.minetorio.core.logistics;

import dev.minetorio.core.robot.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plans logistic robot trips in one network (Factorio 1.1 rules), one trip per free robot:
 * <ol>
 *   <li><b>Requests</b>: requester chests and players first, then buffer chests. Each request slot
 *       short of its minimum ({@link RequestMath#deficit}, counting items already on their way) gets
 *       items from an <b>active provider</b> if any has them, otherwise from the nearest <b>storage,
 *       passive provider or buffer chest</b>. Buffer chests supply players always, requester chests
 *       only with "request from buffer chests" on, and never other buffer chests.</li>
 *   <li><b>Active providers</b> are emptied into storage.</li>
 *   <li><b>Player trash</b> (above a request's maximum, {@link RequestMath#trash}) goes to storage.</li>
 * </ol>
 * Storage is chosen like Factorio: a storage chest filtered to the item, else one already holding it,
 * else an empty one (filtered chests take only their item); the nearest in each tier. Every trip is
 * entered in the {@link DeliveryLedger} (undo it with {@link #cancel} if the robot cannot start).
 */
public final class LogisticPlanner {
    private LogisticPlanner() {}

    /** What a node is to the planner. */
    public enum Role { PASSIVE_PROVIDER, ACTIVE_PROVIDER, STORAGE, REQUESTER, BUFFER, PLAYER }

    /**
     * A chest or player in the network. {@code contents}: items by id; {@code requests}: request
     * slots (requester, buffer, player); {@code filter}: a storage chest's item filter or null;
     * {@code fromBuffers}: a requester chest's "request from buffer chests".
     */
    public record Node(String id, Role role, Cell cell, Map<String, Integer> contents, List<LogisticRequest> requests,
                       String filter, boolean fromBuffers, Map<String, Integer> trash) {
        public Node(String id, Role role, Cell cell, Map<String, Integer> contents, List<LogisticRequest> requests,
                    String filter, boolean fromBuffers) {
            this(id, role, cell, contents, requests, filter, fromBuffers, Map.of());
        }
        public int count(String item) { return contents.getOrDefault(item, 0); }
    }

    /** One robot trip: take {@code count} of {@code item} at {@code from}, bring it to {@code to}. */
    public record Delivery(String from, String to, String item, int count) {}

    /** Room for {@code item} in a storage node before any promised deliveries (how many more it can take). */
    @FunctionalInterface
    public interface Space {
        int room(Node node, String item);
    }

    public static List<Delivery> plan(List<Node> nodes, DeliveryLedger ledger, int robots, int cargo, Space space) {
        List<Delivery> out = new ArrayList<>();
        if (robots <= 0 || cargo <= 0) return out;
        int[] left = {robots};
        // 1. requests: requesters and players before buffers
        for (int tier = 0; tier < 2; tier++) {
            for (Node dest : nodes) {
                boolean first = dest.role == Role.REQUESTER || dest.role == Role.PLAYER;
                if (tier == 0 ? !first : dest.role != Role.BUFFER) continue;
                for (LogisticRequest r : dest.requests) {
                    int deficit = RequestMath.deficit(r.min(), dest.count(r.item()), ledger.incoming(dest.id, r.item()));
                    while (deficit > 0 && left[0] > 0) {
                        Node src = pickSource(nodes, dest, r.item(), ledger);
                        if (src == null) break;
                        int n = Math.min(cargo, Math.min(deficit, available(src, r.item(), ledger)));
                        add(out, ledger, src, dest, r.item(), n, left);
                        deficit -= n;
                    }
                }
            }
        }
        // 2. active providers empty into storage
        for (Node src : nodes) {
            if (src.role != Role.ACTIVE_PROVIDER) continue;
            for (String item : src.contents.keySet()) {
                toStorage(nodes, ledger, space, src, item, available(src, item, ledger), cargo, left, out);
            }
        }
        // 3. player trash to storage
        for (Node src : nodes) {
            if (src.role != Role.PLAYER) continue;
            for (var entry : src.trash.entrySet()) {
                int pending = Math.max(0, entry.getValue() - ledger.outgoing(src.id, entry.getKey()));
                toStorage(nodes, ledger, space, src, entry.getKey(), pending, cargo, left, out);
            }
            for (LogisticRequest r : src.requests) {
                int trash = RequestMath.trash(r.max(), src.count(r.item()), ledger.outgoing(src.id, r.item()));
                toStorage(nodes, ledger, space, src, r.item(), trash, cargo, left, out);
            }
        }
        return out;
    }

    /** Undoes the ledger entries of a planned trip whose robot could not start. */
    public static void cancel(DeliveryLedger ledger, Delivery d) {
        ledger.removeOutgoing(d.from, d.item, d.count);
        ledger.removeIncoming(d.to, d.item, d.count);
    }

    private static void toStorage(List<Node> nodes, DeliveryLedger ledger, Space space, Node src, String item, int amount,
                                  int cargo, int[] left, List<Delivery> out) {
        while (amount > 0 && left[0] > 0) {
            Node dest = pickStorage(nodes, item, src.cell, ledger, space);
            if (dest == null) return;
            int n = Math.min(cargo, Math.min(amount, room(dest, item, ledger, space)));
            add(out, ledger, src, dest, item, n, left);
            amount -= n;
        }
    }

    private static void add(List<Delivery> out, DeliveryLedger ledger, Node src, Node dest, String item, int n, int[] left) {
        out.add(new Delivery(src.id, dest.id, item, n));
        ledger.addOutgoing(src.id, item, n);
        ledger.addIncoming(dest.id, item, n);
        left[0]--;
    }

    /** Stock at {@code src} not yet promised to a robot. */
    static int available(Node src, String item, DeliveryLedger ledger) {
        return src.count(item) - ledger.outgoing(src.id, item);
    }

    /** May {@code src} supply a request of {@code dest}? */
    static boolean supplies(Node src, Node dest) {
        if (src.id.equals(dest.id)) return false;
        return switch (src.role) {
            case ACTIVE_PROVIDER, PASSIVE_PROVIDER, STORAGE -> true;
            case BUFFER -> dest.role == Role.PLAYER || (dest.role == Role.REQUESTER && dest.fromBuffers);
            default -> false;
        };
    }

    /** The source for {@code item} to {@code dest}: active providers first, then the nearest storage/passive/buffer. */
    static Node pickSource(List<Node> nodes, Node dest, String item, DeliveryLedger ledger) {
        Node best = null;
        int bestTier = Integer.MAX_VALUE;
        long bestD = Long.MAX_VALUE;
        for (Node n : nodes) {
            if (!supplies(n, dest) || available(n, item, ledger) <= 0) continue;
            int tier = n.role == Role.ACTIVE_PROVIDER ? 0 : 1;
            long d = n.cell.distSq(dest.cell);
            if (tier < bestTier || (tier == bestTier && d < bestD)) {
                best = n;
                bestTier = tier;
                bestD = d;
            }
        }
        return best;
    }

    private static int room(Node n, String item, DeliveryLedger ledger, Space space) {
        return space.room(n, item) - ledger.incoming(n.id, item);
    }

    /** The storage chest for {@code item}: filtered to it, else holding it, else empty-filtered; nearest to {@code from}. */
    public static Node pickStorage(List<Node> nodes, String item, Cell from, DeliveryLedger ledger, Space space) {
        Node best = null;
        int bestTier = Integer.MAX_VALUE;
        long bestD = Long.MAX_VALUE;
        for (Node n : nodes) {
            if (n.role != Role.STORAGE) continue;
            if (n.filter != null && !n.filter.equals(item)) continue;
            if (room(n, item, ledger, space) <= 0) continue;
            int tier = n.filter != null ? 0 : n.count(item) > 0 ? 1 : 2;
            long d = n.cell.distSq(from);
            if (tier < bestTier || (tier == bestTier && d < bestD)) {
                best = n;
                bestTier = tier;
                bestD = d;
            }
        }
        return best;
    }
}
