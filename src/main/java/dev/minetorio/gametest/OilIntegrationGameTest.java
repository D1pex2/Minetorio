package dev.minetorio.gametest;

import dev.minetorio.content.registry.ModItems;
import dev.minetorio.Minetorio;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.oilmap.OilMapData;
import dev.minetorio.content.oilmap.OilMapPayloads;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.core.data.OilData;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.content.fluid.FluidAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cross-layer contracts that pure recipe tables alone cannot prove. */
@GameTestHolder(Minetorio.MODID)
@PrefixGameTestTemplate(false)
public final class OilIntegrationGameTest {
    @GameTest(template = "empty16")
    public static void assemblerDowngradeRefusesToHideFluidContents(GameTestHelper h) {
        var pos = new BlockPos(4, 1, 4);
        dev.minetorio.gametest.BeltGameTest.place(h, dev.minetorio.content.registry.ModItems.ASSEMBLING_MACHINE_2.get(), pos, net.minecraft.core.Direction.EAST);
        var absolute = h.absolutePos(pos);
        var be = (dev.minetorio.content.machine.AssemblerBlockEntity) h.getLevel().getBlockEntity(absolute);
        be.setRecipe(Phase1Data.recipes().get("fill_water_barrel").orElseThrow());
        be.oilMachine().inputFluid("water").insert("water", 100);
        be.itemHandler().insertItem(0, MachineItems.stack("minetorio:empty_barrel", 2), false);
        var result = dev.minetorio.content.robot.RobotWork.upgrade(h.getLevel(), absolute, dev.minetorio.content.registry.ModItems.ASSEMBLING_MACHINE_1.get(), 1);
        h.assertTrue(result == null, "Downgrade hid a fluid recipe inside tier 1");
        h.assertTrue(h.getLevel().getBlockEntity(absolute) == be && be.oilMachine().inputFluid("water").amount() == 100
                && be.oilMachine().inputs().getOrDefault("minetorio:empty_barrel", 0) == 2, "Rejected downgrade lost machine contents");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void removedGhostSupportPreventsConstructionUntilRestored(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(5, 1, 5));
        var level = h.getLevel();
        level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(dev.minetorio.content.robot.Ghosts.place(level, pos, dev.minetorio.content.registry.ModItems.TRANSPORT_BELT.get(), net.minecraft.core.Direction.EAST), "Could not place supported ghost");
        level.removeBlock(pos.below(), false);
        var item = new net.minecraft.world.item.ItemStack(dev.minetorio.content.registry.ModItems.TRANSPORT_BELT.get());
        h.assertTrue(!dev.minetorio.content.robot.Ghosts.canBuild(level, pos) && !dev.minetorio.content.robot.Ghosts.build(level, pos, null, item), "Built a floating belt after support removal");
        h.assertTrue(item.getCount() == 1 && level.getBlockState(pos).is(dev.minetorio.content.registry.ModBlocks.GHOST.get()), "Failed build consumed an item or ghost");
        level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(dev.minetorio.content.robot.Ghosts.build(level, pos, null, item), "Restoring support did not allow construction");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void everyOilIngredientAndProductIsARegisteredItem(GameTestHelper h) {
        for (var recipe : Phase1Data.recipes().all()) {
            for (var ingredient : recipe.inputs()) h.assertTrue(!MachineItems.stack(ingredient.item(), 1).isEmpty(), "Missing input " + recipe.id() + ": " + ingredient.item());
            for (var ingredient : recipe.outputs()) h.assertTrue(!MachineItems.stack(ingredient.item(), 1).isEmpty(), "Missing output " + recipe.id() + ": " + ingredient.item());
        }
        for (var recipe : OilData.recipes()) {
            for (var ingredient : recipe.itemInputs()) h.assertTrue(!MachineItems.stack(ingredient.item(), 1).isEmpty(), "Missing oil input " + ingredient.item());
            for (var ingredient : recipe.itemOutputs()) h.assertTrue(!MachineItems.stack(ingredient.item(), 1).isEmpty(), "Missing oil output " + ingredient.item());
        }
        h.assertTrue(LabBlockEntity.isPack(MachineItems.stack("minetorio:chemical_science_pack", 1)), "Lab refuses chemical science");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void fieldYieldQueriesNeverLoadDistantChunks(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos one = new BlockPos(12_000_000, 64, 12_000_000), two = one.offset(8, 0, 8);
        h.assertTrue(level.getChunkSource().getChunkNow(one.getX() >> 4, one.getZ() >> 4) == null, "Test requires an unloaded distant chunk");
        OilMapData.update(level, one, Long.MIN_VALUE + 17, 1.5);
        OilMapData.update(level, two, Long.MIN_VALUE + 17, 2);
        var info = OilMapPayloads.lookup(level, one.getX(), one.getZ());
        h.assertTrue(info.count() == 2 && info.yield() == 1.5 && info.total() == 3.5, "Incorrect field total " + info);
        OilMapData.update(level, one, Long.MIN_VALUE + 17, 0.3);
        h.assertTrue(Math.abs(OilMapPayloads.lookup(level, two.getX(), two.getZ()).total() - 2.3) < 1e-8, "Depletion absent from field total");
        var saved = OilMapData.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        var restored = OilMapData.load(saved, level.registryAccess());
        h.assertTrue(restored.at(one.getX(), one.getZ()).yield() == 0.3
                && Math.abs(restored.total(Long.MIN_VALUE + 17) - 2.3) < 1e-8, "Saved field yield was not restored");
        h.assertTrue(level.getChunkSource().getChunkNow(one.getX() >> 4, one.getZ() >> 4) == null, "Map query forced a chunk load");
        OilMapData.remove(level, one); OilMapData.remove(level, two);
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void rejectedFluidTransferLeavesSourceUntouched(GameTestHelper h) {
        FluidBox source = new FluidBox(100), target = new FluidBox(100, "water");
        source.insert("crude_oil", 75);
        h.assertTrue(FluidAccess.transfer(source, target, 50) == 0, "Wrong fluid was transferred");
        h.assertTrue(source.amount() == 75 && target.amount() == 0, "Rejected transfer destroyed fluid");
        h.succeed();
    }
}
