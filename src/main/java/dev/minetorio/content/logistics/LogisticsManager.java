package dev.minetorio.content.logistics;

import dev.minetorio.content.registry.ModEntities;
import dev.minetorio.content.robot.ConstructionManager;
import dev.minetorio.content.robot.ConstructionRobotEntity;
import dev.minetorio.content.robot.RoboportBlockEntity;
import dev.minetorio.content.robot.RoboportMenu;
import dev.minetorio.core.logistics.DeliveryLedger;
import dev.minetorio.core.logistics.LogisticArea;
import dev.minetorio.core.logistics.LogisticMode;
import dev.minetorio.core.logistics.LogisticNetworks;
import dev.minetorio.core.logistics.LogisticPlanner;
import dev.minetorio.core.logistics.LogisticRequest;
import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.robot.Cell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Logistic networks of one server level, in memory only: the loaded logistic chests, the
 * {@link DeliveryLedger} of items promised to robots in flight (robots re-enter theirs when they
 * load), and the loaded logistic robots.
 *
 * <p>Every {@value LogisticStats#DISPATCH_INTERVAL} ticks the networks are rebuilt from the loaded
 * roboports ({@link LogisticNetworks}: logistic areas of {@link LogisticStats#LOGISTIC_RADIUS}
 * blocks around each roboport that touch join up), every chest and every player with personal
 * requests inside one becomes a node, {@link LogisticPlanner} plans one trip per free logistic
 * robot, and each trip launches a robot from the network's roboport nearest its pickup. Players
 * with a roboport GUI open get their network's figures every second.
 */
public final class LogisticsManager {
    private static final Map<ServerLevel, LogisticsManager> MANAGERS = new WeakHashMap<>();
    private static final int STATS_INTERVAL = 20;

    private final Set<BlockPos> chests = new HashSet<>();
    private final DeliveryLedger ledger = new DeliveryLedger();
    private final Set<LogisticRobotEntity> robots = Collections.newSetFromMap(new WeakHashMap<>());
    private int timer;

    private LogisticsManager() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel server) MANAGERS.remove(server);
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel server) get(server).tick(server);
        });
    }

    public static LogisticsManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, l -> new LogisticsManager());
    }

    @Nullable
    public static LogisticsManager getIfPresent(ServerLevel level) { return MANAGERS.get(level); }

    public DeliveryLedger ledger() { return ledger; }

    void addChest(BlockPos p) { chests.add(p.immutable()); }

    void removeChest(BlockPos p) { chests.remove(p); }

    void track(LogisticRobotEntity r) { robots.add(r); }

    void untrack(LogisticRobotEntity r) { robots.remove(r); }

    /** Loaded robots of this level (logistic and personal construction). */
    public List<LogisticRobotEntity> robots() { return new ArrayList<>(robots); }

    /** Players served besides the level's own (GameTests: FakePlayers are not in the player list). */
    private static final Set<Player> EXTRA_PLAYERS = Collections.newSetFromMap(new WeakHashMap<>());

    /** GameTests: robots serve {@code p} like a player of its level until {@link #removeExtraPlayer}. */
    public static void addExtraPlayer(Player p) { EXTRA_PLAYERS.add(p); }

    public static void removeExtraPlayer(Player p) { EXTRA_PLAYERS.remove(p); }

    /** The level's players and the extra ones in it. */
    static List<Player> players(ServerLevel level) {
        List<Player> out = new ArrayList<>(level.players());
        for (Player p : EXTRA_PLAYERS) if (p.level() == level && !p.isRemoved()) out.add(p);
        return out;
    }

    /** A player of the level by id (extra players included), or null. */
    @Nullable
    static Player player(ServerLevel level, @Nullable UUID id) {
        if (id == null) return null;
        Player p = level.getPlayerByUUID(id);
        if (p != null) return p;
        for (Player e : EXTRA_PLAYERS) if (e.level() == level && e.getUUID().equals(id)) return e;
        return null;
    }

    public static String chestId(BlockPos p) { return "c:" + p.asLong(); }

    public static String playerId(UUID id) { return "p:" + id; }

    @Nullable
    static IItemHandler handler(ServerLevel level, @Nullable BlockPos p) {
        if (p == null || !level.isLoaded(p)) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
    }

    // --- networks -----------------------------------------------------------------------------------

    /** A roboport's logistic area. */
    public static LogisticArea logisticArea(RoboportBlockEntity port) {
        Vec3 c = port.center();
        return new LogisticArea(c.x, port.getBlockPos().getY() + 0.5, c.z, LogisticStats.LOGISTIC_RADIUS);
    }

    /** One logistic network: its roboports and their areas (same order). */
    public record Network(List<RoboportBlockEntity> ports, List<LogisticArea> areas) {
        public boolean contains(double x, double y, double z) {
            for (LogisticArea a : areas) if (a.contains(x, y, z)) return true;
            return false;
        }

        public boolean contains(BlockPos p) { return contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5); }

        public boolean hasPort(@Nullable BlockPos controller) {
            for (RoboportBlockEntity p : ports) if (p.getBlockPos().equals(controller)) return true;
            return false;
        }
    }

    /** The level's logistic networks, from the loaded roboports. */
    public static List<Network> networks(ServerLevel level) {
        List<RoboportBlockEntity> ports = new ArrayList<>();
        for (BlockPos p : ConstructionManager.get(level).roboports()) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof RoboportBlockEntity be && !be.isRemoved()) ports.add(be);
        }
        ports.sort((a, b) -> Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong()));
        List<LogisticArea> areas = new ArrayList<>(ports.size());
        for (RoboportBlockEntity p : ports) areas.add(logisticArea(p));
        List<Network> out = new ArrayList<>();
        for (List<Integer> g : LogisticNetworks.group(areas)) {
            List<RoboportBlockEntity> np = new ArrayList<>();
            List<LogisticArea> na = new ArrayList<>();
            for (int i : g) {
                np.add(ports.get(i));
                na.add(areas.get(i));
            }
            out.add(new Network(np, na));
        }
        return out;
    }

    @Nullable
    public static Network networkAt(ServerLevel level, BlockPos p) {
        for (Network n : networks(level)) if (n.contains(p)) return n;
        return null;
    }

    /** Whether the player is inside the network of roboport {@code home} (any network if home is null). */
    boolean inNetwork(ServerLevel level, @Nullable UUID player, @Nullable BlockPos home) {
        Player p = player(level, player);
        if (p == null) return false;
        for (Network n : networks(level)) {
            if ((home == null || n.hasPort(home)) && n.contains(p.getX(), p.getY(), p.getZ())) return true;
        }
        return false;
    }

    /** The nearest loaded roboport whose logistic area holds {@code pos} or is within twice its radius of it. */
    @Nullable
    static RoboportBlockEntity nearestRoboport(ServerLevel level, Vec3 pos) {
        RoboportBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        double reach = 2.0 * LogisticStats.LOGISTIC_RADIUS;
        for (BlockPos p : ConstructionManager.get(level).roboports()) {
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof RoboportBlockEntity be)) continue;
            double d = be.center().distanceToSqr(pos);
            if (d < bestD && d <= reach * reach) {
                bestD = d;
                best = be;
            }
        }
        return best;
    }

    // --- nodes --------------------------------------------------------------------------------------

    private List<LogisticChestBlockEntity> chestsIn(ServerLevel level, Network n) {
        List<LogisticChestBlockEntity> out = new ArrayList<>();
        for (BlockPos p : chests) {
            if (n.contains(p) && level.isLoaded(p) && level.getBlockEntity(p) instanceof LogisticChestBlockEntity be) out.add(be);
        }
        out.sort((a, b) -> Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong()));
        return out;
    }

    private static Cell cell(BlockPos p) { return new Cell(p.getX(), p.getY(), p.getZ()); }

    private static LogisticPlanner.Node node(LogisticChestBlockEntity be) {
        LogisticMode mode = be.mode();
        List<LogisticRequest> req = mode == LogisticMode.REQUESTER || mode == LogisticMode.BUFFER ? be.requests().requests() : List.of();
        return new LogisticPlanner.Node(chestId(be.getBlockPos()), mode.role(), cell(be.getBlockPos()), be.counts(), req,
                be.filter(), be.requestFromBuffers());
    }

    private static LogisticPlanner.Node node(Player p, PersonalLogistics pl) {
        return new LogisticPlanner.Node(playerId(p.getUUID()), LogisticPlanner.Role.PLAYER, cell(p.blockPosition()),
                PlayerItems.counts(p), pl.requests().requests(), null, false);
    }

    /** Players with active personal requests standing in {@code n}. */
    private static List<Player> players(ServerLevel level, Network n) {
        List<Player> out = new ArrayList<>();
        for (Player p : players(level)) {
            if (!p.isAlive() || p.isSpectator()) continue;
            PersonalLogistics pl = PersonalLogistics.of(p);
            if (pl.enabled() && !pl.requests().isEmpty() && n.contains(p.getX(), p.getY(), p.getZ())) out.add(p);
        }
        return out;
    }

    /** A storage chest of the network at {@code from} that takes {@code stack} (Factorio's storage choice), or null. */
    @Nullable
    BlockPos storageFor(ServerLevel level, ItemStack stack, BlockPos from) {
        Network n = networkAt(level, from);
        if (n == null) return null;
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Map<String, LogisticChestBlockEntity> byId = new HashMap<>();
        List<LogisticPlanner.Node> nodes = new ArrayList<>();
        for (LogisticChestBlockEntity be : chestsIn(level, n)) {
            if (be.mode() != LogisticMode.STORAGE) continue;
            LogisticPlanner.Node node = node(be);
            nodes.add(node);
            byId.put(node.id(), be);
        }
        LogisticPlanner.Node pick = LogisticPlanner.pickStorage(nodes, item, cell(from), ledger,
                (node, it) -> byId.get(node.id()).room(it));
        return pick == null ? null : byId.get(pick.id()).getBlockPos();
    }

    // --- ticking ------------------------------------------------------------------------------------

    private void tick(ServerLevel level) {
        timer++;
        if (timer % LogisticStats.DISPATCH_INTERVAL == 0) {
            for (Network n : networks(level)) dispatch(level, n);
        }
        if (timer % STATS_INTERVAL == 0) sendStats(level);
    }

    private void dispatch(ServerLevel level, Network n) {
        int robots = 0;
        for (RoboportBlockEntity p : n.ports) if (p.buffer() > 0) robots += p.availableLogisticRobots();
        if (robots <= 0) return;
        List<LogisticPlanner.Node> nodes = new ArrayList<>();
        Map<String, LogisticChestBlockEntity> chestById = new HashMap<>();
        Map<String, Endpoint> endpoints = new HashMap<>();
        for (LogisticChestBlockEntity be : chestsIn(level, n)) {
            LogisticPlanner.Node node = node(be);
            nodes.add(node);
            chestById.put(node.id(), be);
            endpoints.put(node.id(), Endpoint.chest(be.getBlockPos()));
        }
        for (Player p : players(level, n)) {
            LogisticPlanner.Node node = node(p, PersonalLogistics.of(p));
            nodes.add(node);
            endpoints.put(node.id(), Endpoint.player(p.getUUID()));
        }
        if (nodes.isEmpty()) return;
        List<LogisticPlanner.Delivery> plan = LogisticPlanner.plan(nodes, ledger, robots, LogisticStats.CARGO,
                (node, item) -> {
                    LogisticChestBlockEntity be = chestById.get(node.id());
                    return be == null ? 0 : be.room(item);
                });
        Map<String, Cell> cells = new HashMap<>();
        for (LogisticPlanner.Node node : nodes) cells.put(node.id(), node.cell());
        for (LogisticPlanner.Delivery d : plan) {
            if (!launch(level, n, cells.get(d.from()), endpoints.get(d.from()), endpoints.get(d.to()), d)) {
                LogisticPlanner.cancel(ledger, d);
            }
        }
    }

    private boolean launch(ServerLevel level, Network n, Cell from, Endpoint source, Endpoint target, LogisticPlanner.Delivery d) {
        RoboportBlockEntity port = null;
        double best = Double.MAX_VALUE;
        Vec3 at = new Vec3(from.x() + 0.5, from.y() + 0.5, from.z() + 0.5);
        for (RoboportBlockEntity p : n.ports) {
            if (p.buffer() <= 0 || p.availableLogisticRobots() <= 0) continue;
            double dist = p.center().distanceToSqr(at);
            if (dist < best) {
                best = dist;
                port = p;
            }
        }
        if (port == null || !port.takeLogisticRobot()) return false;
        LogisticRobotEntity robot = new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(), level);
        Vec3 dock = port.dock();
        robot.moveTo(dock.x, dock.y, dock.z, port.facing().toYRot(), 0);
        robot.startDelivery(port.getBlockPos(), source, target, d.item(), d.count());
        if (!level.addFreshEntity(robot)) {
            port.storeRobot(new ItemStack(LogisticsRegistry.LOGISTIC_ROBOT.get()));
            port.robotReturned();
            return false;
        }
        track(robot);
        return true;
    }

    // --- network figures for the roboport GUI --------------------------------------------------------

    private void sendStats(ServerLevel level) {
        List<Network> networks = null;
        for (ServerPlayer player : level.players()) {
            if (!(player.containerMenu instanceof RoboportMenu menu) || menu.pos() == null) continue;
            if (networks == null) networks = networks(level);
            for (Network n : networks) {
                if (n.hasPort(menu.pos())) {
                    PacketDistributor.sendToPlayer(player, stats(level, n, menu.containerId));
                    break;
                }
            }
        }
    }

    /** Robots and items of network {@code n}. */
    public LogisticsPayloads.NetworkStats stats(ServerLevel level, Network n, int containerId) {
        int la = 0, ca = 0;
        Set<BlockPos> homes = new HashSet<>();
        for (RoboportBlockEntity p : n.ports) {
            la += p.availableLogisticRobots();
            ca += p.availableRobots();
            homes.add(p.getBlockPos());
        }
        int lFlying = 0;
        for (LogisticRobotEntity r : robots) {
            if (!r.isRemoved() && r.kind() == LogisticRobotEntity.Kind.LOGISTIC && homes.contains(r.home())) lFlying++;
        }
        int cFlying = 0;
        for (ConstructionRobotEntity r : level.getEntities(ModEntities.CONSTRUCTION_ROBOT.get(), r -> homes.contains(r.home()))) cFlying++;
        Map<String, Integer> items = networkItems(level, n);
        List<LogisticsPayloads.ItemCount> lines = new ArrayList<>();
        items.forEach((k, v) -> lines.add(new LogisticsPayloads.ItemCount(k, v)));
        lines.sort((a, b) -> a.count() != b.count() ? Integer.compare(b.count(), a.count()) : a.item().compareTo(b.item()));
        return new LogisticsPayloads.NetworkStats(containerId, n.ports.size(), la, la + lFlying, ca, ca + cFlying, lines);
    }

    /** Items in the network: the contents of its provider, storage and buffer chests (Factorio's count). */
    public Map<String, Integer> networkItems(ServerLevel level, Network n) {
        Map<String, Integer> items = new HashMap<>();
        for (LogisticChestBlockEntity be : chestsIn(level, n)) {
            if (be.mode().countsInNetwork()) be.counts().forEach((k, v) -> items.merge(k, v, Integer::sum));
        }
        return items;
    }
}
