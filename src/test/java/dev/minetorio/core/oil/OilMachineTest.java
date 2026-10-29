package dev.minetorio.core.oil;

import dev.minetorio.core.data.OilData;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OilMachineTest {
    @Test void refineryBlocksAllInputsWhenAnyOutputIsFull() {
        OilMachine m = new OilMachine(1, 21000);
        m.setRecipe(OilData.recipe("advanced_oil_processing"));
        m.inputFluid("water").insert("water", 50);
        m.inputFluid("crude_oil").insert("crude_oil", 100);
        m.outputFluid("heavy_oil").insert("heavy_oil", 1000);
        assertEquals(0, m.powerDemand());
        m.tick(1);
        assertEquals(100, m.inputFluid("crude_oil").amount());
        assertEquals(50, m.inputFluid("water").amount());
        m.outputFluid("heavy_oil").extract(25);
        for (int i=0;i<100;i++) m.tick(1);
        assertEquals(1000, m.outputFluid("heavy_oil").amount());
        assertEquals(45, m.outputFluid("light_oil").amount());
        assertEquals(55, m.outputFluid("petroleum_gas").amount());
    }

    @Test void chemicalCraftConsumesAtomicallyAndSurvivesSave() {
        OilMachine m = new OilMachine(1, 10500);
        m.setRecipe(OilData.recipe("plastic_bar"));
        m.insertInput("minetorio:coal", 1, false);
        m.tick(1);
        assertEquals(Map.of("minetorio:coal",1), m.inputs());
        m.inputFluid("petroleum_gas").insert("petroleum_gas", 20);
        m.tick(0);
        assertFalse(m.isCrafting());
        m.tick(1);
        assertTrue(m.isCrafting());
        assertFalse(m.setRecipe(OilData.recipe("sulfur")));
        OilMachine loaded = new OilMachine(1,10500);
        loaded.load(m.snapshot());
        for(int i=1;i<20;i++) loaded.tick(1);
        assertEquals(2, loaded.extractOutput("minetorio:plastic_bar",64));
        assertEquals(0, loaded.inputFluid("petroleum_gas").amount());
    }

    @Test void outputObstructedDuringCraftNeverOverflowsOrLosesProducts() {
        OilMachine m = new OilMachine(1, 1);
        m.setRecipe(OilData.recipe("lubricant"));
        m.inputFluid("heavy_oil").insert("heavy_oil",10);
        m.tick(1);
        m.outputFluid("lubricant").insert("lubricant",1000);
        for(int i=1;i<20;i++) m.tick(1);
        assertTrue(m.isCrafting());
        assertEquals(1000,m.outputFluid("lubricant").amount());
        assertEquals(.05,m.progress(),1e-9);
        m.outputFluid("lubricant").extract(10);
        for(int i=1;i<20;i++) m.tick(1);
        assertFalse(m.isCrafting());
        assertEquals(1000,m.outputFluid("lubricant").amount());
    }

    @Test void extractionYieldDecaysToFactorioFloorAndRestoresExactly() {
        OilYield oil = new OilYield(2);
        assertEquals(20, oil.extractCycle());
        assertTrue(oil.yield() < 2);
        OilYield loaded = OilYield.restore(oil.initialYield(), oil.remainingCycles());
        assertEquals(oil.yield(),loaded.yield());
        for(int i=0;i<100000;i++) loaded.extractCycle();
        assertEquals(.4,loaded.yield(),1e-9);
        assertEquals(4,loaded.extractCycle(),1e-9);
        OilYield ordinary = new OilYield(.5);
        for(int i=0;i<100000;i++) ordinary.extractCycle();
        assertEquals(.2,ordinary.yield(),1e-9);
    }

    @Test void idleRecipeSwitchRetainsTypedTankIdentityAndRejectsIncompatibleFluid() {
        OilMachine m=new OilMachine(.75,1);
        m.setRecipe(OilData.recipe("fill_water_barrel"));
        var water=m.inputFluid("water");
        water.insert("water",50);
        assertFalse(m.canSetRecipe(OilData.recipe("electric_engine_unit")));
        assertFalse(m.setRecipe(OilData.recipe("electric_engine_unit")));
        assertSame(water,m.inputFluid("water"));
        water.extract(50);
        assertTrue(m.canSetRecipe(OilData.recipe("electric_engine_unit")));
        assertTrue(m.setRecipe(OilData.recipe("electric_engine_unit")));
        assertSame(water,m.inputFluid("water"));
        assertEquals(0,m.inputFluid("lubricant").insert("water",100));
    }

    @Test void fractionalCraftingSpeedCarriesBetweenBatches() {
        OilMachine m=new OilMachine(.75,1);
        m.setRecipe(OilData.recipe("fill_water_barrel"));
        m.insertInput("minetorio:empty_barrel",20,false);
        m.inputFluid("water").insert("water",1000);
        int produced = 0;
        for(int i=0;i<80;i++) {
            m.insertInput("minetorio:empty_barrel",20,false);
            m.tick(1);
            produced += m.extractOutput("minetorio:water_barrel",10);
        }
        assertEquals(15,produced);
    }

    @Test void refineryHasOneHundredUnitOutputBuffersAndRejectsOverfullSavedState() {
        OilMachine m=new OilMachine(1,1,100);
        m.setRecipe(OilData.recipe("advanced_oil_processing"));
        m.inputFluid("water").insert("water",50);
        m.inputFluid("crude_oil").insert("crude_oil",100);
        m.outputFluid("light_oil").insert("light_oil",60);
        assertEquals(1000,m.inputFluid("crude_oil").capacity());
        assertEquals(100,m.outputFluid("light_oil").capacity());
        m.tick(1);
        assertEquals(100,m.inputFluid("crude_oil").amount());
        m.outputFluid("light_oil").extract(5);
        for(int i=0;i<100;i++)m.tick(1);
        assertEquals(100,m.outputFluid("light_oil").amount());
        var saved=m.snapshot();
        var invalid=new OilMachine.State(saved.recipeId(),saved.inputs(),saved.outputs(),saved.inputFluids(),Map.of("light_oil",101d),saved.crafting(),saved.progress());
        assertThrows(IllegalArgumentException.class,()->m.load(invalid));
        assertEquals(saved,m.snapshot());
    }

    @Test void explicitRecipeChangeRefundsCancelledItemsButNeverRefundsConsumedFluid() {
        OilMachine m=new OilMachine(1,1,100);
        m.setRecipe(OilData.recipe("plastic_bar"));
        m.insertInput("minetorio:coal",3,false);
        m.inputFluid("petroleum_gas").insert("petroleum_gas",60);
        m.tick(1);
        assertEquals(Map.of("minetorio:coal",3),m.changeRecipe(OilData.recipe("sulfur")));
        assertEquals(40,m.inputFluid("petroleum_gas").amount());
        assertFalse(m.isCrafting());
        assertEquals(0,m.progress());
        assertTrue(m.changeRecipe(OilData.recipe("battery")).isEmpty());
        assertEquals(0,m.inputFluid("petroleum_gas").amount());
        assertEquals(0,m.inputFluid("sulfuric_acid").amount());
    }

    @Test void explicitRecipeChangeReturnsFinishedOutputsAlongsideInFlightItems() {
        OilMachine m=new OilMachine(1,1);
        m.setRecipe(OilData.recipe("plastic_bar"));
        m.insertInput("minetorio:coal",2,false);
        m.inputFluid("petroleum_gas").insert("petroleum_gas",40);
        for(int i=0;i<21;i++)m.tick(1);
        assertEquals(Map.of("minetorio:coal",1,"minetorio:plastic_bar",2),m.changeRecipe(null));
        assertTrue(m.inputs().isEmpty());assertTrue(m.outputs().isEmpty());
        assertNull(m.recipe());assertFalse(m.isCrafting());
    }
}
