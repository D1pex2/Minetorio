package dev.minetorio.core.oil;

import dev.minetorio.core.data.OilData;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.recipe.Ingredient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OilDataTest {
    @Test void machineOnlyRecipesNeverBecomeAutomaticHandcraftIntermediates() {
        var queue=new dev.minetorio.core.handcraft.HandcraftQueue(Phase1Data.recipes());
        var inv=new dev.minetorio.core.item.Inventory() {
            public int count(String item) { return 10000; }
            public int remove(String item,int n) { return n; }
            public int add(String item,int n) { return n; }
        };
        assertEquals(0,queue.maxCraftable("engine_unit",inv,id->true));
        for(var r:OilData.recipes()) if(r.kind()==OilRecipe.MachineKind.ASSEMBLER)
            assertEquals(0,queue.maxCraftable(r.id(),inv,id->true),r.id());
    }
    @Test void factorioNormalRecipesAndTimes() {
        var basic=OilData.recipe("basic_oil_processing");
        assertEquals(100,basic.ticks());
        assertEquals(100,basic.fluidInputs().get("crude_oil"));
        assertEquals(45,basic.fluidOutputs().get("petroleum_gas"));
        assertEquals(90,OilData.recipe("coal_liquefaction").fluidOutputs().get("heavy_oil"));
        assertEquals(2,OilData.recipe("explosives").itemOutputs().getFirst().count());
        assertEquals(80,OilData.recipe("explosives").ticks());
        assertEquals(20,OilData.recipe("battery").fluidInputs().get("sulfuric_acid"));
        assertEquals(40,OilData.recipe("express_underground_belt").fluidInputs().get("lubricant"));
    }

    @Test void solarPanelAndPersonalRoboportMatchFactorio11110Recipes() {
        var solarPanel=OilData.itemRecipes().stream().filter(recipe->recipe.id().equals("solar_panel")).findFirst().orElseThrow();
        var personalRoboport=OilData.itemRecipes().stream().filter(recipe->recipe.id().equals("personal_roboport")).findFirst().orElseThrow();
        assertAll(
            () -> assertAll("solar_panel",
                () -> assertEquals(List.of(new Ingredient("minetorio:copper_plate",5),new Ingredient("minetorio:steel_plate",5),new Ingredient("minetorio:electronic_circuit",15)),solarPanel.inputs()),
                () -> assertEquals(200,solarPanel.ticks()),
                () -> assertEquals(List.of(new Ingredient("minetorio:solar_panel",1)),solarPanel.outputs())),
            () -> assertAll("personal_roboport",
                () -> assertEquals(List.of(new Ingredient("minetorio:advanced_circuit",10),new Ingredient("minetorio:battery",45),new Ingredient("minetorio:iron_gear_wheel",40),new Ingredient("minetorio:steel_plate",20)),personalRoboport.inputs()),
                () -> assertEquals(200,personalRoboport.ticks()),
                () -> assertEquals(List.of(new Ingredient("minetorio:personal_roboport",1)),personalRoboport.outputs())));
    }

    @Test void realRobotChainAndSteelAreLockedBehindResearch() {
        var book=Phase1Data.recipes();
        assertTrue(book.get("construction_robot").orElseThrow().inputs().stream().anyMatch(i->i.item().equals("minetorio:flying_robot_frame")));
        assertEquals(320,book.get("steel_plate").orElseThrow().ticks());
        assertFalse(Phase1Data.startRecipes().contains("steel_plate"));
        assertTrue(Phase1Data.techTree().get("construction_robotics").orElseThrow().packsPerUnit().containsKey("minetorio:chemical_science_pack"));
        for(var fluid:dev.minetorio.core.fluid.FluidData.fluids()) {
            if(fluid.id().equals("steam")) continue;
            assertEquals(50,OilData.recipe("fill_"+fluid.id()+"_barrel").fluidInputs().get(fluid.id()));
            assertEquals(50,OilData.recipe("empty_"+fluid.id()+"_barrel").fluidOutputs().get(fluid.id()));
        }
        assertNull(OilData.recipe("fill_steam_barrel"));
        assertNull(OilData.recipe("empty_steam_barrel"));
        assertEquals(dev.minetorio.core.recipe.RecipeCategory.ASSEMBLING,book.get("electric_engine_unit").orElseThrow().category());
        assertEquals(dev.minetorio.core.recipe.RecipeCategory.ASSEMBLING,book.get("fill_water_barrel").orElseThrow().category());
    }
}
