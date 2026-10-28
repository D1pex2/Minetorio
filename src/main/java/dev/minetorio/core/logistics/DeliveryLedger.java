package dev.minetorio.core.logistics;

import java.util.HashMap;
import java.util.Map;

/**
 * Items promised to logistic robots in flight, per node (a chest or a player) and item: <b>outgoing</b>
 * items a robot is on its way to pick up there, <b>incoming</b> items a robot is bringing there. The
 * planner counts a source's stock minus its outgoing items, and a requester's contents plus its
 * incoming items, so two robots are never sent for the same item.
 */
public final class DeliveryLedger {
    private final Map<String, Map<String, Integer>> outgoing = new HashMap<>();
    private final Map<String, Map<String, Integer>> incoming = new HashMap<>();

    public int outgoing(String node, String item) { return get(outgoing, node, item); }

    public int incoming(String node, String item) { return get(incoming, node, item); }

    public void addOutgoing(String node, String item, int n) { add(outgoing, node, item, n); }

    public void removeOutgoing(String node, String item, int n) { add(outgoing, node, item, -n); }

    public void addIncoming(String node, String item, int n) { add(incoming, node, item, n); }

    public void removeIncoming(String node, String item, int n) { add(incoming, node, item, -n); }

    /** Nothing promised anywhere. */
    public boolean isEmpty() { return outgoing.isEmpty() && incoming.isEmpty(); }

    private static int get(Map<String, Map<String, Integer>> m, String node, String item) {
        Map<String, Integer> byItem = m.get(node);
        return byItem == null ? 0 : byItem.getOrDefault(item, 0);
    }

    private static void add(Map<String, Map<String, Integer>> m, String node, String item, int n) {
        if (n == 0) return;
        Map<String, Integer> byItem = m.computeIfAbsent(node, k -> new HashMap<>());
        int v = byItem.getOrDefault(item, 0) + n;
        if (v <= 0) byItem.remove(item);
        else byItem.put(item, v);
        if (byItem.isEmpty()) m.remove(node);
    }
}
