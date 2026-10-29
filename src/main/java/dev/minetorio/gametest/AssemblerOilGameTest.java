package dev.minetorio.gametest;

import dev.minetorio.content.fluid.FluidMachine;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.data.Phase1Data;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.minetorio.gametest.BeltGameTest.place;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public final class AssemblerOilGameTest {
    @GameTest(template="empty16",timeoutTicks=90)
    public static void assemblerTwoBarrelsWaterWithRealFluidAndRestores(GameTestHelper h) {
        BlockPos pos=new BlockPos(2,1,2);
        place(h,ModItems.ASSEMBLING_MACHINE_2.get(),pos,Direction.EAST);
        var be=(AssemblerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        be.setRecipe(Phase1Data.recipes().get("fill_water_barrel").orElseThrow());
        var handler=be.itemHandler();
        var barrel=MachineItems.stack("minetorio:empty_barrel",1);
        h.assertTrue(!barrel.isEmpty(),"empty_barrel item not registered");
        h.assertTrue(handler.insertItem(0,barrel,false).isEmpty(),"barrel input refused");
        TestPower.powerAt(h,new BlockPos(1,1,2));
        h.runAtTickTime(15,()->{
            h.assertTrue(handler.extractItem(AssemblerBlockEntity.OUTPUT_SLOT,64,true).isEmpty(),"crafted without water");
            h.assertTrue(be instanceof FluidMachine,"assembler has no fluid ports");
            ((FluidMachine)be).fluidBox(0).insert("water",50);
            var saved=be.saveWithFullMetadata(h.getLevel().registryAccess());
            be.loadWithComponents(saved,h.getLevel().registryAccess());
        });
        h.runAtTickTime(35,()->{
            var out=handler.extractItem(AssemblerBlockEntity.OUTPUT_SLOT,64,false);
            h.assertTrue(MachineItems.id(out).equals("minetorio:water_barrel") && out.getCount()==1,"missing filled barrel: "+out+"; state="+be.oilMachine().snapshot()+" power="+be.satisfaction());
            h.assertTrue(((FluidMachine)be).fluidBox(0).amount()==0,"water not consumed");
            h.succeed();
        });
    }

    @GameTest(template="empty16",timeoutTicks=20)
    public static void assemblerOneCannotUseFluidRecipesButCanCraftEngines(GameTestHelper h) {
        BlockPos pos=new BlockPos(2,1,2);
        place(h,ModItems.ASSEMBLING_MACHINE_1.get(),pos,Direction.EAST);
        var be=(AssemblerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        be.setRecipe(Phase1Data.recipes().get("fill_water_barrel").orElseThrow());
        h.assertTrue(be.machine().recipe()==null,"tier1 accepted fluid crafting");
        be.setRecipe(Phase1Data.recipes().get("engine_unit").orElseThrow());
        h.assertTrue(be.machine().recipe()!=null && be.machine().recipe().id().equals("engine_unit"),"tier1 refused advanced-crafting engine");
        h.succeed();
    }

    @GameTest(template="empty16",timeoutTicks=20)
    public static void assemblerAcceptsEightyGearsForExactExpressUndergroundRecipe(GameTestHelper h) {
        BlockPos pos=new BlockPos(2,1,2);
        place(h,ModItems.ASSEMBLING_MACHINE_2.get(),pos,Direction.EAST);
        var be=(AssemblerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        be.setRecipe(Phase1Data.recipes().get("express_underground_belt").orElseThrow());
        h.assertTrue(be.itemHandler().getSlotLimit(0)==160,"capability misreports 80-gear recipe buffer limit: "+be.itemHandler().getSlotLimit(0));
        h.assertTrue(be.itemHandler().getSlotLimit(AssemblerBlockEntity.OUTPUT_SLOT)==64,"output stack limit changed");
        var player=MachineGameTest.player(h,new BlockPos(3,1,7));
        var menu=be.createMenu(1,player.getInventory(),player);
        var first=menu.getSlot(0).safeInsert(MachineItems.stack("minetorio:iron_gear_wheel",64));
        var second=menu.getSlot(0).safeInsert(MachineItems.stack("minetorio:iron_gear_wheel",16));
        h.assertTrue(first.isEmpty() && second.isEmpty(),"cannot buffer 80 gears for one recipe: "+be.oilMachine().inputs());
        h.assertTrue(be.oilMachine().inputs().getOrDefault("minetorio:iron_gear_wheel",0)==80,"wrong gear count");
        h.succeed();
    }

    @GameTest(template="empty16",timeoutTicks=45)
    public static void changingMixedRecipeRefundsItemsAndCannotContaminateForeignFluidPipe(GameTestHelper h) {
        BlockPos pos=new BlockPos(2,1,2),pipe=new BlockPos(1,1,3);
        place(h,ModItems.ASSEMBLING_MACHINE_2.get(),pos,Direction.EAST);
        var be=(AssemblerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        be.setRecipe(Phase1Data.recipes().get("electric_engine_unit").orElseThrow());
        be.itemHandler().insertItem(0,MachineItems.stack("minetorio:engine_unit",1),false);
        be.itemHandler().insertItem(1,MachineItems.stack("minetorio:electronic_circuit",2),false);
        be.fluidBox(0).insert("lubricant",45);
        place(h,ModItems.PIPE.get(),pipe,Direction.NORTH);
        var segment=dev.minetorio.content.fluid.FluidAccess.segmentAt(h.getLevel(),h.absolutePos(pipe));
        segment.insert("water",50);
        TestPower.powerAt(h,new BlockPos(1,1,2));
        h.runAtTickTime(20,()->{
            h.assertTrue(be.oilMachine().isCrafting(),"electric engine did not start");
            var returned=be.setRecipe(Phase1Data.recipes().get("fill_water_barrel").orElseThrow());
            h.assertTrue(returned.equals(java.util.Map.of("minetorio:engine_unit",1,"minetorio:electronic_circuit",2)),"cancelled items not refunded: "+returned);
            h.assertTrue(be.oilMachine().inputFluid("lubricant").amount()==0,"old fluid not cleared on explicit recipe change");
            h.assertTrue("water".equals(segment.fluid()) && segment.amount()==50,"foreign fluid pipe contaminated or drained");
            h.assertTrue(!be.oilMachine().isCrafting() && be.oilMachine().progress()==0,"cancelled progress retained");
            h.succeed();
        });
    }

    @GameTest(template="empty16",timeoutTicks=35)
    public static void incompatibleLoadedTierCannotAdvanceHiddenFluidRecipe(GameTestHelper h) {
        BlockPos sourcePos=new BlockPos(2,1,2),targetPos=new BlockPos(8,1,2);
        place(h,ModItems.ASSEMBLING_MACHINE_2.get(),sourcePos,Direction.EAST);
        place(h,ModItems.ASSEMBLING_MACHINE_1.get(),targetPos,Direction.EAST);
        var source=(AssemblerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(sourcePos));
        var target=(AssemblerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(targetPos));
        source.setRecipe(Phase1Data.recipes().get("electric_engine_unit").orElseThrow());
        source.oilMachine().insertInput("minetorio:engine_unit",1,false);
        source.oilMachine().insertInput("minetorio:electronic_circuit",2,false);
        source.fluidBox(0).insert("lubricant",45);
        source.oilMachine().tick(1);
        var saved=source.saveWithFullMetadata(h.getLevel().registryAccess());
        target.loadWithComponents(saved,h.getLevel().registryAccess());
        var before=target.oilMachine().snapshot();
        TestPower.powerAt(h,new BlockPos(7,1,2));
        h.runAtTickTime(20,()->{
            h.assertTrue(target.demand()==0,"unsupported fluid recipe draws power in tier1");
            h.assertTrue(target.oilMachine().snapshot().equals(before),"unsupported tier advanced or discarded fluid state");
            h.succeed();
        });
    }
}
