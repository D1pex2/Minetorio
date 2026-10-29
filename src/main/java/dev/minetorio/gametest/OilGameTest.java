package dev.minetorio.gametest;

import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.oil.OilRegistry;
import dev.minetorio.content.oil.OilSourceBlockEntity;
import dev.minetorio.content.oil.PumpjackBlock;
import dev.minetorio.content.oil.PumpjackBlockEntity;
import dev.minetorio.content.oil.OilProcessingBlock;
import dev.minetorio.content.oil.OilProcessingBlockEntity;
import dev.minetorio.content.oil.SteelChestBlockEntity;
import dev.minetorio.content.oil.SteelFurnaceBlockEntity;
import dev.minetorio.content.oil.OilMenu;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.registry.ModItems;
import java.util.Set;
import java.util.Map;
import dev.minetorio.core.data.OilData;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class OilGameTest {
    @GameTest(template = "empty16")
    public static void sourceSurvivesPumpjackDismantle(GameTestHelper h) {
        BlockPos source = new BlockPos(6, 1, 6);
        h.setBlock(source, OilRegistry.OIL_SOURCE.get());
        OilSourceBlockEntity well = (OilSourceBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(source));
        well.initialize(2);
        well.extracted();
        h.assertTrue(h.getBlockState(source).getPistonPushReaction() == PushReaction.BLOCK, "source moved by piston");
        BlockPos anchor = source.above();
        BeltGameTest.place(h, OilRegistry.PUMPJACK_ITEM.get(), PumpjackBlock.SHAPE.originForAnchor(anchor, Direction.NORTH), Direction.NORTH);
        BlockPos origin = PumpjackBlock.SHAPE.originForAnchor(h.absolutePos(anchor), Direction.NORTH);
        h.assertTrue(h.getLevel().getBlockState(origin).is(OilRegistry.PUMPJACK.get()), "pumpjack was not placed");
        var state = h.getLevel().getBlockState(origin);
        ((MultiblockControllerBlock) state.getBlock()).dismantle(h.getLevel(), origin, state);
        h.assertTrue(h.getBlockState(source).is(OilRegistry.OIL_SOURCE.get()), "dismantle erased well");
        h.assertTrue(((OilSourceBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(source))).cycles() == 1, "yield history lost");
        h.succeed();
    }

    @GameTest(template = "empty16", timeoutTicks = 100)
    public static void poweredPumpjackExtractsFromPersistentSource(GameTestHelper h) {
        BlockPos source = new BlockPos(6, 1, 6), anchor = source.above();
        h.setBlock(source, OilRegistry.OIL_SOURCE.get());
        OilSourceBlockEntity well=(OilSourceBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(source));
        well.initialize(1);
        BeltGameTest.place(h, OilRegistry.PUMPJACK_ITEM.get(), PumpjackBlock.SHAPE.originForAnchor(anchor, Direction.NORTH), Direction.NORTH);
        BlockPos origin=PumpjackBlock.SHAPE.originForAnchor(h.absolutePos(anchor),Direction.NORTH);
        PumpjackBlockEntity pump=(PumpjackBlockEntity)h.getLevel().getBlockEntity(origin);
        TestPower.powerAt(h,new BlockPos(9,2,6));
        h.runAtTickTime(65,()->{
            h.assertTrue(pump.fluidBox(0).amount()>0, "pumpjack produced no crude oil");
            h.assertTrue("crude_oil".equals(pump.fluidBox(0).fluid()), "wrong output fluid");
            h.assertTrue(well.cycles()>0, "source did not record extraction");
            h.succeed();
        });
    }

    @GameTest(template = "empty16", timeoutTicks = 160)
    public static void basicRefineryUsesStablePortsAndPower(GameTestHelper h) {
        BlockPos anchor=new BlockPos(7,1,7);
        OilProcessingBlock block=OilRegistry.OIL_REFINERY.get();
        BeltGameTest.place(h,OilRegistry.OIL_REFINERY_ITEM.get(),block.shape().originForAnchor(anchor,Direction.NORTH),Direction.NORTH);
        BlockPos origin=block.shape().originForAnchor(h.absolutePos(anchor),Direction.NORTH);
        OilProcessingBlockEntity refinery=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(origin);
        h.assertTrue(refinery.select(OilData.recipe("basic_oil_processing")),"basic recipe refused");
        h.assertTrue(refinery.fluidBox(1).insert("crude_oil",100)==100,"crude port refused input");
        h.assertTrue(refinery.fluidBox(0).capacity()==0 && refinery.fluidBox(2).capacity()==0 && refinery.fluidBox(3).capacity()==0,"unused ports active");
        TestPower.powerAt(h,new BlockPos(10,1,7));
        h.runAtTickTime(130,()->{
            h.assertTrue("petroleum_gas".equals(refinery.fluidBox(4).fluid()),"gas not emitted at the third output");
            h.assertTrue(refinery.fluidBox(4).amount()==45,"wrong gas output");
            h.succeed();
        });
    }

    @GameTest(template = "empty16")
    public static void steelChestHasFortyEightUsableSlots(GameTestHelper h) {
        BlockPos pos=new BlockPos(5,1,5);
        h.setBlock(pos,OilRegistry.STEEL_CHEST.get());
        var chest=(SteelChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        var handler=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(pos),Direction.UP);
        h.assertTrue(handler!=null && handler.getSlots()==48,"steel chest capacity is not 48");
        h.assertTrue(handler.insertItem(47,new ItemStack(Items.IRON_INGOT,3),false).isEmpty(),"last slot rejected items");
        h.assertTrue(chest.inventory().getItem(47).getCount()==3,"last slot did not retain items");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void refineryRecipeAndFluidsSurviveReload(GameTestHelper h) {
        BlockPos pos=new BlockPos(5,1,8);
        BeltGameTest.place(h,OilRegistry.OIL_REFINERY_ITEM.get(),pos,Direction.NORTH);
        BlockPos abs=h.absolutePos(pos);
        var refinery=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(abs);
        h.assertTrue(refinery.select(OilData.recipe("advanced_oil_processing")),"recipe refused");
        refinery.fluidBox(0).insert("water",50); refinery.fluidBox(1).insert("crude_oil",100);
        refinery.fluidBox(4).insert("petroleum_gas",55);
        var player=MachineGameTest.player(h,new BlockPos(5,1,9));
        var menu=(OilMenu)refinery.createMenu(1,player.getInventory(),player);
        h.assertTrue(menu.fluidAmount(0)==50 && menu.fluidAmount(1)==100 && menu.fluidAmount(4)==55,"GUI tank data incorrect");
        var copy=new OilProcessingBlockEntity(abs,h.getLevel().getBlockState(abs));
        copy.loadWithComponents(refinery.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.machine().recipe().id().equals("advanced_oil_processing"),"recipe not restored");
        h.assertTrue(copy.fluidBox(0).amount()==50 && copy.fluidBox(1).amount()==100 && copy.fluidBox(4).amount()==55,"fluid contents not restored");
        h.succeed();
    }

    @GameTest(template = "empty16", batch="oil_research")
    public static void refineryMenuRejectsLockedRecipe(GameTestHelper h) {
        var research=ResearchSavedData.get(h.getLevel().getServer()); research.restore(Set.of(),null,Map.of());
        BlockPos pos=new BlockPos(5,1,8);
        BeltGameTest.place(h,OilRegistry.OIL_REFINERY_ITEM.get(),pos,Direction.NORTH);
        var refinery=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        var player=MachineGameTest.player(h,new BlockPos(5,1,9));
        var menu=(OilMenu)refinery.createMenu(1,player.getInventory(),player);
        player.containerMenu=menu;
        int advanced=OilMenu.RECIPES.indexOf(OilData.recipe("advanced_oil_processing"));
        h.assertTrue(!menu.clickMenuButton(player,advanced),"locked advanced oil processing was accepted");
        h.assertTrue(refinery.machine().recipe()==null,"locked recipe was set");
        research.restore(Set.of("advanced_oil_processing"),null,Map.of());
        h.assertTrue(menu.clickMenuButton(player,advanced),"researched recipe refused");
        research.restore(Set.of(),null,Map.of());
        h.succeed();
    }

    @GameTest(template = "empty16", batch="oil_change_recipe", timeoutTicks=40)
    public static void changingRecipeRefundsItemsAndReturnsFluidToPipe(GameTestHelper h) {
        var research=ResearchSavedData.get(h.getLevel().getServer()); research.restore(Set.of("oil_processing","coal_liquefaction"),null,Map.of());
        BlockPos pos=new BlockPos(5,1,8), pipe=new BlockPos(6,1,9);
        BeltGameTest.place(h,OilRegistry.OIL_REFINERY_ITEM.get(),pos,Direction.NORTH);
        BeltGameTest.place(h,ModItems.PIPE.get(),pipe,Direction.NORTH);
        var refinery=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(refinery.select(OilData.recipe("coal_liquefaction")),"coal liquefaction refused");
        h.assertTrue(refinery.machine().insertInput("minetorio:coal",10,false)==10,"coal refused");
        refinery.fluidBox(0).insert("heavy_oil",25);
        var player=MachineGameTest.player(h,new BlockPos(5,1,9));
        var menu=(OilMenu)refinery.createMenu(1,player.getInventory(),player); player.containerMenu=menu;
        h.runAtTickTime(5,()->{
            int basic=OilMenu.RECIPES.indexOf(OilData.recipe("basic_oil_processing"));
            h.assertTrue(menu.clickMenuButton(player,basic),"recipe switch refused");
            h.assertTrue(player.getInventory().contains(new ItemStack(BaseMaterials.COAL.get())),"input coal not returned");
            var segment=dev.minetorio.content.fluid.FluidAccess.segmentAt(h.getLevel(),h.absolutePos(pipe));
            h.assertTrue(segment!=null && "heavy_oil".equals(segment.fluid()) && Math.abs(segment.amount()-25)<1e-6,"heavy oil not returned to pipe");
            research.restore(Set.of(),null,Map.of()); h.succeed();
        });
    }

    @GameTest(template = "empty16", batch="steel_research", timeoutTicks=200)
    public static void steelFurnaceRejectsLockedRecipeAndResumesAfterResearch(GameTestHelper h) {
        ResearchSavedData research=ResearchSavedData.get(h.getLevel().getServer()); research.restore(Set.of(),null,Map.of());
        BlockPos pos=new BlockPos(5,1,5);
        BeltGameTest.place(h,OilRegistry.STEEL_FURNACE_ITEM.get(),pos,Direction.NORTH);
        SteelFurnaceBlockEntity furnace=(SteelFurnaceBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        ItemStack plate=new ItemStack(ModItems.IRON_PLATE.get(),5);
        h.assertTrue(furnace.itemHandler().insertItem(SteelFurnaceBlockEntity.INPUT_SLOT,plate,false).getCount()==5,"locked steel input accepted");
        furnace.machine().insertInput("minetorio:iron_plate",5,false);
        furnace.burner().insertFuel("minetorio:coal",1,false);
        h.runAtTickTime(25,()->{
            h.assertTrue(furnace.machine().progress()==0,"locked steel recipe advanced");
            research.restore(Set.of("steel_processing"),null,Map.of());
        });
        h.runAtTickTime(195,()->{
            h.assertTrue(furnace.machine().outputs().getOrDefault("minetorio:steel_plate",0)>0,"steel did not smelt after research");
            research.restore(Set.of(),null,Map.of());
            h.succeed();
        });
    }

    @GameTest(template = "empty16", batch="stone_steel_research", timeoutTicks=200)
    public static void stoneFurnaceRejectsSteelUntilResearch(GameTestHelper h) {
        ResearchSavedData research=ResearchSavedData.get(h.getLevel().getServer()); research.restore(Set.of(),null,Map.of());
        BlockPos pos=new BlockPos(5,1,5);
        BeltGameTest.place(h,ModItems.STONE_FURNACE.get(),pos,Direction.NORTH);
        var furnace=(StoneFurnaceBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        ItemStack plate=new ItemStack(ModItems.IRON_PLATE.get(),5);
        h.assertTrue(furnace.itemHandler().insertItem(StoneFurnaceBlockEntity.INPUT_SLOT,plate,false).getCount()==5,"locked steel input accepted");
        furnace.machine().insertInput("minetorio:iron_plate",5,false);
        furnace.burner().insertFuel("minetorio:coal",1,false);
        h.runAtTickTime(25,()->{
            h.assertTrue(furnace.machine().progress()==0,"locked steel recipe advanced");
            research.restore(Set.of("steel_processing"),null,Map.of());
        });
        h.runAtTickTime(195,()->{
            h.assertTrue(furnace.machine().progress()>0 || furnace.machine().outputs().getOrDefault("minetorio:steel_plate",0)>0,"steel did not resume after research");
            research.restore(Set.of(),null,Map.of());
            h.succeed();
        });
    }
}
