package dev.minetorio.core.research;

import static org.junit.jupiter.api.Assertions.*;

import dev.minetorio.core.Units;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.*;
import org.junit.jupiter.api.Test;

class ResearchTest {
    @Test void blueScienceStartsAndRestoresARealAdvancedOilResearchUnit() {
        String blue="minetorio:chemical_science_pack";
        ResearchState research=state();
        research.load(Set.of("chemical_science_pack"),"advanced_oil_processing",Map.of());
        LabLogic original=lab();
        for(String id:List.of("minetorio:automation_science_pack","minetorio:logistic_science_pack",blue))
            assertEquals(1,original.insertPack(id,1,true),id);
        for(int i=0;i<200;i++) original.tick(research,1);
        assertTrue(original.unitPacks().containsKey(blue));
        LabLogic restored=lab();
        restored.load(original.packs(),original.unitTech(),original.unitPacks(),original.progress());
        assertEquals(original.unitPacks(),restored.unitPacks());
        for(int i=0;i<400;i++) restored.tick(research,1);
        assertEquals(1,research.unitsDone());
        assertTrue(restored.packs().isEmpty());
        assertTrue(restored.unitPacks().isEmpty());
    }

    @Test void implementedScienceTypesHaveTheSameInsertionAndReloadRules() {
        LabLogic original=lab();
        for(String id:List.of("chemical","production","utility")) {
            String item="minetorio:"+id+"_science_pack";
            assertEquals(2,original.insertPack(item,3,true),item);
            assertEquals(0,original.insertPack(item,1,true),item);
        }
        LabLogic restored=lab();
        restored.load(original.packs(),null,Map.of(),0);
        assertEquals(original.packs(),restored.packs());
    }
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
        assertTrue(s.canResearch("electronics"));
        assertFalse(s.canResearch("fast_inserter"));
        s.setCurrent("electronics");
        for(int i=0;i<30;i++) s.completeUnit();
        assertTrue(s.canResearch("fast_inserter"));
        s.setCurrent("logistics");
        for(int i=0;i<20;i++) s.completeUnit();
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
        assertEquals(198, l.insertPack(R, 200, false));
        assertEquals(200, l.packs().get(R));
    }

    @Test void changingTechAbortsUnit() {
        ResearchState s = state();
        s.setCurrent("automation");
        LabLogic l = lab();
        l.insertPack(R, 2, true);
        for (int i = 0; i < 100; i++) l.tick(s, 1.0);
        s.setCurrent("logistics");
        l.tick(s, 1.0); // aborts with refund (R23), starts anew for logistics (15 s = 300 ticks)
        assertEquals(Map.of(R, 1), l.packs());
        assertEquals(Map.of(R, 1), l.unitPacks());
        for (int i = 0; i < 298; i++) l.tick(s, 1.0);
        assertEquals(0, s.unitsDone());
        l.tick(s, 1.0);
        assertEquals(1, s.unitsDone());
    }

    @Test void allTreeRecipesExist() {
        for (Technology t : Phase1Data.techTree().all())
            for (String r : t.unlocksRecipes())
                assertTrue(Phase1Data.recipes().get(r).isPresent() || dev.minetorio.core.data.OilData.recipe(r)!=null, r);
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
        assertEquals(200, l.packRoom(G, false));
        l.insertPack(R, 1, true);
        assertEquals(1, l.packRoom(R, true));
        assertEquals(199, l.packRoom(R, false));
        l.insertPack(R, 10, false);
        assertEquals(0, l.packRoom(R, true));
        assertEquals(189, l.packRoom(R, false));
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
        back.load(l.packs(), l.unitTech(), l.unitPacks(), l.progress());
        assertEquals(l.packs(), back.packs());
        assertTrue(back.isWorking());
        assertEquals(Units.kw(60), back.powerDemand());
        for (int i = 0; i < 149; i++) back.tick(s, 1.0);
        assertEquals(0, s.unitsDone());
        back.tick(s, 1.0); // the unit finishes after 200 ticks in total, no new packs taken for it
        assertEquals(1, s.unitsDone());
        assertEquals(Map.of(R, 2), back.packs());

        LabLogic idle = lab();
        idle.load(Map.of(R, 5, "minetorio:iron_plate", 3, G, 0), null, Map.of(G, 1), 0.7);
        assertFalse(idle.isWorking());
        assertEquals(Map.of(R, 5, G, 1), idle.packs()); // unknown items and empty counts dropped; idle unit packs stored
        assertEquals(0, idle.progress());
    }

    private static int total(LabLogic l) {
        int n = 0;
        for (int v : l.packs().values()) n += v;
        for (int v : l.unitPacks().values()) n += v;
        return n;
    }

    @Test void switchingResearchMidUnitKeepsPackTotal() {
        ResearchState s = state();
        s.load(Set.of("automation", "electronics", "steel_processing", "logistic_science_pack"), "automation_2", Map.of());
        LabLogic l = lab();
        l.insertPack(R, 3, false);
        l.insertPack(G, 2, false);
        for (int i = 0; i < 50; i++) l.tick(s, 1.0);
        assertEquals(Map.of(R, 1, G, 1), l.unitPacks());
        assertEquals(5, total(l));
        for (String tech : List.of("logistics", "automation_2", "fast_inserter", "automation_2")) {
            s.setCurrent(tech);
            l.tick(s, 1.0);
            assertEquals(5, total(l), "after switching to " + tech);
        }
        LabLogic copy = lab(); // abort after a reload refunds too
        copy.load(l.packs(), l.unitTech(), l.unitPacks(), l.progress());
        s.setCurrent("logistics");
        copy.tick(s, 0.0);
        assertEquals(5, total(copy));
    }

    @Test void techCompletedElsewhereRefundsUnit() {
        ResearchState s = state();
        s.load(Set.of(), "automation", Map.of("automation", 9));
        LabLogic a = lab();
        LabLogic b = lab();
        a.insertPack(R, 1, true);
        for (int i = 0; i < 100; i++) a.tick(s, 1.0);
        b.insertPack(R, 1, true);
        for (int i = 0; i < 100; i++) { a.tick(s, 1.0); b.tick(s, 1.0); }
        assertTrue(s.isResearched("automation"));
        assertTrue(a.packs().isEmpty());
        b.tick(s, 1.0); // no current: b's unit is aborted and refunded
        assertFalse(b.isWorking());
        assertEquals(Map.of(R, 1), b.packs());
        assertTrue(b.unitPacks().isEmpty());
    }
}
