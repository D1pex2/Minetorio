package dev.minetorio.core.research;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.*;
import org.junit.jupiter.api.Test;

class ResearchTest {
    private static final String R = "minetorio:automation_science_pack";
    private static final String G = "minetorio:logistic_science_pack";

    private ResearchState state() { return new ResearchState(Phase1Data.techTree(), Phase1Data.startRecipes()); }

    private LabLogic lab() { return new LabLogic(1.0, Units.kw(60)); }

    @Test void cannotResearchWithoutPrereqs() {
        ResearchState s = state();
        assertFalse(s.canResearch("automation_2"));
        assertThrows(IllegalStateException.class, () -> s.setCurrent("automation_2"));
        assertTrue(s.canResearch("automation"));
    }

    @Test void completingUnlocksRecipes() {
        ResearchState s = state();
        assertFalse(s.isRecipeUnlocked("assembling_machine_1"));
        s.setCurrent("automation");
        for (int i = 0; i < 10; i++) s.completeUnit();
        assertTrue(s.isResearched("automation"));
        assertTrue(s.isRecipeUnlocked("assembling_machine_1"));
        assertTrue(s.isRecipeUnlocked("long_handed_inserter"));
        assertTrue(s.current().isEmpty());
        assertTrue(s.canResearch("fast_inserter"));
        s.completeUnit(); // no current: no-op
        assertEquals(0, s.unitsDone());
    }

    @Test void switchingKeepsPartialProgress() {
        ResearchState s = state();
        s.setCurrent("automation");
        for (int i = 0; i < 4; i++) s.completeUnit();
        s.setCurrent("logistics");
        assertEquals(0, s.unitsDone());
        s.completeUnit();
        s.setCurrent("automation");
        assertEquals(4, s.unitsDone());
        assertEquals(Map.of("automation", 4, "logistics", 1), s.progressById());

        ResearchState t = state();
        t.load(Set.of(), "automation", s.progressById());
        assertEquals(4, t.unitsDone());
    }

    @Test void labConsumesPacksPerUnit() {
        ResearchState s = state();
        s.setCurrent("automation");
        LabLogic l = lab();
        assertEquals(2, l.insertPack(R, 2, true));
        for (int i = 0; i < 400; i++) l.tick(s, 1.0);
        assertEquals(2, s.unitsDone());
        assertTrue(l.packs().isEmpty());
        assertEquals(0, l.powerDemand());
    }

    @Test void labTimingAndPower() {
        ResearchState s = state();
        s.setCurrent("automation");
        LabLogic l = lab();
        l.insertPack(R, 2, true);
        for (int i = 0; i < 199; i++) l.tick(s, 1.0);
        assertEquals(0, s.unitsDone());
        assertEquals(Units.kw(60), l.powerDemand());
        l.tick(s, 1.0);
        assertEquals(1, s.unitsDone());
    }

    @Test void labWaitsWithoutPacks() {
        ResearchState s = state();
        s.setCurrent("automation");
        LabLogic l = lab();
        for (int i = 0; i < 300; i++) l.tick(s, 1.0);
        assertEquals(0, s.unitsDone());
        assertEquals(0, l.powerDemand());
    }

    @Test void halfSatisfactionIsHalfSpeed() {
        ResearchState s = state();
        s.setCurrent("automation");
        LabLogic l = lab();
        l.insertPack(R, 1, true);
        for (int i = 0; i < 399; i++) l.tick(s, 0.5);
        assertEquals(0, s.unitsDone());
        l.tick(s, 0.5);
        assertEquals(1, s.unitsDone());
    }

    @Test void needsEveryPackOfTechnology() {
        ResearchState s = state();
        s.load(Set.of("automation", "logistic_science_pack"), "automation_2", Map.of());
        LabLogic l = lab();
        l.insertPack(R, 1, true);
        for (int i = 0; i < 20; i++) l.tick(s, 1.0);
        assertEquals(0, l.powerDemand());
        l.insertPack(G, 1, true);
        l.tick(s, 1.0);
        assertTrue(l.packs().isEmpty());
        assertEquals(Units.kw(60), l.powerDemand());
    }

    @Test void packLimitsAndFilter() {
        LabLogic l = lab();
        assertEquals(0, l.insertPack("minetorio:iron_plate", 5, false));
        assertEquals(2, l.insertPack(R, 5, true));
        assertEquals(0, l.insertPack(R, 1, true));
        assertEquals(62, l.insertPack(R, 100, false));
        assertEquals(64, l.packs().get(R));
    }

    @Test void changingTechAbortsUnit() {
        ResearchState s = state();
        s.setCurrent("automation");
        LabLogic l = lab();
        l.insertPack(R, 2, true);
        for (int i = 0; i < 100; i++) l.tick(s, 1.0);
        s.setCurrent("logistics");
        l.tick(s, 1.0); // aborts without refund, starts anew for logistics (15 s = 300 ticks)
        assertTrue(l.packs().isEmpty());
        for (int i = 0; i < 298; i++) l.tick(s, 1.0);
        assertEquals(0, s.unitsDone());
        l.tick(s, 1.0);
        assertEquals(1, s.unitsDone());
    }

    @Test void allTreeRecipesExist() {
        for (Technology t : Phase1Data.techTree().all())
            for (String r : t.unlocksRecipes())
                assertTrue(Phase1Data.recipes().get(r).isPresent(), r);
    }

    @Test void everyLockedRecipeHasTech() {
        Set<String> unlocked = new HashSet<>();
        for (Technology t : Phase1Data.techTree().all()) unlocked.addAll(t.unlocksRecipes());
        for (var r : Phase1Data.recipes().all()) {
            if (r.category() != RecipeCategory.CRAFTING) continue;
            if (Phase1Data.startRecipes().contains(r.id()) || r.id().startsWith("express_")) continue;
            assertTrue(unlocked.contains(r.id()), r.id());
        }
    }

    @Test void packRoomMatchesInsertLimits() {
        LabLogic l = lab();
        assertEquals(0, l.packRoom("minetorio:iron_plate", true));
        assertEquals(2, l.packRoom(R, true));
        assertEquals(64, l.packRoom(G, false));
        l.insertPack(R, 1, true);
        assertEquals(1, l.packRoom(R, true));
        assertEquals(63, l.packRoom(R, false));
        l.insertPack(R, 10, false);
        assertEquals(0, l.packRoom(R, true));
        assertEquals(53, l.packRoom(R, false));
    }

    @Test void labLoadRestoresUnitInProgress() {
        ResearchState s = state();
        s.setCurrent("automation");
        LabLogic l = lab();
        l.insertPack(R, 3, false);
        for (int i = 0; i < 50; i++) l.tick(s, 1.0);
        assertTrue(l.isWorking());
        assertEquals("automation", l.unitTech());
        LabLogic back = lab();
        back.load(l.packs(), l.unitTech(), l.progress());
        assertEquals(l.packs(), back.packs());
        assertTrue(back.isWorking());
        assertEquals(Units.kw(60), back.powerDemand());
        for (int i = 0; i < 149; i++) back.tick(s, 1.0);
        assertEquals(0, s.unitsDone());
        back.tick(s, 1.0); // the unit finishes after 200 ticks in total, no new packs taken for it
        assertEquals(1, s.unitsDone());
        assertEquals(Map.of(R, 2), back.packs());

        LabLogic idle = lab();
        idle.load(Map.of(R, 5, "minetorio:iron_plate", 3, G, 0), null, 0.7);
        assertFalse(idle.isWorking());
        assertEquals(Map.of(R, 5), idle.packs()); // unknown items and empty counts dropped
        assertEquals(0, idle.progress());
    }
}
