package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.dropped;
import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.container;
import static dev.minetorio.gametest.InserterGameTest.count;
import static dev.minetorio.gametest.InserterGameTest.handler;
import static dev.minetorio.gametest.InserterGameTest.placeInserter;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.inserter.InserterEndpoints;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity.SelectResult;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.StoneFurnaceBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.deposit.OreType;
import dev.minetorio.core.recipe.Recipe;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class MachineGameTest {
    private static final String PLATE = "minetorio:iron_plate";
    private static final String GEAR = "minetorio:iron_gear_wheel";
    /** Furnace at (2,1,2) facing east: footprint x 2..3, z 2..3. */
    private static final BlockPos FURNACE = new BlockPos(2, 1, 2);
    /** Assembler at (2,1,2) facing east: footprint x 2..4, z 2..4. */
    private static final BlockPos ASSEMBLER = new BlockPos(2, 1, 2);

    // --- helpers ---------------------------------------------------------------------------------

    static StoneFurnaceBlockEntity furnace(GameTestHelper h, BlockPos rel) {
        place(h, ModItems.STONE_FURNACE.get(), rel, Direction.EAST);
        return (StoneFurnaceBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    static AssemblerBlockEntity assembler(GameTestHelper h, Item item, BlockPos rel, String recipe) {
        place(h, item, rel, Direction.EAST);
        AssemblerBlockEntity be = (AssemblerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        if (recipe != null) be.setRecipe(Phase1Data.recipes().get(recipe).orElseThrow());
        return be;
    }

    /** Inserts through the machine's item capability (automated) and asserts all of it was accepted. */
    static void insert(GameTestHelper h, BlockPos rel, int slot, ItemStack stack) {
        ItemStack rest = handler(h, rel).insertItem(slot, stack, false);
        h.assertTrue(rest.isEmpty(), "refused " + rest + " into slot " + slot);
    }

    /**
     * Records the duration (last tick minus first tick of a craft) of the first craft of
     * {@code be}: [0] = tick the craft was first seen running, [1] = tick the first output was seen.
     */
    static long[] watchFirstCraft(GameTestHelper h, AssemblerBlockEntity be) {
        long[] ticks = {-1, -1};
        h.onEachTick(() -> {
            if (ticks[0] < 0 && be.machine().isCrafting()) ticks[0] = h.getTick();
            if (ticks[1] < 0 && be.machine().outputs().getOrDefault(GEAR, 0) > 0) ticks[1] = h.getTick();
        });
        return ticks;
    }

    static FakePlayer player(GameTestHelper h, BlockPos rel) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "machine-test"));
        BlockPos at = h.absolutePos(rel);
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    static int inInventory(Player player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    // --- furnace ---------------------------------------------------------------------------------

    /** Bootstrap: 1 raw iron + 1 coal → 1 iron plate after 3.2 s (64 ticks); the furnace is lit while smelting. */
    @GameTest(template = "empty16", timeoutTicks = 100)
    public static void furnaceSmeltsRawIron(GameTestHelper h) {
        StoneFurnaceBlockEntity be = furnace(h, FURNACE);
        insert(h, FURNACE, StoneFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));
        insert(h, FURNACE, StoneFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON));
        AtomicLong doneAt = new AtomicLong(-1);
        h.onEachTick(() -> {
            if (doneAt.get() < 0 && !be.machine().outputs().isEmpty()) doneAt.set(h.getTick());
        });
        h.runAtTickTime(30, () -> h.assertTrue(be.isWorking(), "not lit while smelting"));
        h.runAtTickTime(90, () -> {
            h.assertTrue(doneAt.get() >= 62 && doneAt.get() <= 68, "plate after " + doneAt.get() + " ticks");
            h.assertTrue(be.machine().outputs().equals(Map.of(PLATE, 1)), "outputs " + be.machine().outputs());
            h.assertTrue(be.machine().inputs().isEmpty(), "inputs " + be.machine().inputs());
            IItemHandler items = handler(h, FURNACE);
            h.assertTrue(items.extractItem(StoneFurnaceBlockEntity.INPUT_SLOT, 1, false).isEmpty(), "extracted from the input slot");
            ItemStack out = items.extractItem(StoneFurnaceBlockEntity.OUTPUT_SLOT, 64, false);
            h.assertTrue(out.is(ModItems.IRON_PLATE.get()) && out.getCount() == 1, "extracted " + out);
            h.assertFalse(be.isWorking(), "still lit after the grace period");
            h.succeed();
        });
    }

    /**
     * Deposit ore and cobblestone: iron_ore → iron_plate, 2 cobblestone → stone_brick. Automated
     * inserts stop at 2 crafts of input and 5 fuel; the furnace's state survives save/load.
     */
    @GameTest(template = "empty16", timeoutTicks = 100)
    public static void furnaceSmeltsDepositOre(GameTestHelper h) {
        StoneFurnaceBlockEntity ore = furnace(h, FURNACE);
        BlockPos bricksAt = new BlockPos(6, 1, 2);
        StoneFurnaceBlockEntity bricks = furnace(h, bricksAt);
        insert(h, FURNACE, StoneFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 5));
        ItemStack sixth = handler(h, FURNACE).insertItem(StoneFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL), false);
        h.assertTrue(sixth.getCount() == 1, "automated fuel above 5");
        ItemStack ores = handler(h, FURNACE).insertItem(StoneFurnaceBlockEntity.INPUT_SLOT, new ItemStack(ModItems.IRON_ORE.get(), 5), false);
        h.assertTrue(ores.getCount() == 3, "automated input cap: left " + ores);
        insert(h, bricksAt, StoneFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));
        insert(h, bricksAt, StoneFurnaceBlockEntity.INPUT_SLOT, new ItemStack(Items.COBBLESTONE, 2));

        h.runAtTickTime(20, () -> {
            var registries = h.getLevel().registryAccess();
            StoneFurnaceBlockEntity copy = new StoneFurnaceBlockEntity(ore.getBlockPos(), ore.getBlockState());
            copy.loadWithComponents(ore.saveWithoutMetadata(registries), registries);
            h.assertTrue(copy.machine().recipe() == ore.machine().recipe(), "recipe not saved");
            h.assertTrue(copy.machine().progress() == ore.machine().progress() && copy.machine().isCrafting(), "progress not saved");
            h.assertTrue(copy.machine().inputs().equals(ore.machine().inputs()), "inputs not saved");
            h.assertTrue(copy.burner().fuel().equals(ore.burner().fuel())
                    && copy.burner().remainingJ() == ore.burner().remainingJ(), "burner not saved");
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(ore.machine().outputs().equals(Map.of(PLATE, 1)), "ore outputs " + ore.machine().outputs());
            h.assertTrue(bricks.machine().outputs().equals(Map.of("minetorio:stone_brick", 1)), "brick outputs " + bricks.machine().outputs());
            h.succeed();
        });
    }

    /** Stacks/keys with data components never enter a machine (stored by id, the data would be lost). */
    @GameTest(template = "empty16")
    public static void machinesRefuseItemsWithComponents(GameTestHelper h) {
        StoneFurnaceBlockEntity furnace = furnace(h, FURNACE);
        BlockPos assemblerAt = new BlockPos(6, 1, 2);
        AssemblerBlockEntity assembler = assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), assemblerAt, "iron_gear_wheel");
        ItemStack namedOre = new ItemStack(ModItems.IRON_ORE.get());
        namedOre.set(DataComponents.CUSTOM_NAME, Component.literal("special"));
        ItemStack namedCoal = new ItemStack(Items.COAL);
        namedCoal.set(DataComponents.CUSTOM_NAME, Component.literal("special"));
        ItemStack namedPlate = new ItemStack(ModItems.IRON_PLATE.get());
        namedPlate.set(DataComponents.CUSTOM_NAME, Component.literal("special"));

        h.assertTrue(handler(h, FURNACE).insertItem(StoneFurnaceBlockEntity.INPUT_SLOT, namedOre, false).getCount() == 1, "named ore accepted");
        h.assertTrue(handler(h, FURNACE).insertItem(StoneFurnaceBlockEntity.FUEL_SLOT, namedCoal, false).getCount() == 1, "named coal accepted");
        h.assertTrue(handler(h, assemblerAt).insertItem(0, namedPlate, false).getCount() == 1, "named plate accepted");
        var registries = h.getLevel().registryAccess();
        String oreKey = LaneStacks.key(namedOre, registries);
        String plateKey = LaneStacks.key(namedPlate, registries);
        h.assertFalse(furnace.asTarget().wants(oreKey) || furnace.asTarget().accept(oreKey), "inserter key with data accepted by furnace");
        h.assertFalse(assembler.asTarget().wants(plateKey) || assembler.asTarget().accept(plateKey), "inserter key with data accepted by assembler");
        h.assertTrue(furnace.machine().inputs().isEmpty() && furnace.burner().fuel().isEmpty(), "furnace changed");
        h.assertTrue(assembler.machine().inputs().isEmpty(), "assembler changed");
        h.assertTrue(InserterEndpoints.target(h.getLevel(), h.absolutePos(FURNACE), Direction.WEST).wants("minetorio:iron_ore"), "plain ore refused");
        h.succeed();
    }

    // --- assembler -------------------------------------------------------------------------------

    /** Assembling machine 1 on a grid, gear recipe, an inserter feeding plates: each gear takes 20 ticks (0.5 s / 0.5). */
    @GameTest(template = "empty16", timeoutTicks = 300)
    public static void assemblerMakesGearsAtFullPower(GameTestHelper h) {
        AssemblerBlockEntity be = assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), ASSEMBLER, "iron_gear_wheel");
        container(h, Blocks.CHEST, new BlockPos(0, 1, 3), new ItemStack(ModItems.IRON_PLATE.get(), 6));
        placeInserter(h, ModItems.INSERTER.get(), new BlockPos(1, 1, 3), Direction.EAST); // its pole also covers the assembler
        long[] craft = watchFirstCraft(h, be);
        h.succeedWhen(() -> {
            h.assertTrue(be.machine().outputs().getOrDefault(GEAR, 0) == 3, "gears " + be.machine().outputs());
            h.assertTrue(craft[1] - craft[0] == 19, "first craft ran from tick " + craft[0] + " to " + craft[1]);
            h.assertTrue(be.satisfaction() == 1.0, "satisfaction " + be.satisfaction());
            ItemStack out = handler(h, ASSEMBLER).extractItem(AssemblerBlockEntity.OUTPUT_SLOT, 64, false);
            h.assertTrue(out.is(ModItems.IRON_GEAR_WHEEL.get()) && out.getCount() == 3, "extracted " + out);
        });
    }

    /** A grid that can supply half the demand: satisfaction 0.5, each gear takes 40 ticks. */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void assemblerHalfPowerHalfSpeed(GameTestHelper h) {
        AssemblerBlockEntity be = assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), ASSEMBLER, "iron_gear_wheel");
        TestPower.limitedPowerAt(h, new BlockPos(3, 1, 6), AssemblerBlockEntity.POWER_1 / 2); // supply area z 4..8
        h.assertTrue(be.machine().insertInput(PLATE, 4, false) == 4, "plates refused");
        long[] craft = watchFirstCraft(h, be);
        h.runAtTickTime(30, () -> h.assertTrue(be.isWorking(), "not working at half power"));
        h.succeedWhen(() -> {
            h.assertTrue(be.machine().outputs().getOrDefault(GEAR, 0) == 2, "gears " + be.machine().outputs());
            h.assertTrue(craft[1] - craft[0] == 39, "first craft ran from tick " + craft[0] + " to " + craft[1]);
            h.assertTrue(Math.abs(be.satisfaction() - 0.5) < 1e-9 || be.demand() == 0, "satisfaction " + be.satisfaction());
        });
    }

    /** Recipe, inputs, outputs and craft progress survive save/load (assembling machine 2). */
    @GameTest(template = "empty16", timeoutTicks = 100)
    public static void assemblerNbtRoundTrip(GameTestHelper h) {
        AssemblerBlockEntity be = assembler(h, ModItems.ASSEMBLING_MACHINE_2.get(), ASSEMBLER, "iron_gear_wheel");
        TestPower.powerAt(h, new BlockPos(3, 1, 6));
        be.machine().insertInput(PLATE, 7, false);
        h.runAtTickTime(25, () -> { // 0.75 speed: a gear per ~13.3 ticks → 1 done, 1 in progress
            h.assertTrue(be.machine().isCrafting() && be.machine().progress() > 0, "no craft in progress");
            h.assertTrue(!be.machine().outputs().isEmpty(), "no output yet");
            var registries = h.getLevel().registryAccess();
            AssemblerBlockEntity copy = new AssemblerBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.loadWithComponents(be.saveWithoutMetadata(registries), registries);
            h.assertTrue(copy.machine().recipe() == be.machine().recipe(), "recipe " + copy.machine().recipe());
            h.assertTrue(copy.machine().inputs().equals(be.machine().inputs()), "inputs " + copy.machine().inputs());
            h.assertTrue(copy.machine().outputs().equals(be.machine().outputs()), "outputs " + copy.machine().outputs());
            h.assertTrue(copy.machine().isCrafting(), "craft in progress lost");
            h.assertTrue(copy.machine().progress() == be.machine().progress(), "progress " + copy.machine().progress());
            h.succeed();
        });
    }

    /**
     * Recipe selection from the network is validated: smelting recipes, unknown ids, other blocks
     * and far players are refused; a change gives the machine's items back to the player.
     */
    @GameTest(template = "empty16")
    public static void selectRecipePayloadRejectsSmelting(GameTestHelper h) {
        AssemblerBlockEntity be = assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), ASSEMBLER, null);
        BlockPos pos = h.absolutePos(ASSEMBLER);
        FakePlayer near = player(h, new BlockPos(3, 1, 7));
        h.assertTrue(AssemblerBlockEntity.select(near, pos, "iron_plate") == SelectResult.NOT_CRAFTING, "smelting accepted");
        h.assertTrue(AssemblerBlockEntity.select(near, pos, "copper_plate_from_raw_copper") == SelectResult.NOT_CRAFTING, "smelting accepted");
        h.assertTrue(be.machine().recipe() == null, "recipe set to " + be.machine().recipe());
        h.assertTrue(AssemblerBlockEntity.select(near, pos, "no_such_recipe") == SelectResult.UNKNOWN_RECIPE, "unknown accepted");
        h.assertTrue(AssemblerBlockEntity.select(near, h.absolutePos(new BlockPos(8, 1, 8)), "iron_gear_wheel") == SelectResult.NOT_ASSEMBLER,
                "air accepted");
        FakePlayer far = player(h, new BlockPos(3, 1, 15));
        far.setPos(far.getX(), far.getY(), far.getZ() + 20);
        h.assertTrue(AssemblerBlockEntity.select(far, pos, "iron_gear_wheel") == SelectResult.TOO_FAR, "far player accepted");
        h.assertTrue(be.machine().recipe() == null, "recipe set to " + be.machine().recipe());

        h.assertTrue(AssemblerBlockEntity.select(near, pos, "iron_gear_wheel") == SelectResult.OK, "gear refused");
        Recipe gear = Phase1Data.recipes().get("iron_gear_wheel").orElseThrow();
        h.assertTrue(be.machine().recipe() == gear, "recipe " + be.machine().recipe());
        be.machine().insertInput(PLATE, 3, false);
        h.assertTrue(AssemblerBlockEntity.select(near, pos, "pipe") == SelectResult.OK, "pipe refused");
        h.assertTrue(be.machine().inputs().isEmpty(), "inputs kept " + be.machine().inputs());
        h.assertTrue(inInventory(near, ModItems.IRON_PLATE.get()) == 3, "plates returned " + inInventory(near, ModItems.IRON_PLATE.get()));
        h.succeed();
    }

    /** Breaking an assembler drops its stored inputs, the inputs of the craft in progress and its outputs. */
    @GameTest(template = "empty16")
    public static void assemblerDropsContents(GameTestHelper h) {
        AssemblerBlockEntity be = assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), ASSEMBLER, "iron_gear_wheel");
        Recipe gear = Phase1Data.recipes().get("iron_gear_wheel").orElseThrow();
        be.machine().load(gear, Map.of(PLATE, 3), Map.of(GEAR, 2), Map.of(), true, 0.5);
        h.getLevel().destroyBlock(h.absolutePos(new BlockPos(4, 2, 4)), true); // a part
        h.assertTrue(dropped(h, ModItems.IRON_PLATE.get()) == 5, "plates dropped " + dropped(h, ModItems.IRON_PLATE.get()));
        h.assertTrue(dropped(h, ModItems.IRON_GEAR_WHEEL.get()) == 2, "gears dropped " + dropped(h, ModItems.IRON_GEAR_WHEEL.get()));
        h.assertTrue(dropped(h, ModItems.ASSEMBLING_MACHINE_1.get()) == 1, "machine item not dropped");
        h.succeed();
    }

    // --- integration -----------------------------------------------------------------------------

    /**
     * Deposit → burner drill → belt → inserter → furnace → inserter → assembler (gear) → inserter →
     * chest: gears arrive in the chest.
     */
    @GameTest(template = "empty16", timeoutTicks = 1200)
    public static void fullFactoryLine(GameTestHelper h) {
        DrillGameTest.deposit(h, OreType.IRON, new BlockPos(2, 1, 3), 1000);
        place(h, ModItems.BURNER_MINING_DRILL.get(), new BlockPos(1, 1, 2), Direction.EAST); // output cell (3,1,2)
        var drill = (BurnerDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 1, 2)));
        h.assertTrue(drill.addFuel("minecraft:coal", 5) == 5, "drill fuel refused");
        place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(3, 1, 2), Direction.EAST);
        place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(4, 1, 2), Direction.EAST);
        placeInserter(h, ModItems.INSERTER.get(), new BlockPos(5, 1, 2), Direction.EAST);
        BlockPos furnaceAt = new BlockPos(6, 1, 2); // x 6..7, z 2..3
        furnace(h, furnaceAt);
        insert(h, furnaceAt, StoneFurnaceBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 5));
        placeInserter(h, ModItems.INSERTER.get(), new BlockPos(8, 1, 2), Direction.EAST); // its pole covers the assembler
        AssemblerBlockEntity assembler = assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), new BlockPos(9, 1, 2), "iron_gear_wheel");
        placeInserter(h, ModItems.INSERTER.get(), new BlockPos(12, 1, 3), Direction.EAST);
        BlockPos chest = new BlockPos(13, 1, 3);
        h.setBlock(chest, Blocks.CHEST);
        h.succeedWhen(() -> h.assertTrue(count(h, chest, ModItems.IRON_GEAR_WHEEL.get()) >= 1,
                "no gear yet; assembler " + assembler.machine().inputs() + " / " + assembler.machine().outputs()));
    }
}
