package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.dropped;
import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.container;
import static dev.minetorio.gametest.InserterGameTest.count;
import static dev.minetorio.gametest.InserterGameTest.handler;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.deposit.Deposits;
import dev.minetorio.content.deposit.ScannerItem;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.deposit.Deposit;
import dev.minetorio.core.deposit.OreType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class DrillGameTest {
    private static final String COAL = "minecraft:coal";
    /** Test deposit ids: far below the starting deposits' -1..-4. */
    private static final AtomicLong NEXT_ID = new AtomicLong(-1_000_000);

    /** Burner drill at (2,1,2) facing east: footprint x 2..3, z 2..3; output cell (4,1,2). */
    private static final BlockPos BURNER = new BlockPos(2, 1, 2);
    private static final BlockPos BURNER_OUT = new BlockPos(4, 1, 2);
    /** Electric drill at (2,1,2) facing east: footprint x 2..4, z 2..4; output cell (5,1,3). */
    private static final BlockPos ELECTRIC = new BlockPos(2, 1, 2);
    private static final BlockPos ELECTRIC_OUT = new BlockPos(5, 1, 3);

    // --- helpers ---------------------------------------------------------------------------------

    /**
     * Makes this test's area hold exactly one deposit (or none, if {@code type} is null) centered at
     * {@code relCenter}, radius 4, with {@code remaining} ore left.
     */
    @Nullable
    static Deposit deposit(GameTestHelper h, @Nullable OreType type, BlockPos relCenter, long remaining) {
        BlockPos c = h.absolutePos(relCenter);
        Deposit d = type == null ? null : new Deposit(NEXT_ID.decrementAndGet(), type, c.getX(), c.getZ(), 4, remaining);
        Deposits.overrideForTest(h.getLevel(), h.getBounds(), d, remaining);
        return d;
    }

    static BurnerDrillBlockEntity burnerDrill(GameTestHelper h, int coal) {
        place(h, ModItems.BURNER_MINING_DRILL.get(), BURNER, Direction.EAST);
        BurnerDrillBlockEntity be = (BurnerDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BURNER));
        if (coal > 0) h.assertTrue(be.addFuel(COAL, coal) == coal, "fuel refused");
        return be;
    }

    static long remaining(GameTestHelper h, Deposit d) {
        return Deposits.get(h.getLevel()).remaining(d);
    }

    // --- tests -----------------------------------------------------------------------------------

    /** 0.25 ore/s for 400 ticks = 5 ore (±1); 1 coal (4 MJ) lasts 533 ticks at 150 kW. */
    @GameTest(template = "empty16", timeoutTicks = 420)
    public static void burnerDrillMinesIntoChest(GameTestHelper h) {
        Deposit d = deposit(h, OreType.IRON, new BlockPos(3, 1, 3), 1000);
        h.setBlock(BURNER_OUT, Blocks.CHEST);
        BurnerDrillBlockEntity be = burnerDrill(h, 1);
        h.runAtTickTime(400, () -> {
            int ore = count(h, BURNER_OUT, ModItems.IRON_ORE.get());
            h.assertTrue(ore >= 4 && ore <= 6, "mined " + ore);
            int held = be.output().held() == null ? 0 : 1;
            h.assertTrue(remaining(h, d) == 1000 - ore - held, "ledger " + remaining(h, d) + " vs " + ore + "+" + held);
            h.assertTrue(be.isWorking(), "not working");
            h.succeed();
        });
    }

    /** Remaining 3 → exactly 3 ore, then the drill stands (and burns nothing more). */
    @GameTest(template = "empty16", timeoutTicks = 420)
    public static void depletionStopsDrill(GameTestHelper h) {
        Deposit d = deposit(h, OreType.IRON, new BlockPos(3, 1, 3), 3);
        h.setBlock(BURNER_OUT, Blocks.CHEST);
        BurnerDrillBlockEntity be = burnerDrill(h, 5);
        h.runAtTickTime(400, () -> {
            h.assertTrue(count(h, BURNER_OUT, ModItems.IRON_ORE.get()) == 3, "mined " + count(h, BURNER_OUT, ModItems.IRON_ORE.get()));
            h.assertTrue(remaining(h, d) == 0, "remaining " + remaining(h, d));
            h.assertTrue(be.output().held() == null, "buffer holds " + be.output().held());
            h.assertFalse(be.isWorking(), "still working");
            long left = be.burner().remainingJ();
            int fuel = be.burner().fuel().getOrDefault(COAL, 0);
            h.runAfterDelay(20, () -> {
                h.assertTrue(be.burner().remainingJ() == left && be.burner().fuel().getOrDefault(COAL, 0) == fuel, "burns while depleted");
                h.succeed();
            });
        });
    }

    /** No deposit under the drill: nothing comes out and no fuel is burnt. */
    @GameTest(template = "empty16", timeoutTicks = 220)
    public static void noDepositNoOutput(GameTestHelper h) {
        deposit(h, null, new BlockPos(3, 1, 3), 0);
        h.setBlock(BURNER_OUT, Blocks.CHEST);
        BurnerDrillBlockEntity be = burnerDrill(h, 1);
        h.runAtTickTime(200, () -> {
            IItemHandler chest = handler(h, BURNER_OUT);
            for (int i = 0; i < chest.getSlots(); i++) h.assertTrue(chest.getStackInSlot(i).isEmpty(), "chest got " + chest.getStackInSlot(i));
            h.assertTrue(be.output().held() == null, "buffer holds " + be.output().held());
            h.assertTrue(be.burner().fuel().getOrDefault(COAL, 0) == 1 && be.burner().remainingJ() == 0, "fuel burnt");
            h.assertFalse(be.isWorking(), "working without a deposit");
            h.succeed();
        });
    }

    /**
     * Full chest: the ore waits in the 1-item buffer, the ledger is debited exactly one more than
     * the chest holds; the buffer survives save/load and drops when the drill is broken.
     */
    @GameTest(template = "empty16", timeoutTicks = 220)
    public static void drillWaitsWhenBlocked(GameTestHelper h) {
        Deposit d = deposit(h, OreType.IRON, new BlockPos(3, 1, 3), 1000);
        List<ItemStack> stone = new ArrayList<>();
        for (int i = 0; i < 27; i++) stone.add(new ItemStack(Items.STONE, 64));
        container(h, Blocks.CHEST, BURNER_OUT, stone.toArray(ItemStack[]::new));
        BurnerDrillBlockEntity be = burnerDrill(h, 5);
        h.runAtTickTime(200, () -> {
            int inChest = count(h, BURNER_OUT, ModItems.IRON_ORE.get());
            h.assertTrue(inChest == 0, "full chest took " + inChest);
            h.assertTrue("minetorio:iron_ore".equals(be.output().held()), "buffer holds " + be.output().held());
            h.assertTrue(remaining(h, d) == 1000 - inChest - 1, "ledger remaining " + remaining(h, d));
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
            h.succeed();
        });
    }

    /** Without a pole nearby the electric drill does nothing; once a powered pole covers it, it mines (0.5/s). */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void electricDrillNeedsPower(GameTestHelper h) {
        Deposit d = deposit(h, OreType.COPPER, new BlockPos(3, 1, 3), 1000);
        h.setBlock(ELECTRIC_OUT, Blocks.CHEST);
        place(h, ModItems.ELECTRIC_MINING_DRILL.get(), ELECTRIC, Direction.EAST);
        ElectricDrillBlockEntity be = (ElectricDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ELECTRIC));
        h.runAtTickTime(100, () -> {
            h.assertTrue(count(h, ELECTRIC_OUT, ModItems.COPPER_ORE.get()) == 0, "mined without power");
            h.assertTrue(remaining(h, d) == 1000, "ledger changed without power");
            h.assertFalse(be.isWorking(), "working without power");
            h.assertTrue(be.satisfaction() == 0, "satisfaction " + be.satisfaction());
            h.assertTrue(be.demand() > 0, "no demand");
            TestPower.powerAt(h, new BlockPos(3, 1, 6)); // supply area x 1..5, z 4..8: covers the drill's z=4 row
        });
        h.runAtTickTime(190, () -> {
            int ore = count(h, ELECTRIC_OUT, ModItems.COPPER_ORE.get());
            h.assertTrue(ore >= 2, "mined with power: " + ore); // ~88 powered ticks at 0.5/s ≈ 2.2
            h.assertTrue(be.satisfaction() == 1.0, "satisfaction " + be.satisfaction());
            h.succeed();
        });
    }

    /** A player standing over a deposit is told its ore and how much is left. */
    @GameTest(template = "empty16")
    public static void scannerMessages(GameTestHelper h) {
        deposit(h, OreType.IRON, new BlockPos(8, 1, 8), 12_345);
        List<Component> messages = new ArrayList<>();
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 8));
        Player player = new Player(h.getLevel(), at, 0, new GameProfile(UUID.randomUUID(), "scanner-test")) {
            @Override
            public boolean isSpectator() { return false; }

            @Override
            public boolean isCreative() { return false; }

            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
                if (actionBar) messages.add(message);
            }
        };
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ItemStack scanner = new ItemStack(ModItems.SCANNER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, scanner);
        scanner.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(messages.size() == 1, "messages " + messages);
        String text = messages.get(0).getString();
        h.assertTrue(text.toLowerCase().contains("iron"), "no ore name: " + text);
        h.assertTrue(text.contains("12 345"), "no amount: " + text);
        h.succeed();
    }

    /** Deposits exist only in the Overworld: elsewhere nothing is found and nothing can be mined. */
    @GameTest(template = "empty16")
    public static void otherDimensionsHaveNoDeposits(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "no nether");
        Deposits deposits = Deposits.get(nether);
        h.assertTrue(deposits.depositUnder(-50, -50, 50, 50).isEmpty(), "deposit in the nether");
        h.assertTrue(deposits.nearest(0, 0, Deposits.SCAN_RADIUS).isEmpty(), "nearest in the nether");
        String text = ScannerItem.message(nether, 0, 0).getString();
        h.assertTrue(text.contains("256"), "unexpected scan in the nether: " + text);
        h.succeed();
    }
}
