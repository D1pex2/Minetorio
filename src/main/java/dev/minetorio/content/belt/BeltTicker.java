package dev.minetorio.content.belt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Ticks every loaded {@link BeltNode} of one level (server or client) once per level tick,
 * downstream-first: a node is ticked after every node it hands items into. The order is a
 * post-order DFS over "hands into" edges, rebuilt whenever a node is added, removed or re-linked;
 * cycles are broken at an arbitrary point (one hand-off per loop then loses its sub-tick overflow).
 * Nodes in chunks that do not tick (e.g. border chunks) are skipped, like block entities.
 */
public final class BeltTicker {
    private static final Map<Level, BeltTicker> TICKERS = Collections.synchronizedMap(new WeakHashMap<>());

    private final Set<BeltNode> nodes = new LinkedHashSet<>();
    private final Map<BlockPos, BeltNode> byPos = new HashMap<>();
    private final Level level;
    private List<BeltNode> order = List.of();
    private boolean dirty;

    private BeltTicker(Level level) {
        this.level = level;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BeltTicker::onLevelTick);
        NeoForge.EVENT_BUS.addListener(BeltTicker::onLevelUnload);
    }

    public static BeltTicker of(Level level) {
        return TICKERS.computeIfAbsent(level, BeltTicker::new);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        BeltTicker ticker = TICKERS.get(event.getLevel());
        if (ticker != null) ticker.tick();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) TICKERS.remove(level);
    }

    public void add(BeltNode node) {
        if (!nodes.add(node)) return;
        for (BlockPos p : node.occupied()) byPos.put(p.immutable(), node);
        dirty = true;
    }

    public void remove(BeltNode node) {
        if (!nodes.remove(node)) return;
        for (BlockPos p : node.occupied()) byPos.remove(p, node);
        dirty = true;
    }

    /** Call when a node's downstream links change (e.g. an underground pair was linked). */
    public void markDirty() {
        dirty = true;
    }

    /** The registered node occupying {@code pos}, or null. */
    @Nullable
    public BeltNode at(BlockPos pos) {
        BeltNode n = byPos.get(pos);
        return n == null || n.isRemoved() ? null : n;
    }

    private void tick() {
        if (dirty) rebuild();
        int n = order.size();
        boolean[] live = new boolean[n];
        for (int i = 0; i < n; i++) {
            BeltNode node = order.get(i);
            live[i] = !node.isRemoved() && level.shouldTickBlocksAt(node.occupied().get(0));
            if (live[i]) node.tickBelt();
        }
        for (int i = 0; i < n; i++) {
            if (live[i]) order.get(i).afterBeltTick();
        }
    }

    private void rebuild() {
        dirty = false;
        nodes.removeIf(BeltNode::isRemoved);
        byPos.values().removeIf(BeltNode::isRemoved);
        List<BeltNode> out = new ArrayList<>(nodes.size());
        Set<BeltNode> visited = new HashSet<>();
        // Iterative post-order DFS: a node is emitted after everything downstream of it.
        Deque<Iterator<BlockPos>> stack = new ArrayDeque<>();
        Deque<BeltNode> path = new ArrayDeque<>();
        for (BeltNode root : nodes) {
            if (!visited.add(root)) continue;
            path.push(root);
            stack.push(root.downstream().iterator());
            while (!stack.isEmpty()) {
                Iterator<BlockPos> it = stack.peek();
                if (it.hasNext()) {
                    BeltNode next = byPos.get(it.next());
                    if (next != null && visited.add(next)) {
                        path.push(next);
                        stack.push(next.downstream().iterator());
                    }
                } else {
                    stack.pop();
                    out.add(path.pop());
                }
            }
        }
        order = out;
    }

    /** Ticking order, downstream first (for tests). */
    public List<BeltNode> order() {
        if (dirty) rebuild();
        return Collections.unmodifiableList(order);
    }
}
