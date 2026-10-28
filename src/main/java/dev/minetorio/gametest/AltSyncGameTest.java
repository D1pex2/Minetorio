package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.alt.AltSource;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltStatus;
import dev.minetorio.core.orefield.OreType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Alt-mode info reaches the client: each machine's update tag carries its icon and status, a
 * client copy loading that tag shows the same, and the info follows the machine's state.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class AltSyncGameTest {
    private static final BlockPos AT = new BlockPos(2, 1, 2);

    /** What a client would show after receiving {@code be}'s update tag. */
    private static AltInfo synced(GameTestHelper h, BlockEntity be) {
        CompoundTag tag = be.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(tag.contains(AltSource.KEY_ALT), "no alt info in the update tag of " + be);
        return AltSource.read(tag, null);
    }

    private static void expect(GameTestHelper h, AltInfo actual, String icon, AltStatus status) {
        h.assertTrue(actual.equals(AltInfo.of(icon, status)), "alt info " + actual + ", expected " + icon + " / " + status);
    }

    /** No fuel → NO_FUEL; fuel and raw iron → the plate it smelts; the info reaches a client copy. */
    @GameTest(template = "empty16", timeoutTicks = 100)
    public static void furnaceStatusReachesTheUpdateTag(GameTestHelper h) {
        StoneFurnaceBlockEntity be = MachineGameTest.furnace(h, AT);
        expect(h, synced(h, be), null, AltStatus.NO_FUEL);
        MachineGameTest.insert(h, AT, StoneFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 2));
        MachineGameTest.insert(h, AT, StoneFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));
        h.runAfterDelay(25, () -> {
            AltInfo info = synced(h, be);
            expect(h, info, "minetorio:iron_plate", AltStatus.NONE);
            StoneFurnaceBlockEntity client = new StoneFurnaceBlockEntity(be.getBlockPos(), be.getBlockState());
            client.loadWithComponents(be.getUpdateTag(h.getLevel().registryAccess()), h.getLevel().registryAccess());
            h.assertTrue(client.altInfo().equals(info), "client copy shows " + client.altInfo());
            h.succeed();
        });
    }

    /** A burner drill on iron with coal shows iron ore; without a field under it, NO_ORE; without fuel, NO_FUEL. */
    @GameTest(template = "empty16", timeoutTicks = 100)
    public static void drillStatusReachesTheUpdateTag(GameTestHelper h) {
        DrillGameTest.field(h, OreType.IRON, 2, 2, 3, 3, 1000);
        BurnerDrillBlockEntity onOre = DrillGameTest.burnerDrill(h, 0);
        place(h, ModItems.BURNER_MINING_DRILL.get(), new BlockPos(8, 1, 8), Direction.EAST);
        BurnerDrillBlockEntity bare = (BurnerDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(8, 1, 8)));
        bare.addFuel("minecraft:coal", 1);
        h.runAfterDelay(25, () -> {
            expect(h, synced(h, onOre), OreType.IRON.item(), AltStatus.NO_FUEL);
            expect(h, synced(h, bare), null, AltStatus.NO_ORE);
            onOre.addFuel("minecraft:coal", 1);
            h.runAfterDelay(15, () -> {
                expect(h, synced(h, onOre), OreType.IRON.item(), AltStatus.NONE);
                h.succeed();
            });
        });
    }

    /** An assembler without a recipe warns NO_RECIPE; with one but without a pole, NO_POWER with the product as icon; an electric drill without power, NO_POWER. */
    @GameTest(template = "empty16", timeoutTicks = 60)
    public static void electricMachinesWarnWithoutPower(GameTestHelper h) {
        AssemblerBlockEntity none = MachineGameTest.assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), AT, null);
        AssemblerBlockEntity gears = MachineGameTest.assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), new BlockPos(8, 1, 2), "iron_gear_wheel");
        DrillGameTest.field(h, OreType.COPPER, 1, 7, 5, 11, 1000);
        place(h, ModItems.ELECTRIC_MINING_DRILL.get(), new BlockPos(2, 1, 8), Direction.EAST);
        ElectricDrillBlockEntity drill = (ElectricDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 8)));
        h.runAfterDelay(25, () -> {
            expect(h, synced(h, none), null, AltStatus.NO_RECIPE);
            expect(h, synced(h, gears), "minetorio:iron_gear_wheel", AltStatus.NO_POWER);
            expect(h, synced(h, drill), OreType.COPPER.item(), AltStatus.NO_POWER);
            h.succeed();
        });
    }
}
