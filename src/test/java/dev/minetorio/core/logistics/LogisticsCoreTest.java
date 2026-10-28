package dev.minetorio.core.logistics;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.logistics.LogisticPlanner.Delivery;
import dev.minetorio.core.logistics.LogisticPlanner.Node;
import dev.minetorio.core.logistics.LogisticPlanner.Role;
import dev.minetorio.core.robot.Cell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogisticsCoreTest {
    private static final String PLATE = "minetorio:iron_plate";
    private static final String GEAR = "minetorio:iron_gear_wheel";
    private static final int U = LogisticStats.UNLIMITED;
    /** Every storage chest has room for 100 of anything. */
    private static final LogisticPlanner.Space ROOMY = (n, item) -> 100;

    private static Node node(String id, Role role, int x, Map<String, Integer> contents, LogisticRequest... requests) {
        return new Node(id, role, new Cell(x, 0, 0), contents, List.of(requests), null, false);
    }

    private static Node storage(String id, int x, String filter, Map<String, Integer> contents) {
        return new Node(id, Role.STORAGE, new Cell(x, 0, 0), contents, List.of(), filter, false);
    }

    // --- numbers ----------------------------------------------------------------------------------

    @Test
    void factorioNumbers() {
        assertEquals(0.15, LogisticStats.LOGISTIC_SPEED_BLOCKS_PER_TICK, 1e-9); // 3 tiles/s
        assertEquals(1, LogisticStats.CARGO);
        assertEquals(50, 2 * LogisticStats.LOGISTIC_RADIUS);
        assertEquals(30, 2 * LogisticStats.PERSONAL_CONSTRUCTION_RADIUS);
        assertEquals(10, LogisticStats.PERSONAL_ROBOT_LIMIT);
    }

    @Test
    void chestModes() {
        assertFalse(LogisticMode.REQUESTER.countsInNetwork());
        assertTrue(LogisticMode.BUFFER.countsInNetwork());
        assertEquals(1, LogisticMode.STORAGE.requestSlots());
        assertEquals(0, LogisticMode.PASSIVE_PROVIDER.requestSlots());
        assertEquals(LogisticStats.CHEST_REQUEST_SLOTS, LogisticMode.REQUESTER.requestSlots());
        assertEquals(Role.BUFFER, LogisticMode.BUFFER.role());
    }

    // --- networks ---------------------------------------------------------------------------------

    private static LogisticArea area(double x, double z) {
        return new LogisticArea(x, 64.5, z, LogisticStats.LOGISTIC_RADIUS);
    }

    @Test
    void roboportsWhoseAreasTouchOrOverlapFormOneNetwork() {
        List<LogisticArea> a = List.of(area(0, 0), area(30, 0), area(80, 0), area(130, 0), area(500, 500));
        // 0-1 overlap, 1-2 touch exactly (gap 50 = 25 + 25), 2-3 touch, 4 alone
        assertEquals(List.of(List.of(0, 1, 2, 3), List.of(4)), LogisticNetworks.group(a));
    }

    @Test
    void separatedAreasAreSeparateNetworks() {
        List<LogisticArea> a = List.of(area(0, 0), area(51, 0), area(0, 51), area(51, 51));
        assertEquals(4, LogisticNetworks.group(a).size());
        // diagonal neighbours: x touches but z does not
        List<LogisticArea> b = List.of(area(0, 0), area(50, 60));
        assertEquals(2, LogisticNetworks.group(b).size());
    }

    @Test
    void networkAtAPoint() {
        List<LogisticArea> a = List.of(area(0, 0), area(30, 0), area(200, 0));
        var g = LogisticNetworks.group(a);
        assertEquals(0, LogisticNetworks.networkAt(g, a, 50, 64, 0));
        assertEquals(1, LogisticNetworks.networkAt(g, a, 210, 64, 0));
        assertEquals(-1, LogisticNetworks.networkAt(g, a, 100, 64, 0));
        assertTrue(area(0, 0).contains(new Cell(24, 64, -25)));
        assertFalse(area(0, 0).contains(new Cell(25, 64, 0)));
    }

    // --- request math -----------------------------------------------------------------------------

    @Test
    void deficitCountsWhatIsOnItsWay() {
        assertEquals(7, RequestMath.deficit(10, 3, 0));
        assertEquals(2, RequestMath.deficit(10, 3, 5));
        assertEquals(0, RequestMath.deficit(10, 12, 0));
        assertEquals(0, RequestMath.deficit(0, 0, 0));
    }

    @Test
    void trashIsAboveTheMaximum() {
        assertEquals(5, RequestMath.trash(10, 15, 0));
        assertEquals(2, RequestMath.trash(10, 15, 3));
        assertEquals(0, RequestMath.trash(10, 9, 0));
        assertEquals(0, RequestMath.trash(U, 1000, 0));
    }

    @Test
    void requestMaxNeverBelowMin() {
        LogisticRequest r = new LogisticRequest(PLATE, 50, 10);
        assertEquals(50, r.max());
        assertFalse(new LogisticRequest(PLATE, 1, U).hasMax());
        assertThrows(IllegalArgumentException.class, () -> new LogisticRequest("", 1, 1));
    }

    @Test
    void requestTableKeepsEachItemOnce() {
        RequestTable t = new RequestTable(3);
        int v = t.version();
        t.set(0, new LogisticRequest(PLATE, 10, U));
        t.set(2, new LogisticRequest(PLATE, 20, U));
        assertNull(t.get(0));
        assertEquals(20, t.get(2).min());
        assertTrue(t.version() > v);
        assertTrue(t.setCounts(2, 5, 8));
        assertEquals(new LogisticRequest(PLATE, 5, 8), t.get(2));
        assertFalse(t.setCounts(0, 1, 1));
        assertFalse(t.set(3, null));
        assertEquals(List.of(t.get(2)), t.requests());
    }

    // --- planning ---------------------------------------------------------------------------------

    @Test
    void requesterGetsItemsFromAPassiveProviderOneRobotPerItem() {
        Node pp = node("pp", Role.PASSIVE_PROVIDER, 0, Map.of(PLATE, 10));
        Node req = node("req", Role.REQUESTER, 5, Map.of(), new LogisticRequest(PLATE, 3, U));
        DeliveryLedger ledger = new DeliveryLedger();
        List<Delivery> plan = LogisticPlanner.plan(List.of(pp, req), ledger, 10, 1, ROOMY);
        assertEquals(3, plan.size());
        for (Delivery d : plan) assertEquals(new Delivery("pp", "req", PLATE, 1), d);
        assertEquals(3, ledger.incoming("req", PLATE));
        assertEquals(3, ledger.outgoing("pp", PLATE));
        // planned again while the robots fly: nothing more to send
        assertTrue(LogisticPlanner.plan(List.of(pp, req), ledger, 10, 1, ROOMY).isEmpty());
    }

    @Test
    void robotsLimitTrips() {
        Node pp = node("pp", Role.PASSIVE_PROVIDER, 0, Map.of(PLATE, 10));
        Node req = node("req", Role.REQUESTER, 5, Map.of(), new LogisticRequest(PLATE, 8, U));
        assertEquals(2, LogisticPlanner.plan(List.of(pp, req), new DeliveryLedger(), 2, 1, ROOMY).size());
        assertEquals(1, LogisticPlanner.plan(List.of(pp, req), new DeliveryLedger(), 1, 4, ROOMY).size());
        assertEquals(4, LogisticPlanner.plan(List.of(pp, req), new DeliveryLedger(), 1, 4, ROOMY).get(0).count());
    }

    @Test
    void activeProviderBeforeNearerStorageAndPassive() {
        Node pp = node("pp", Role.PASSIVE_PROVIDER, 1, Map.of(PLATE, 5));
        Node st = storage("st", 2, null, Map.of(PLATE, 5));
        Node ap = node("ap", Role.ACTIVE_PROVIDER, 40, Map.of(PLATE, 1));
        Node req = node("req", Role.REQUESTER, 0, Map.of(), new LogisticRequest(PLATE, 2, U));
        List<Delivery> plan = LogisticPlanner.plan(List.of(pp, st, ap, req), new DeliveryLedger(), 2, 1, ROOMY);
        assertEquals("ap", plan.get(0).from());
        assertEquals("pp", plan.get(1).from()); // then the nearest of storage/passive
    }

    @Test
    void buffersSupplyRequestersOnlyWhenAsked() {
        Node buf = node("buf", Role.BUFFER, 1, Map.of(PLATE, 5));
        Node req = node("req", Role.REQUESTER, 0, Map.of(), new LogisticRequest(PLATE, 1, U));
        assertTrue(LogisticPlanner.plan(List.of(buf, req), new DeliveryLedger(), 5, 1, ROOMY).isEmpty());
        Node req2 = new Node("req", Role.REQUESTER, new Cell(0, 0, 0), Map.of(), List.of(new LogisticRequest(PLATE, 1, U)), null, true);
        assertEquals("buf", LogisticPlanner.plan(List.of(buf, req2), new DeliveryLedger(), 5, 1, ROOMY).get(0).from());
        // players always take from buffers
        Node player = node("p", Role.PLAYER, 0, Map.of(), new LogisticRequest(PLATE, 1, U));
        assertEquals("buf", LogisticPlanner.plan(List.of(buf, player), new DeliveryLedger(), 5, 1, ROOMY).get(0).from());
    }

    @Test
    void buffersFillFromProvidersButNotFromBuffersAndAfterRequesters() {
        Node pp = node("pp", Role.PASSIVE_PROVIDER, 0, Map.of(PLATE, 1));
        Node other = node("other", Role.BUFFER, 1, Map.of(PLATE, 9));
        Node buf = node("buf", Role.BUFFER, 2, Map.of(), new LogisticRequest(PLATE, 5, U));
        Node req = node("req", Role.REQUESTER, 50, Map.of(), new LogisticRequest(PLATE, 1, U));
        List<Delivery> plan = LogisticPlanner.plan(List.of(pp, other, buf, req), new DeliveryLedger(), 5, 1, ROOMY);
        // the requester (listed last) is served first and takes the only provider plate; the buffer gets nothing
        assertEquals(List.of(new Delivery("pp", "req", PLATE, 1)), plan);
    }

    @Test
    void activeProviderEmptiesIntoStoragePreferringFilterThenSameItemThenEmpty() {
        Node ap = node("ap", Role.ACTIVE_PROVIDER, 0, Map.of(PLATE, 1, GEAR, 1));
        Node empty = storage("empty", 1, null, Map.of());
        Node holding = storage("holding", 30, null, Map.of(GEAR, 3));
        Node filtered = storage("filtered", 40, PLATE, Map.of());
        List<Delivery> plan = LogisticPlanner.plan(List.of(ap, empty, holding, filtered), new DeliveryLedger(), 5, 1, ROOMY);
        Map<String, String> to = new HashMap<>();
        for (Delivery d : plan) to.put(d.item(), d.to());
        assertEquals("filtered", to.get(PLATE));
        assertEquals("holding", to.get(GEAR));
        // a filtered chest never takes other items; with no other storage, gears stay
        List<Delivery> only = LogisticPlanner.plan(List.of(ap, filtered), new DeliveryLedger(), 5, 1, ROOMY);
        assertEquals(List.of(new Delivery("ap", "filtered", PLATE, 1)), only);
    }

    @Test
    void storageRoomIsRespected() {
        Node ap = node("ap", Role.ACTIVE_PROVIDER, 0, Map.of(PLATE, 5));
        Node st = storage("st", 1, null, Map.of());
        List<Delivery> plan = LogisticPlanner.plan(List.of(ap, st), new DeliveryLedger(), 10, 1, (n, item) -> 2);
        assertEquals(2, plan.size());
    }

    @Test
    void requestsComeBeforeEmptyingActiveProviders() {
        Node ap = node("ap", Role.ACTIVE_PROVIDER, 0, Map.of(PLATE, 2));
        Node st = storage("st", 1, null, Map.of());
        Node req = node("req", Role.REQUESTER, 9, Map.of(), new LogisticRequest(PLATE, 1, U));
        List<Delivery> plan = LogisticPlanner.plan(List.of(ap, st, req), new DeliveryLedger(), 10, 1, ROOMY);
        assertEquals(List.of(new Delivery("ap", "req", PLATE, 1), new Delivery("ap", "st", PLATE, 1)), plan);
    }

    @Test
    void playerTrashGoesToStorage() {
        Node player = node("p", Role.PLAYER, 0, Map.of(PLATE, 13), new LogisticRequest(PLATE, 0, 10));
        Node st = storage("st", 3, null, Map.of());
        DeliveryLedger ledger = new DeliveryLedger();
        List<Delivery> plan = LogisticPlanner.plan(List.of(player, st), ledger, 10, 1, ROOMY);
        assertEquals(3, plan.size());
        assertEquals(new Delivery("p", "st", PLATE, 1), plan.get(0));
        assertEquals(3, ledger.outgoing("p", PLATE));
        // the player is never a source for requests
        Node req = node("req", Role.REQUESTER, 1, Map.of(), new LogisticRequest(PLATE, 1, U));
        assertTrue(LogisticPlanner.plan(List.of(player, req), new DeliveryLedger(), 10, 1, ROOMY).isEmpty());
    }

    @Test
    void cancelUndoesTheLedger() {
        Node pp = node("pp", Role.PASSIVE_PROVIDER, 0, Map.of(PLATE, 10));
        Node req = node("req", Role.REQUESTER, 5, Map.of(), new LogisticRequest(PLATE, 1, U));
        DeliveryLedger ledger = new DeliveryLedger();
        List<Delivery> plan = new ArrayList<>(LogisticPlanner.plan(List.of(pp, req), ledger, 10, 1, ROOMY));
        LogisticPlanner.cancel(ledger, plan.get(0));
        assertTrue(ledger.isEmpty());
    }
}
