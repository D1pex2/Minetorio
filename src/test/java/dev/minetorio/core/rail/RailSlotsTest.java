package dev.minetorio.core.rail;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import dev.minetorio.core.data.RailData.StockKind;
import static dev.minetorio.core.rail.RailGeometry.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RailSlotsTest {
    @Test void authorityAndDirtyNotifications() {
        AtomicInteger changes = new AtomicInteger();
        RailwayState state = new RailwayState(key -> 5, changes::incrementAndGet);
        var cargo = state.createCar(UUID.randomUUID(), StockKind.CARGO, false);
        var slots = cargo.cargo().orElseThrow();
        changes.set(0);
        slots.insert(0, "minetorio:pistol", 2, true);
        assertEquals(0, changes.get());
        slots.insert(0, "minetorio:pistol", 2, false);
        assertEquals(1, changes.get());
        slots.setBar(0);
        slots.setFilter(0, Optional.of("minetorio:pistol"));
        slots.extract(0, 1, false);
        cargo.setHp(500);
        assertEquals(5, changes.get());
        var fluid = state.createCar(UUID.randomUUID(), StockKind.FLUID, false);
        changes.set(0);
        assertEquals(25000, fluid.insertFluid("water", 30000));
        assertEquals(0, fluid.insertFluid("steam", 1));
        assertEquals(100, fluid.extractFluid(100));
        assertEquals(2, changes.get());
        var loco = state.createCar(UUID.randomUUID(), StockKind.LOCOMOTIVE, true);
        changes.set(0);
        assertEquals(3, loco.fuel().orElseThrow().snapshot().size());
        assertEquals(0, loco.fuel().orElseThrow().insert(0, "minetorio:pistol", 1, false));
        assertEquals(1, loco.fuel().orElseThrow().insert(2, "minetorio:coal", 1, false));
        loco.setActiveFuel("minetorio:coal", new java.math.BigDecimal("123.45"));
        assertEquals(2, changes.get());
        assertTrue(loco.cargo().isEmpty());
        assertTrue(cargo.fuel().isEmpty());
        assertTrue(fluid.cargo().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> state.cars().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.trains().clear());
    }
    @Test void graphSnapshotsKeepRevisionAndCannotBypassAuthority() {
        AtomicInteger changes = new AtomicInteger();
        RailwayState state = new RailwayState(key -> 5, changes::incrementAndGet);
        Piece first = new Piece(UUID.randomUUID(), new Point(0, 0, 0), Shape.STRAIGHT, 0, false);
        Piece next = new Piece(UUID.randomUUID(), new Point(0, 0, -2), Shape.STRAIGHT, 0, false);
        state.replaceRails(List.of(first));
        var snapshot = state.graph();
        state.replaceRails(List.of(first, next));
        assertEquals(snapshot.revision() + 1, state.graph().revision());
        assertEquals(2, changes.get());
        assertTrue(snapshot.piece(next.id()).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> state.graph().rebuild(List.of()));
        assertTrue(state.graph().piece(first.id()).isPresent());
        assertEquals(2, changes.get());
        assertThrows(IllegalArgumentException.class, () -> state.replaceRails(List.of(first, first)));
        assertEquals(snapshot.revision() + 1, state.graph().revision());
    }
    @Test void exactCapacityAndIndices() {
        RailSlots slots = new RailSlots(40, key -> 5);
        assertEquals(5, slots.insert(0, "minetorio:pistol", 8, false));
        assertEquals(5, slots.insert(39, "minetorio:pistol", 8, false));
        assertThrows(IndexOutOfBoundsException.class, () -> slots.insert(40, "minetorio:pistol", 1, false));
        assertThrows(IndexOutOfBoundsException.class, () -> slots.get(-1));
        assertEquals(40, slots.snapshot().size());
    }
    @Test void barOnlyBlocksAutomaticInsertion() {
        RailSlots slots = new RailSlots(40, key -> 5);
        slots.setBar(0);
        assertEquals(0, slots.insert(0, "minetorio:pistol", 1, false));
        assertEquals(1, slots.insertManual(0, "minetorio:pistol", 1, false));
        assertEquals(1, slots.extract(0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> slots.setBar(-1));
        assertThrows(IllegalArgumentException.class, () -> slots.setBar(41));
    }
    @Test void nonPositiveCountsDoNotTransfer() {
        RailSlots slots = new RailSlots(1, key -> 5);
        assertEquals(0, slots.insert(0, "minetorio:pistol", -1, false));
        assertEquals(0, slots.insert(0, "minetorio:pistol", 0, false));
        slots.insert(0, "minetorio:pistol", 2, false);
        assertEquals(0, slots.extract(0, -1, false));
        assertEquals(0, slots.extract(0, 0, false));
        assertEquals(2, slots.get(0).count());
    }
    @Test void completeKeysAndComponentLimit() {
        RailSlots slots = new RailSlots(2, key -> key.equals("invalid") ? 0 : 1);
        assertEquals(1, slots.insert(0, "minetorio:pistol#one", 2, false));
        assertEquals(0, slots.insert(0, "minetorio:pistol#one", 1, false));
        assertEquals(0, slots.insert(0, "minetorio:pistol#two", 1, false));
        assertEquals(1, slots.insert(1, "minetorio:pistol#two", 1, false));
        assertEquals(0, slots.insertManual(0, "invalid", 1, false));
        RailSlots merge = new RailSlots(1, key -> 5);
        merge.insert(0, "minetorio:pistol#one", 1, false);
        assertEquals(0, merge.insert(0, "minetorio:pistol#two", 1, false));
    }
    @Test void filtersAndSimulationPreserveContents() {
        RailSlots slots = new RailSlots(1, key -> 5);
        slots.setFilter(0, Optional.of("minetorio:pistol"));
        assertEquals(0, slots.insert(0, "minetorio:coal", 1, false));
        assertEquals(2, slots.insert(0, "minetorio:pistol#one", 2, true));
        assertEquals(0, slots.get(0).count());
        slots.insert(0, "minetorio:pistol#one", 2, false);
        var before = slots.snapshot();
        assertEquals(1, slots.extract(0, 1, true));
        slots.setFilter(0, Optional.of("minetorio:coal"));
        assertEquals(before.getFirst().key(), slots.get(0).key());
        assertEquals(2, slots.get(0).count());
        assertThrows(IllegalArgumentException.class, () -> slots.setFilter(0, Optional.of("minetorio:pistol#one")));
        assertThrows(UnsupportedOperationException.class, () -> slots.snapshot().clear());
    }
}
