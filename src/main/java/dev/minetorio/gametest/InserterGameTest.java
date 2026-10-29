package dev.minetorio.gametest;

import dev.minetorio.content.registry.BaseMaterials;
import static dev.minetorio.gametest.BeltGameTest.belt;
import static dev.minetorio.gametest.BeltGameTest.dropped;
import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.registry.ModItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class InserterGameTest {
    private static final String PLATE = "minetorio:iron_plate";
    private static final String COAL = "minetorio:coal";

    // --- helpers ---------------------------------------------------------------------------------

    static InserterBlockEntity inserter(GameTestHelper h, BlockPos rel) {
        return (InserterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    /** Places an inserter dropping toward {@code facing}; electric ones get a powered pole 2 blocks above. */
    static InserterBlockEntity placeInserter(GameTestHelper h, Item item, BlockPos rel, Direction facing) {
        place(h, item, rel, facing);
        InserterBlockEntity be = inserter(h, rel);
        if (!be.type().burner) TestPower.powerAt(h, rel.above(2));
        return be;
    }

    static IItemHandler handler(GameTestHelper h, BlockPos rel) {
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(rel), null);
    }

    /** Puts {@code block} (a container) at {@code rel} holding {@code contents}. */
    static void container(GameTestHelper h, Block block, BlockPos rel, ItemStack... contents) {
        h.setBlock(rel, block);
        IItemHandler handler = handler(h, rel);
        for (ItemStack s : contents) {
            ItemStack rest = s.copy();
            for (int i = 0; i < handler.getSlots() && !rest.isEmpty(); i++) rest = handler.insertItem(i, rest, false);
            h.assertTrue(rest.isEmpty(), "container full");
        }
    }

    static int count(GameTestHelper h, BlockPos rel, Item item) {
        IItemHandler handler = handler(h, rel);
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) n += handler.getStackInSlot(i).getCount();
        }
        return n;
    }

    static int onBelt(BeltBlockEntity b, LaneSide side) {
        return b.lane(side).items().size();
    }

    // --- tests -----------------------------------------------------------------------------------

    /** 5 plates, one per 24-tick cycle: the 5th arrives at 4*24 + 12 = 108 ticks (+ slack). */
    @GameTest(template = "empty16", timeoutTicks = 5 * 24 + 10)
    public static void basicChestToChest(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        BlockPos dst = new BlockPos(3, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 5));
        h.setBlock(dst, Blocks.CHEST);
        placeInserter(h, ModItems.INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        h.succeedWhen(() -> {
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 5, "delivered " + count(h, dst, ModItems.IRON_PLATE.get()));
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 0, "source not empty");
        });
    }

    /**
     * A fuelless burner inserter holding 1 coal keeps it as fuel, then works: the next coal it
     * picks up also stays (its fuel slot emptied when the first one started burning), after that
     * it carries coal. Nothing is lost: source + target + hand + fuel slot + 1 burning = 11.
     */
    @GameTest(template = "empty16", timeoutTicks = 300)
    public static void burnerRefuelsItself(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        BlockPos dst = new BlockPos(3, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(BaseMaterials.COAL.get(), 10));
        h.setBlock(dst, Blocks.CHEST);
        InserterBlockEntity be = placeInserter(h, ModItems.BURNER_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        h.assertTrue(be.burner().fuel().isEmpty() && be.burner().remainingJ() == 0, "starts without fuel");
        be.logic().load(Map.of("phase", "TO_DROP", "held", COAL, "leg", 0.0));
        h.succeedWhen(() -> {
            int delivered = count(h, dst, BaseMaterials.COAL.get());
            h.assertTrue(delivered >= 3, "delivered " + delivered);
            int inHand = COAL.equals(be.logic().held()) ? 1 : 0;
            int slot = be.burner().fuel().getOrDefault(COAL, 0);
            h.assertTrue(slot == 1, "fuel slot " + be.burner().fuel());
            h.assertTrue(be.burner().remainingJ() > 0, "nothing burning");
            int total = count(h, src, BaseMaterials.COAL.get()) + delivered + inHand + slot + 1;
            h.assertTrue(total == 11, "coal total " + total);
        });
    }

    /** Without fuel a burner inserter does not move; right-clicking it with fuel fills its slot. */
    @GameTest(template = "empty16")
    public static void burnerRightClickFuels(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 2));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);
        BlockPos at = new BlockPos(2, 1, 2);
        InserterBlockEntity be = placeInserter(h, ModItems.BURNER_INSERTER.get(), at, Direction.EAST);
        h.runAtTickTime(20, () -> {
            h.assertTrue(be.logic().held() == null && be.logic().armProgress() == 0, "moved without fuel");
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BaseMaterials.COAL.get(), 3));
            h.useBlock(at, player);
            h.assertTrue(be.burner().fuel().getOrDefault(COAL, 0) == 3, "fuel " + be.burner().fuel());
            h.assertTrue(player.getMainHandItem().isEmpty(), "player kept " + player.getMainHandItem());
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(count(h, new BlockPos(3, 1, 2), ModItems.IRON_PLATE.get()) >= 1, "nothing delivered after fueling");
            h.succeed();
        });
    }

    @GameTest(template = "empty16")
    public static void beltToChestTakesFromBothLanes(GameTestHelper h) {
        BlockPos beltPos = new BlockPos(2, 1, 2);
        place(h, ModItems.TRANSPORT_BELT.get(), beltPos, Direction.NORTH); // ends in the open: items wait
        BeltBlockEntity b = belt(h, beltPos);
        b.lane(LaneSide.LEFT).insertAt(PLATE, 0.5);
        b.lane(LaneSide.RIGHT).insertAt("minetorio:copper_plate", 0.5);
        BlockPos dst = new BlockPos(4, 1, 2);
        h.setBlock(dst, Blocks.CHEST);
        placeInserter(h, ModItems.INSERTER.get(), new BlockPos(3, 1, 2), Direction.EAST);
        h.succeedWhen(() -> {
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 1, "iron (left lane) not taken");
            h.assertTrue(count(h, dst, ModItems.COPPER_PLATE.get()) == 1, "copper (right lane) not taken");
            h.assertTrue(b.lane(LaneSide.LEFT).isEmpty() && b.lane(LaneSide.RIGHT).isEmpty(), "belt not empty");
        });
    }

    /** Inserter west of a north-facing belt: the far lane is the east one, i.e. RIGHT. */
    @GameTest(template = "empty16")
    public static void chestToBeltDropsOnFarLane(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 2));
        BlockPos beltPos = new BlockPos(3, 1, 2);
        place(h, ModItems.TRANSPORT_BELT.get(), beltPos, Direction.NORTH);
        placeInserter(h, ModItems.INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        BeltBlockEntity b = belt(h, beltPos);
        h.succeedWhen(() -> {
            h.assertTrue(onBelt(b, LaneSide.LEFT) == 0, "near lane got items");
            h.assertTrue(onBelt(b, LaneSide.RIGHT) == 2, "far lane has " + onBelt(b, LaneSide.RIGHT));
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 0, "source not empty");
        });
    }

    /** Long-handed: takes from 2 blocks behind, drops 2 blocks ahead; the adjacent blocks are untouched. */
    @GameTest(template = "empty16")
    public static void longHandedReachesTwo(GameTestHelper h) {
        BlockPos far = new BlockPos(1, 1, 2);
        BlockPos near = new BlockPos(2, 1, 2);
        BlockPos nearDst = new BlockPos(4, 1, 2);
        BlockPos farDst = new BlockPos(5, 1, 2);
        container(h, Blocks.BARREL, far, new ItemStack(ModItems.IRON_PLATE.get(), 3));
        container(h, Blocks.BARREL, near, new ItemStack(ModItems.COPPER_PLATE.get(), 3));
        h.setBlock(nearDst, Blocks.BARREL);
        h.setBlock(farDst, Blocks.BARREL);
        placeInserter(h, ModItems.LONG_HANDED_INSERTER.get(), new BlockPos(3, 1, 2), Direction.EAST);
        h.succeedWhen(() -> {
            h.assertTrue(count(h, farDst, ModItems.IRON_PLATE.get()) == 3, "far target has " + count(h, farDst, ModItems.IRON_PLATE.get()));
            h.assertTrue(count(h, near, ModItems.COPPER_PLATE.get()) == 3, "adjacent source was used");
            h.assertTrue(handler(h, nearDst).getStackInSlot(0).isEmpty(), "adjacent target got items");
        });
    }

    /** Review Focus 3: target full → the plate waits in the hand; source −1; nothing lost or duplicated. */
    @GameTest(template = "empty16")
    public static void fullTargetNoLoss(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        BlockPos dst = new BlockPos(3, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 5));
        h.setBlock(dst, Blocks.CHEST);
        IItemHandler target = handler(h, dst);
        for (int i = 0; i < target.getSlots(); i++) target.insertItem(i, new ItemStack(Items.STONE, 64), false);
        InserterBlockEntity be = placeInserter(h, ModItems.INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        h.runAtTickTime(80, () -> {
            h.assertTrue(PLATE.equals(be.logic().held()), "hand holds " + be.logic().held());
            h.assertTrue(be.logic().armProgress() == 1.0 && !be.logic().isMoving(), "arm should wait at the target");
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 4, "source has " + count(h, src, ModItems.IRON_PLATE.get()));
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 0, "full target took a plate");
            h.assertTrue(dropped(h, ModItems.IRON_PLATE.get()) == 0, "plate dropped");
            h.succeed();
        });
    }

    /** Electric inserters do not move without power (no pole near this one). */
    @GameTest(template = "empty16")
    public static void electricWithoutPowerIdle(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 2));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.CHEST);
        place(h, ModItems.FAST_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        InserterBlockEntity be = inserter(h, new BlockPos(2, 1, 2));
        h.runAtTickTime(40, () -> {
            h.assertTrue(be.logic().held() == null && be.logic().armProgress() == 0, "moved without power");
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 2, "source changed");
            h.succeed();
        });
    }

    /** Enchantments and a custom name survive chest → inserter → chest (the hand holds a stack key). */
    @GameTest(template = "empty16")
    public static void componentsSurviveInserter(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Swung"));
        BlockPos src = new BlockPos(1, 1, 2);
        BlockPos dst = new BlockPos(3, 1, 2);
        container(h, Blocks.CHEST, src, sword);
        h.setBlock(dst, Blocks.CHEST);
        placeInserter(h, ModItems.FAST_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        h.succeedWhen(() -> {
            ItemStack got = handler(h, dst).getStackInSlot(0);
            h.assertTrue(!got.isEmpty(), "not delivered yet");
            h.assertTrue(ItemStack.isSameItemSameComponents(sword, got), "components changed: " + got.getComponentsPatch());
        });
    }

    /** R22: an item whose stack key exceeds 32 KB is never picked up; it stays in the source. */
    @GameTest(template = "empty16")
    public static void hugeItemStaysInSource(GameTestHelper h) {
        ItemStack huge = new ItemStack(ModItems.IRON_PLATE.get());
        huge.set(DataComponents.CUSTOM_NAME, Component.literal("x".repeat(40_000)));
        BlockPos src = new BlockPos(1, 1, 2);
        container(h, Blocks.CHEST, src, huge, new ItemStack(ModItems.COPPER_PLATE.get()));
        BlockPos beltPos = new BlockPos(3, 1, 2);
        place(h, ModItems.TRANSPORT_BELT.get(), beltPos, Direction.NORTH);
        InserterBlockEntity be = placeInserter(h, ModItems.INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        h.runAtTickTime(60, () -> {
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 1, "huge plate left the source");
            h.assertTrue(count(h, src, ModItems.COPPER_PLATE.get()) == 0, "normal item behind it was not moved");
            h.assertTrue(be.logic().held() == null, "hand holds " + be.logic().held());
            h.assertTrue(onBelt(belt(h, beltPos), LaneSide.RIGHT) == 1, "belt should carry only the copper plate");
            h.succeed();
        });
    }

    /** Review Focus 1: hand, arm position and burner fuel survive save/load. */
    @GameTest(template = "empty16")
    public static void inserterNbtRoundTrip(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        InserterBlockEntity be = placeInserter(h, ModItems.BURNER_INSERTER.get(), at, Direction.EAST);
        be.burner().insertFuel(COAL, 2, false);
        be.burner().draw();
        be.logic().load(Map.of("phase", "TO_DROP", "held", PLATE, "leg", 0.25));
        var registries = h.getLevel().registryAccess();
        CompoundTag tag = be.saveWithFullMetadata(registries);
        InserterBlockEntity copy = new InserterBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(tag, registries);
        h.assertTrue(PLATE.equals(copy.logic().held()), "held " + copy.logic().held());
        h.assertTrue(copy.logic().armProgress() == 0.25, "arm " + copy.logic().armProgress());
        h.assertTrue(copy.burner().fuel().equals(be.burner().fuel()), "fuel " + copy.burner().fuel());
        h.assertTrue(copy.burner().remainingJ() == be.burner().remainingJ(), "remaining energy");
        h.succeed();
    }

    /** Breaking an inserter drops the inserter, the item in its hand and its fuel. */
    @GameTest(template = "empty16")
    public static void breakingDropsHeldAndFuel(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        InserterBlockEntity be = placeInserter(h, ModItems.BURNER_INSERTER.get(), at, Direction.EAST);
        be.burner().insertFuel(COAL, 2, false);
        be.logic().load(Map.of("phase", "WAITING", "held", PLATE, "leg", 0.0));
        h.getLevel().destroyBlock(h.absolutePos(at), true);
        h.assertTrue(dropped(h, ModItems.BURNER_INSERTER.get()) == 1, "inserter item");
        h.assertTrue(dropped(h, ModItems.IRON_PLATE.get()) == 1, "held plate");
        h.assertTrue(dropped(h, BaseMaterials.COAL.get()) == 2, "fuel");
        h.succeed();
    }
}
