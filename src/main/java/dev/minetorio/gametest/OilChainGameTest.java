package dev.minetorio.gametest;

import dev.minetorio.content.registry.BaseMaterials;
import dev.minetorio.content.fluid.FluidAccess;
import dev.minetorio.content.oil.OilRegistry;
import dev.minetorio.content.oil.OilSourceBlockEntity;
import dev.minetorio.content.oil.OilProcessingBlockEntity;
import dev.minetorio.content.oil.PumpjackBlock;
import dev.minetorio.content.oil.PumpjackBlockEntity;
import dev.minetorio.content.fluid.FluidPorts;
import dev.minetorio.content.fluid.FluidNetworkManager;
import dev.minetorio.content.fluid.PipeBlock;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.demo.OilDemoBuilder;
import dev.minetorio.core.data.OilData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.minetorio.gametest.BeltGameTest.place;

/** Real steam, powered machinery, pipe network and inserters, without filling machine tanks by hand. */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class OilChainGameTest {
    private static BlockPos p(int x,int z) { return new BlockPos(x,1,z); }
    private static void pipe(GameTestHelper h,int x,int z) { place(h,ModItems.PIPE.get(),p(x,z),Direction.NORTH); }

    @GameTest(template="empty32",timeoutTicks=1000)
    public static void steamPowersOilToAutomatedPlastic(GameTestHelper h) {
        PowerGameTest.steamPlant(h,20);
        for(BlockPos pos:new BlockPos[]{p(12,8),p(15,12),p(16,17),p(22,17),p(24,14),p(30,10)})place(h,ModItems.SMALL_ELECTRIC_POLE.get(),pos,Direction.NORTH);
        BlockPos source=new BlockPos(18,0,18); h.setBlock(source,OilRegistry.OIL_SOURCE.get());
        ((OilSourceBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(source))).initialize(1);
        place(h,OilRegistry.PUMPJACK_ITEM.get(),p(17,19),Direction.NORTH);
        place(h,OilRegistry.OIL_REFINERY_ITEM.get(),p(23,19),Direction.NORTH);
        place(h,OilRegistry.CHEMICAL_PLANT_ITEM.get(),p(26,13),Direction.NORTH);
        var refinery=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p(23,19)));
        var plant=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p(26,13)));
        h.assertTrue(refinery.select(OilData.recipe("basic_oil_processing")),"refinery recipe refused");
        h.assertTrue(plant.select(OilData.recipe("plastic_bar")),"plastic recipe refused");
        for(int x=19;x<=21;x++)pipe(h,x,16);
        for(int z=17;z<=20;z++)pipe(h,21,z);
        for(int x=22;x<=26;x++)pipe(h,x,20);
        pipe(h,27,14); pipe(h,26,14);
        InserterGameTest.container(h,Blocks.CHEST,p(24,12),new ItemStack(BaseMaterials.COAL.get(),16));
        h.setBlock(p(30,12),Blocks.CHEST);
        place(h,ModItems.INSERTER.get(),p(25,12),Direction.EAST);
        place(h,ModItems.INSERTER.get(),p(29,12),Direction.EAST);
        h.runAtTickTime(280,()->h.assertTrue(PipeBlock.toggle(h.getLevel(),h.absolutePos(p(26,20)),Direction.NORTH,null),"could not close refinery pipe"));
        h.runAtTickTime(400,()->{
            List<BlockPos> nodes=new ArrayList<>(); for(int x=19;x<=21;x++)nodes.add(p(x,16));
            for(int z=17;z<=20;z++)nodes.add(p(21,z)); for(int x=22;x<=26;x++)nodes.add(p(x,20));
            var network=FluidNetworkManager.get(h.getLevel()); var before=FluidAccess.segmentAt(h.getLevel(),h.absolutePos(nodes.getFirst()));
            h.assertTrue(before!=null && "crude_oil".equals(before.fluid()) && before.amount()>0,"crude pipe did not accumulate for unload test");
            double amount=before.amount(); var tags=new ArrayList<net.minecraft.nbt.CompoundTag>(); var registry=h.getLevel().registryAccess();
            for(BlockPos node:nodes)tags.add(h.getLevel().getBlockEntity(h.absolutePos(node)).saveWithoutMetadata(registry));
            for(BlockPos node:nodes)network.network().removeNode(h.absolutePos(node).asLong());
            for(int i=0;i<nodes.size();i++) {
                var be=h.getLevel().getBlockEntity(h.absolutePos(nodes.get(i))); be.loadWithComponents(tags.get(i),registry); network.queueNode(be.getBlockPos());
            }
            var restored=FluidAccess.segmentAt(h.getLevel(),h.absolutePos(nodes.getFirst()));
            h.assertTrue(restored!=null && "crude_oil".equals(restored.fluid()) && Math.abs(restored.amount()-amount)<1e-6,"oil changed across simulated chunk unload");
            h.assertTrue(PipeBlock.toggle(h.getLevel(),h.absolutePos(p(26,20)),Direction.NORTH,null),"could not reopen refinery pipe");
        });
        h.runAtTickTime(950,()->{
            var crude=FluidAccess.segmentAt(h.getLevel(),h.absolutePos(p(21,20)));
            h.assertTrue(crude!=null && (crude.fluid()==null || crude.fluid().equals("crude_oil")),"crude line invalid");
            var output=dev.minetorio.content.machine.MachineItems.item("minetorio:plastic_bar");
            h.assertTrue(InserterGameTest.count(h,p(30,12),output)>0,"steam-powered oil chain produced no plastic; refinery="+refinery.machine().snapshot()+" power="+refinery.satisfaction()+" plant="+plant.machine().snapshot()+" power="+plant.satisfaction()+" crude="+(crude==null?"none":crude.fluid()+":"+crude.amount()));
            h.succeed();
        });
    }

    @GameTest(template="empty32",timeoutTicks=2600)
    public static void completeOilDemoProducesPlasticAndBatteries(GameTestHelper h) {
        OilDemoBuilder.Layout layout=OilDemoBuilder.build(h.getLevel(),h.absolutePos(BlockPos.ZERO));
        h.runAtTickTime(2500,()->{
            var plastic=dev.minetorio.content.machine.MachineItems.item("minetorio:plastic_bar");
            var battery=dev.minetorio.content.machine.MachineItems.item("minetorio:battery");
            var out1=h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,layout.plasticOutput(),null);
            var out2=h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,layout.batteryOutput(),null);
            int n1=0,n2=0; for(int i=0;i<out1.getSlots();i++) if(out1.getStackInSlot(i).is(plastic))n1+=out1.getStackInSlot(i).getCount();
            for(int i=0;i<out2.getSlots();i++) if(out2.getStackInSlot(i).is(battery))n2+=out2.getStackInSlot(i).getCount();
            var plasticBe=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(layout.plasticPlant());
            var sulfurBe=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(layout.sulfurPlant());
            var acidBe=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(layout.acidPlant());
            var batteryBe=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(layout.batteryPlant());
            h.assertTrue(n1>0 && n2>0,"demo output plastic="+n1+" battery="+n2+"; plastic="+plasticBe.machine().snapshot()+" sulfur="+sulfurBe.machine().snapshot()+" acid="+acidBe.machine().snapshot()+" battery="+batteryBe.machine().snapshot());
            h.succeed();
        });
    }

    @GameTest(template="empty32",timeoutTicks=220)
    public static void pumpjackRotationsDeliverAllProducedOil(GameTestHelper h) {
        Direction[] facings={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        PumpjackBlockEntity[] pumps=new PumpjackBlockEntity[4]; OilSourceBlockEntity[] sources=new OilSourceBlockEntity[4]; BlockPos[] pipes=new BlockPos[4];
        for(int i=0;i<4;i++) {
            BlockPos origin=p(3+i*7,17).above(); Direction facing=facings[i];
            BlockPos anchor=PumpjackBlock.SHAPE.anchorOf(origin,facing); BlockPos source=anchor.below();
            h.setBlock(source,OilRegistry.OIL_SOURCE.get()); sources[i]=(OilSourceBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(source)); sources[i].initialize(1);
            place(h,OilRegistry.PUMPJACK_ITEM.get(),origin,facing);
            pumps[i]=(PumpjackBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(origin));
            BlockPos neighbor=anchor.relative(facing,2).relative(facing.getClockWise()); pipes[i]=neighbor;
            var port=FluidPorts.ports(h.getLevel(),h.absolutePos(origin)).getFirst();
            h.assertTrue(port.neighbor().equals(h.absolutePos(neighbor)),"wrong pumpjack output for "+facing);
            place(h,ModItems.PIPE.get(),neighbor,Direction.NORTH);
            TestPower.powerAt(h,origin.above(3));
        }
        h.runAtTickTime(180,()->{
            for(int i=0;i<4;i++) {
                var segment=FluidAccess.segmentAt(h.getLevel(),h.absolutePos(pipes[i]));
                h.assertTrue(segment!=null && "crude_oil".equals(segment.fluid()),"no oil at "+facings[i]);
                double expected=0; for(long j=0;j<sources[i].cycles();j++)expected+=10*dev.minetorio.content.oil.OilFields.yield(1,j);
                double actual=segment.amount()+pumps[i].fluidBox(0).amount();
                h.assertTrue(Math.abs(expected-actual)<1e-6,"oil lost at "+facings[i]+": "+expected+" vs "+actual);
            }
            h.succeed();
        });
    }

    @GameTest(template="empty32",timeoutTicks=1000)
    public static void fullFractionStopsRefineryUntilLightOilCracking(GameTestHelper h) {
        PowerGameTest.steamPlant(h,20);
        for(BlockPos pos:new BlockPos[]{p(12,8),p(15,12),p(16,17),p(21,14),p(24,14),p(21,11)})place(h,ModItems.SMALL_ELECTRIC_POLE.get(),pos,Direction.NORTH);
        BlockPos source=new BlockPos(18,0,18); h.setBlock(source,OilRegistry.OIL_SOURCE.get());
        ((OilSourceBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(source))).initialize(50);
        place(h,OilRegistry.PUMPJACK_ITEM.get(),p(17,19),Direction.NORTH);
        place(h,OilRegistry.OIL_REFINERY_ITEM.get(),p(23,19),Direction.NORTH);
        var refinery=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p(23,19)));
        h.assertTrue(refinery.select(OilData.recipe("advanced_oil_processing")),"advanced recipe refused");
        pipe(h,19,16); pipe(h,20,16); for(int z=17;z<=22;z++)pipe(h,20,z); for(int x=21;x<=26;x++)pipe(h,x,22); pipe(h,26,21); pipe(h,26,20);
        pipe(h,8,8); for(int x=8;x<=22;x++)pipe(h,x,9); for(int z=10;z<=20;z++)pipe(h,22,z); pipe(h,23,20); pipe(h,24,20);
        place(h,ModItems.STORAGE_TANK.get(),p(28,10),Direction.NORTH);
        pipe(h,27,14); for(int z=11;z<=14;z++)pipe(h,28,z);
        double[] before={0};
        h.runAtTickTime(400,()->{
            h.assertTrue(refinery.machine().isOutputFull(),"refinery did not stop for a full light fraction: "+refinery.machine().snapshot());
            before[0]=refinery.fluidBox(2).amount();
            h.assertTrue(refinery.fluidBox(3).amount()>=90,"light fraction did not accumulate");
            place(h,OilRegistry.CHEMICAL_PLANT_ITEM.get(),p(23,11),Direction.NORTH);
            var cracker=(OilProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(p(23,11)));
            h.assertTrue(cracker.select(OilData.recipe("light_oil_cracking")),"cracking recipe refused");
            pipe(h,23,12); pipe(h,25,14); pipe(h,25,13); pipe(h,25,12);
            for(int x=23;x<=27;x++)pipe(h,x,8); pipe(h,27,9); pipe(h,27,10);
        });
        h.runAtTickTime(900,()->{
            h.assertTrue(refinery.fluidBox(2).amount()>before[0],"refinery did not resume after cracking: "+refinery.machine().snapshot());
            h.succeed();
        });
    }
}
