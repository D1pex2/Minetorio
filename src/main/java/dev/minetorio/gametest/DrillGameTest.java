package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.dropped;
import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.container;
import static dev.minetorio.gametest.InserterGameTest.count;
import static dev.minetorio.gametest.InserterGameTest.handler;

import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.orefield.OreFields;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.orefield.OreType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Mining drills on ore fields: drills stand on the floor (y = 1) and mine the field blocks of the floor (y = 0). */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class DrillGameTest {
    private static final String COAL = "minecraft:coal";

    /** Burner drill at (2,1,2) facing east: footprint x 2..3, z 2..3; output cell (4,1,2). */
    private static final BlockPos BURNER = new BlockPos(2, 1, 2);
    private static final BlockPos BURNER_OUT = new BlockPos(4, 1, 2);
    /** Electric drill at (2,1,2) facing east: footprint x 2..4, z 2..4 (area x 1..5, z 1..5); output cell (5,1,3). */
    private static final BlockPos ELECTRIC = new BlockPos(2, 1, 2);
    private static final BlockPos ELECTRIC_OUT = new BlockPos(5, 1, 3);

    // --- helpers ---------------------------------------------------------------------------------

    /** Floor field blocks of {@code type} with {@code amount} ore each over relative x0..x1, z0..z1 (y = 0). */
    static void field(GameTestHelper h, OreType type, int x0, int z0, int x1, int z1, long amount) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) OreFields.place(h.getLevel(), h.absolutePos(new BlockPos(x, 0, z)), type, amount);
        }
    }

    static long remaining(GameTestHelper h, BlockPos rel) {
        return OreFields.remaining(h.getLevel(), h.absolutePos(rel));
    }

    static boolean isField(GameTestHelper h, BlockPos rel) {
        return OreFields.typeOf(h.getBlockState(rel)) != null;
    }

    static BurnerDrillBlockEntity burnerDrill(GameTestHelper h, int coal) {
        place(h, ModItems.BURNER_MINING_DRILL.get(), BURNER, Direction.EAST);
        BurnerDrillBlockEntity be = (BurnerDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BURNER));
        if (coal > 0) h.assertTrue(be.addFuel(COAL, coal) == coal, "fuel refused");
        return be;
    }

    // --- tests -----------------------------------------------------------------------------------

    /**
     * 0.25 ore/s for 400 ticks = 5 ore (±1); 1 coal (4 MJ) lasts 533 ticks at 150 kW. All four blocks
     * are equally near the center: the first in row order, (2,0,2), is mined alone.
     */
    @GameTest(template = "empty16", timeoutTicks = 420)
    public static void burnerDrillMinesIntoChest(GameTestHelper h) {
        field(h, OreType.IRON, 2, 2, 3, 3, 1000);
        h.setBlock(BURNER_OUT, Blocks.CHEST);
        BurnerDrillBlockEntity be = burnerDrill(h, 1);
        h.runAtTickTime(400, () -> {
            int ore = count(h, BURNER_OUT, ModItems.IRON_ORE.get());
            h.assertTrue(ore >= 4 && ore <= 6, "mined " + ore);
            int held = be.output().held() == null ? 0 : 1;
            BlockPos first = new BlockPos(2, 0, 2);
            h.assertTrue(remaining(h, first) == 1000 - ore - held, "block " + remaining(h, first) + " vs " + ore + "+" + held);
            for (BlockPos other : List.of(new BlockPos(3, 0, 2), new BlockPos(2, 0, 3), new BlockPos(3, 0, 3))) {
                h.assertTrue(remaining(h, other) == 1000, "touched " + other + ": " + remaining(h, other));
            }
            h.assertTrue(be.isWorking(), "not working");
            h.succeed();
        });
    }

    /**
     * Four blocks of 1 ore under the 2×2 drill (and one beside it): exactly 4 ore, the four blocks
     * turn into dirt one by one, the one beside stays; then the drill stands and burns nothing more.
     */
    @GameTest(template = "empty16", timeoutTicks = 460)
    public static void burnerDrillExhaustsItsTwoByTwo(GameTestHelper h) {
        field(h, OreType.IRON, 2, 2, 3, 3, 1);
        field(h, OreType.IRON, 1, 2, 1, 2, 1); // beside the footprint
        h.setBlock(BURNER_OUT, Blocks.CHEST);
        BurnerDrillBlockEntity be = burnerDrill(h, 5);
        h.runAtTickTime(400, () -> {
            h.assertTrue(count(h, BURNER_OUT, ModItems.IRON_ORE.get()) == 4, "mined " + count(h, BURNER_OUT, ModItems.IRON_ORE.get()));
            for (int x = 2; x <= 3; x++) {
                for (int z = 2; z <= 3; z++) {
                    BlockPos p = new BlockPos(x, 0, z);
                    h.assertTrue(h.getBlockState(p).is(Blocks.DIRT), "not dirt at " + p + ": " + h.getBlockState(p));
                }
            }
            h.assertTrue(isField(h, new BlockPos(1, 0, 2)) && remaining(h, new BlockPos(1, 0, 2)) == 1, "mined outside the 2×2");
            h.assertTrue(be.output().held() == null, "buffer holds " + be.output().held());
            h.assertFalse(be.isWorking(), "still working");
            h.assertTrue(be.status().getContents() instanceof TranslatableContents t && t.getKey().equals("minetorio.drill.no_ore"),
                    "status " + be.status());
            long left = be.burner().remainingJ();
            int fuel = be.burner().fuel().getOrDefault(COAL, 0);
            h.runAfterDelay(30, () -> {
                h.assertTrue(be.burner().remainingJ() == left && be.burner().fuel().getOrDefault(COAL, 0) == fuel, "burns while idle");
                h.succeed();
            });
        });
    }

    /** No field under the drill: nothing comes out and no fuel is burnt. */
    @GameTest(template = "empty16", timeoutTicks = 220)
    public static void noFieldNoOutput(GameTestHelper h) {
        h.setBlock(BURNER_OUT, Blocks.CHEST);
        BurnerDrillBlockEntity be = burnerDrill(h, 1);
        h.runAtTickTime(200, () -> {
            IItemHandler chest = handler(h, BURNER_OUT);
            for (int i = 0; i < chest.getSlots(); i++) h.assertTrue(chest.getStackInSlot(i).isEmpty(), "chest got " + chest.getStackInSlot(i));
            h.assertTrue(be.output().held() == null, "buffer holds " + be.output().held());
            h.assertTrue(be.burner().fuel().getOrDefault(COAL, 0) == 1 && be.burner().remainingJ() == 0, "fuel burnt");
            h.assertFalse(be.isWorking(), "working without a field");
            h.assertTrue(be.target() == null, "target " + be.target());
            h.succeed();
        });
    }

    /** A field placed under an idle drill later is found within the rescan interval. */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void idleDrillFindsANewField(GameTestHelper h) {
        h.setBlock(BURNER_OUT, Blocks.CHEST);
        BurnerDrillBlockEntity be = burnerDrill(h, 1);
        h.runAtTickTime(40, () -> field(h, OreType.COAL, 3, 3, 3, 3, 50));
        h.runAtTickTime(180, () -> {
            h.assertTrue(count(h, BURNER_OUT, Items.COAL) >= 1, "mined nothing from the new field");
            h.assertTrue(remaining(h, new BlockPos(3, 0, 3)) < 50, "field untouched");
            h.succeed();
        });
    }

    /**
     * Full chest: the ore waits in the 1-item buffer, the field block is debited exactly one more than
     * the chest holds; the buffer survives save/load and drops when the drill is broken.
     */
    @GameTest(template = "empty16", timeoutTicks = 220)
    public static void drillWaitsWhenBlocked(GameTestHelper h) {
        field(h, OreType.IRON, 2, 2, 3, 3, 1000);
        List<ItemStack> stone = new ArrayList<>();
        for (int i = 0; i < 27; i++) stone.add(new ItemStack(Items.STONE, 64));
        container(h, Blocks.CHEST, BURNER_OUT, stone.toArray(ItemStack[]::new));
        BurnerDrillBlockEntity be = burnerDrill(h, 5);
        h.runAtTickTime(200, () -> {
            int inChest = count(h, BURNER_OUT, ModItems.IRON_ORE.get());
            h.assertTrue(inChest == 0, "full chest took " + inChest);
            h.assertTrue("minetorio:iron_ore".equals(be.output().held()), "buffer holds " + be.output().held());
            h.assertTrue(remaining(h, new BlockPos(2, 0, 2)) == 1000 - 1, "block remaining " + remaining(h, new BlockPos(2, 0, 2)));
            h.assertFalse(be.isWorking(), "working while blocked");

            var registries = h.getLevel().registryAccess();
            BurnerDrillBlockEntity copy = new BurnerDrillBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.loadWithComponents(be.saveWithoutMetadata(registries), registries);
            h.assertTrue("minetorio:iron_ore".equals(copy.output().held()), "buffer not saved");
            h.assertTrue(copy.burner().fuel().equals(be.burner().fuel()), "fuel not saved");

            h.getLevel().destroyBlock(h.absolutePos(BURNER), true);
            h.assertTrue(dropped(h, ModItems.IRON_ORE.get()) == 1, "dropped ore " + dropped(h, ModItems.IRON_ORE.get()));
            h.assertTrue(dropped(h, ModItems.BURNER_MINING_DRILL.get()) == 1, "drill item not dropped");
            h.assertTrue(dropped(h, Items.COAL) >= 1, "fuel not dropped");
            h.assertTrue(isField(h, new BlockPos(2, 0, 2)), "field block gone with the drill");
            h.succeed();
        });
    }

    /** Without a pole nearby the electric drill does nothing; once a powered pole covers it, it mines (0.5/s). */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void electricDrillNeedsPower(GameTestHelper h) {
        field(h, OreType.COPPER, 1, 1, 5, 5, 1000);
        h.setBlock(ELECTRIC_OUT, Blocks.CHEST);
        place(h, ModItems.ELECTRIC_MINING_DRILL.get(), ELECTRIC, Direction.EAST);
        ElectricDrillBlockEntity be = (ElectricDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ELECTRIC));
        h.runAtTickTime(100, () -> {
            h.assertTrue(count(h, ELECTRIC_OUT, ModItems.COPPER_ORE.get()) == 0, "mined without power");
            h.assertTrue(remaining(h, new BlockPos(3, 0, 3)) == 1000, "field changed without power");
            h.assertFalse(be.isWorking(), "working without power");
            h.assertTrue(be.satisfaction() == 0, "satisfaction " + be.satisfaction());
            h.assertTrue(be.demand() > 0, "no demand");
            TestPower.powerAt(h, new BlockPos(3, 1, 6)); // supply area x 1..5, z 4..8: covers the drill's z=4 row
        });
        h.runAtTickTime(190, () -> {
            int ore = count(h, ELECTRIC_OUT, ModItems.COPPER_ORE.get());
            h.assertTrue(ore >= 2, "mined with power: " + ore); // ~88 powered ticks at 0.5/s ≈ 2.2
            h.assertTrue(be.satisfaction() == 1.0, "satisfaction " + be.satisfaction());
            h.assertTrue(remaining(h, new BlockPos(3, 0, 3)) == 1000 - ore - (be.output().held() == null ? 0 : 1),
                    "center block not mined first: " + remaining(h, new BlockPos(3, 0, 3)));
            h.succeed();
        });
    }

    /**
     * The electric drill's area is 5×5 around its center: field blocks of 1 ore at the four corners of
     * that area are mined (and turn into dirt), blocks just outside it are not.
     */
    @GameTest(template = "empty16", timeoutTicks = 300)
    public static void electricDrillCoversFiveByFive(GameTestHelper h) {
        List<BlockPos> corners = List.of(new BlockPos(1, 0, 1), new BlockPos(5, 0, 1), new BlockPos(1, 0, 5), new BlockPos(5, 0, 5));
        List<BlockPos> outside = List.of(new BlockPos(0, 0, 3), new BlockPos(6, 0, 3), new BlockPos(3, 0, 0), new BlockPos(3, 0, 6));
        for (BlockPos c : corners) field(h, OreType.STONE, c.getX(), c.getZ(), c.getX(), c.getZ(), 1);
        for (BlockPos c : outside) field(h, OreType.STONE, c.getX(), c.getZ(), c.getX(), c.getZ(), 1);
        h.setBlock(ELECTRIC_OUT, Blocks.CHEST);
        place(h, ModItems.ELECTRIC_MINING_DRILL.get(), ELECTRIC, Direction.EAST);
        TestPower.powerAt(h, new BlockPos(3, 1, 6)); // supply area x 1..5, z 4..8: covers the drill's z=4 row
        h.runAtTickTime(250, () -> {
            h.assertTrue(count(h, ELECTRIC_OUT, Items.COBBLESTONE) == 4, "mined " + count(h, ELECTRIC_OUT, Items.COBBLESTONE));
            for (BlockPos c : corners) h.assertTrue(h.getBlockState(c).is(Blocks.DIRT), "corner not mined: " + c);
            for (BlockPos c : outside) h.assertTrue(isField(h, c), "mined outside the area: " + c);
            h.succeed();
        });
    }
}
