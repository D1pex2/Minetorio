package dev.minetorio.content.nbt;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.handcraft.HandcraftQueue;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.item.Inventory;
import dev.minetorio.core.machine.Burner;
import dev.minetorio.core.machine.CraftingMachine;
import dev.minetorio.core.recipe.RecipeBook;
import dev.minetorio.core.research.LabLogic;
import dev.minetorio.core.research.ResearchState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreNbtTest {
    @Test
    void beltLaneRoundTrip() {
        BeltLane lane = new BeltLane(1.0);
        assertTrue(lane.insertAt("minetorio:iron_plate", 0.0));
        assertTrue(lane.insertAt("minetorio:copper_plate", 0.4));
        assertTrue(lane.insertAt("minetorio:iron_gear_wheel", 0.9));

        BeltLane back = CoreNbt.readLane(CoreNbt.writeLane(lane));

        assertEquals(3, back.items().size());
        assertEquals(lane.items(), back.items());
        assertEquals(lane.length(), back.length());
    }

    @Test
    void emptyLaneRoundTrip() {
        BeltLane back = CoreNbt.readLane(CoreNbt.writeLane(new BeltLane(0.5)));
        assertTrue(back.isEmpty());
        assertEquals(0.5, back.length());
    }

    @Test
    void fluidRoundTrip() {
        FluidBox box = new FluidBox(200);
        box.insert("steam", 37.5);
        FluidBox back = CoreNbt.readFluid(CoreNbt.writeFluid(box));
        assertEquals("steam", back.fluid());
        assertEquals(37.5, back.amount());
        assertEquals(200, back.capacity());

        FluidBox empty = CoreNbt.readFluid(CoreNbt.writeFluid(new FluidBox(100)));
        assertNull(empty.fluid());
        assertEquals(0, empty.amount());
        assertEquals(100, empty.capacity());
    }

    @Test
    void fluidLoadsIntoExistingBox() {
        FluidBox saved = new FluidBox(200);
        saved.insert("water", 12.25);
        FluidBox target = new FluidBox(200);
        target.insert("steam", 50);
        CoreNbt.loadFluidInto(target, CoreNbt.writeFluid(saved));
        assertEquals("water", target.fluid());
        assertEquals(12.25, target.amount());

        CoreNbt.loadFluidInto(target, CoreNbt.writeFluid(new FluidBox(200)));
        assertNull(target.fluid());
        assertEquals(0, target.amount());
    }

    @Test
    void countsRoundTrip() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("minetorio:iron_plate", 12);
        counts.put("minetorio:copper_cable", 0);
        assertEquals(counts, CoreNbt.readCounts(CoreNbt.writeCounts(counts)));
        assertEquals(Map.of(), CoreNbt.readCounts(CoreNbt.writeCounts(Map.of())));
    }

    @Test
    void genericMapRoundTripKeepsTypes() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("a", 1);
        inner.put("b", 2.5);
        List<Object> listOfMaps = new ArrayList<>();
        listOfMaps.add(new LinkedHashMap<>(Map.of("x", 3)));
        listOfMaps.add(new LinkedHashMap<String, Object>());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "hello");
        m.put("i", 42);
        m.put("l", 1L << 40);
        m.put("d", 0.125);
        m.put("strings", new ArrayList<>(List.of("p", "q")));
        m.put("ints", new ArrayList<>(List.of(1, 0, 1)));
        m.put("empty", new ArrayList<>());
        m.put("maps", listOfMaps);
        m.put("inner", inner);

        Map<String, Object> back = CoreNbt.read(CoreNbt.write(m));

        assertEquals(m, back);
        assertInstanceOf(Integer.class, back.get("i"));
        assertInstanceOf(Long.class, back.get("l"));
        assertInstanceOf(Double.class, back.get("d"));
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> CoreNbt.write(Map.of("bad", new Object())));
        assertThrows(IllegalArgumentException.class, () -> CoreNbt.write(Map.of("mixed", List.of(1, "x"))));
    }

    @Test
    void handcraftQueueSaveRoundTrips() {
        RecipeBook book = Phase1Data.recipes();
        MapInventory inv = new MapInventory();
        inv.add("minetorio:iron_plate", 10);
        inv.add("minetorio:copper_plate", 10);
        HandcraftQueue q = new HandcraftQueue(book);
        assertTrue(q.enqueue("inserter", 1, inv, id -> true));
        for (int i = 0; i < 23; i++) q.tick(inv);

        Map<String, Object> saved = q.save();
        Map<String, Object> back = CoreNbt.read(CoreNbt.write(saved));
        assertEquals(saved, back);
        HandcraftQueue q2 = HandcraftQueue.load(book, back);
        assertEquals(q.entries(), q2.entries());
        assertEquals(saved, q2.save());
    }

    private static final class MapInventory implements Inventory {
        final Map<String, Integer> m = new HashMap<>();

        @Override public int count(String item) { return m.getOrDefault(item, 0); }

        @Override public int remove(String item, int n) {
            int r = Math.min(n, count(item));
            m.put(item, count(item) - r);
            return r;
        }

        @Override public int add(String item, int n) {
            m.merge(item, n, Integer::sum);
            return n;
        }
    }

    @Test
    void craftingMachineRoundTrip() {
        RecipeBook book = Phase1Data.recipes();
        CraftingMachine m = new CraftingMachine(0.5, 3750);
        m.setRecipe(book.get("iron_gear_wheel").orElseThrow());
        m.insertInput("minetorio:iron_plate", 5, false);
        for (int i = 0; i < 25; i++) m.tick(1.0);

        CraftingMachine back = new CraftingMachine(0.5, 3750);
        CoreNbt.loadMachineInto(back, CoreNbt.writeMachine(m), book);

        assertEquals(m.recipe(), back.recipe());
        assertEquals(m.inputs(), back.inputs());
        assertEquals(m.outputs(), back.outputs());
        assertEquals(m.isCrafting(), back.isCrafting());
        assertEquals(m.progress(), back.progress());
    }

    @Test
    void craftingMachineUnknownRecipeLoadsIdle() {
        CraftingMachine m = new CraftingMachine(0.5, 3750);
        m.setRecipe(Phase1Data.recipes().get("iron_gear_wheel").orElseThrow());
        m.insertInput("minetorio:iron_plate", 3, false);
        CraftingMachine back = new CraftingMachine(0.5, 3750);
        CoreNbt.loadMachineInto(back, CoreNbt.writeMachine(m), new RecipeBook(List.of()));
        assertNull(back.recipe());
        assertEquals(Map.of("minetorio:iron_plate", 3), back.drainInputs()); // nothing lost
    }

    @Test
    void unknownRecipeReturnsInputsOfCraftInProgress() {
        CraftingMachine m = new CraftingMachine(0.5, 3750);
        m.setRecipe(Phase1Data.recipes().get("iron_gear_wheel").orElseThrow());
        m.insertInput("minetorio:iron_plate", 5, false);
        m.tick(1.0); // craft running: 2 plates consumed, 3 stored
        assertTrue(m.isCrafting());
        CraftingMachine back = new CraftingMachine(0.5, 3750);
        CoreNbt.loadMachineInto(back, CoreNbt.writeMachine(m), new RecipeBook(List.of()));
        assertFalse(back.isCrafting());
        assertEquals(Map.of("minetorio:iron_plate", 5), back.drainInputs());
    }

    @Test
    void burnerRoundTrip() {
        Burner b = new Burner(4500, Phase1Data.fuelValues());
        b.insertFuel("minecraft:coal", 3, false);
        b.draw();
        Burner back = new Burner(4500, Phase1Data.fuelValues());
        CoreNbt.loadBurnerInto(back, CoreNbt.writeBurner(b));
        assertEquals(b.fuel(), back.fuel());
        assertEquals(b.remainingJ(), back.remainingJ());
    }

    @Test
    void labRoundTrip() {
        ResearchState s = new ResearchState(Phase1Data.techTree(), Phase1Data.startRecipes());
        s.setCurrent("automation");
        LabLogic lab = new LabLogic(1.0, 3000);
        lab.insertPack("minetorio:automation_science_pack", 4, false);
        for (int i = 0; i < 30; i++) lab.tick(s, 1.0);
        LabLogic back = new LabLogic(1.0, 3000);
        CoreNbt.loadLabInto(back, CoreNbt.writeLab(lab));
        assertEquals(lab.packs(), back.packs());
        assertEquals(lab.unitTech(), back.unitTech());
        assertEquals(Map.of("minetorio:automation_science_pack", 1), back.unitPacks());
        assertEquals(lab.progress(), back.progress());
        assertTrue(back.isWorking());

        LabLogic idle = new LabLogic(1.0, 3000);
        LabLogic idleBack = new LabLogic(1.0, 3000);
        CoreNbt.loadLabInto(idleBack, CoreNbt.writeLab(idle));
        assertFalse(idleBack.isWorking());
        assertTrue(idleBack.packs().isEmpty());
    }
}
