package dev.minetorio.gametest;

import dev.minetorio.content.registry.BaseMaterials;
import static dev.minetorio.gametest.BeltGameTest.belt;
import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.container;
import static dev.minetorio.gametest.InserterGameTest.count;
import static dev.minetorio.gametest.InserterGameTest.placeInserter;

import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.inserter.InserterFilterMenu;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.inserter.InserterType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Filter inserter (filters, blacklist, GUI clicks, settings) and stack inserter (hand of 2, belt fill). */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class FilterStackInserterGameTest {
    private static final String PLATE = "minetorio:iron_plate";
    private static final String COPPER = "minetorio:copper_plate";

    /** Belt (ends in the open, items wait) west of the inserter at (3,1,2) dropping east into a chest at (4,1,2). */
    private static BeltBlockEntity beltWithItems(GameTestHelper h, String left, String right) {
        BlockPos beltPos = new BlockPos(2, 1, 2);
        place(h, ModItems.TRANSPORT_BELT.get(), beltPos, Direction.NORTH);
        BeltBlockEntity b = belt(h, beltPos);
        if (left != null) b.lane(LaneSide.LEFT).insertAt(left, 0.5);
        if (right != null) b.lane(LaneSide.RIGHT).insertAt(right, 0.5);
        return b;
    }

    @GameTest(template = "empty16")
    public static void filterFromBeltMovesOnlyMatching(GameTestHelper h) {
        BeltBlockEntity b = beltWithItems(h, COPPER, PLATE);
        BlockPos dst = new BlockPos(4, 1, 2);
        h.setBlock(dst, Blocks.CHEST);
        InserterBlockEntity be = placeInserter(h, ModItems.FILTER_INSERTER.get(), new BlockPos(3, 1, 2), Direction.EAST);
        be.setFilter(2, PLATE);
        h.runAtTickTime(60, () -> {
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 1, "iron plate not moved");
            h.assertTrue(count(h, dst, ModItems.COPPER_PLATE.get()) == 0, "copper plate moved");
            h.assertTrue(b.lane(LaneSide.LEFT).items().size() == 1, "copper left the belt");
            h.assertTrue(be.logic().held() == null, "hand holds " + be.logic().held());
            h.succeed();
        });
    }

    @GameTest(template = "empty16", timeoutTicks = 140)
    public static void filterFromChestMovesOnlyMatching(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        BlockPos dst = new BlockPos(3, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.COPPER_PLATE.get(), 3), new ItemStack(BaseMaterials.COAL.get(), 2),
                new ItemStack(ModItems.IRON_PLATE.get(), 3));
        h.setBlock(dst, Blocks.CHEST);
        InserterBlockEntity be = placeInserter(h, ModItems.FILTER_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        be.setFilter(0, PLATE);
        be.setFilter(4, "minetorio:coal");
        h.runAtTickTime(100, () -> {
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 3, "iron moved " + count(h, dst, ModItems.IRON_PLATE.get()));
            h.assertTrue(count(h, dst, BaseMaterials.COAL.get()) == 2, "coal moved " + count(h, dst, BaseMaterials.COAL.get()));
            h.assertTrue(count(h, dst, ModItems.COPPER_PLATE.get()) == 0, "copper moved");
            h.assertTrue(count(h, src, ModItems.COPPER_PLATE.get()) == 3, "copper left the source");
            h.succeed();
        });
    }

    /** Blacklist: everything but the listed item moves; an empty whitelist moves nothing. */
    @GameTest(template = "empty16", timeoutTicks = 140)
    public static void filterBlacklistMovesTheRest(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        BlockPos dst = new BlockPos(3, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 3), new ItemStack(ModItems.COPPER_PLATE.get(), 3));
        h.setBlock(dst, Blocks.CHEST);
        InserterBlockEntity be = placeInserter(h, ModItems.FILTER_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        h.runAtTickTime(30, () -> {
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 3 && count(h, src, ModItems.COPPER_PLATE.get()) == 3,
                    "an empty whitelist moved items");
            be.setFilter(0, PLATE);
            be.setBlacklist(true);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(count(h, dst, ModItems.COPPER_PLATE.get()) == 3, "copper moved " + count(h, dst, ModItems.COPPER_PLATE.get()));
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 0, "blacklisted iron moved");
            h.assertTrue(count(h, src, ModItems.IRON_PLATE.get()) == 3, "iron left the source");
            h.succeed();
        });
    }

    /** GUI: clicking a ghost slot with an item sets it, with an empty cursor clears it; the mode button toggles. */
    @GameTest(template = "empty16")
    public static void filterMenuClicksSetFilters(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        InserterBlockEntity be = placeInserter(h, ModItems.FILTER_INSERTER.get(), at, Direction.EAST);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(at.getCenter()));
        InserterFilterMenu menu = new InserterFilterMenu(1, player.getInventory(), h.absolutePos(at));
        h.assertTrue(menu.stillValid(player), "menu not valid");
        h.assertTrue(menu.filterSlots() == 5, "slots " + menu.filterSlots());
        menu.setCarried(new ItemStack(ModItems.IRON_PLATE.get(), 7));
        h.assertTrue(menu.clickMenuButton(player, 3), "click refused");
        h.assertTrue(PLATE.equals(be.filter().get(3)), "slot 3 " + be.filter().get(3));
        h.assertTrue(menu.getCarried().getCount() == 7, "ghost slot took items");
        menu.setCarried(ItemStack.EMPTY);
        menu.clickMenuButton(player, 3);
        h.assertTrue(be.filter().get(3) == null, "slot not cleared");
        menu.clickMenuButton(player, InserterFilterMenu.BUTTON_TOGGLE_MODE);
        h.assertTrue(be.filter().isBlacklist(), "mode not toggled");
        h.assertTrue(!menu.clickMenuButton(player, 5), "out of range slot accepted");
        h.succeed();
    }

    /** Settings: copied with saveSettings/loadSettings, saved with the block entity, in the client update tag. */
    @GameTest(template = "empty16")
    public static void filterSettingsSaveAndLoad(GameTestHelper h) {
        InserterBlockEntity be = placeInserter(h, ModItems.FILTER_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        be.setFilter(0, PLATE);
        be.setFilter(4, COPPER);
        be.setBlacklist(true);
        InserterBlockEntity other = placeInserter(h, ModItems.FILTER_INSERTER.get(), new BlockPos(4, 1, 2), Direction.EAST);
        other.loadSettings(be.saveSettings());
        h.assertTrue(other.filter().equals(be.filter()), "copied " + other.filter());

        var registries = h.getLevel().registryAccess();
        CompoundTag tag = be.saveWithFullMetadata(registries);
        InserterBlockEntity copy = new InserterBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(tag, registries);
        h.assertTrue(copy.filter().equals(be.filter()), "reloaded " + copy.filter());

        InserterBlockEntity client = new InserterBlockEntity(be.getBlockPos(), be.getBlockState());
        client.loadWithComponents(be.getUpdateTag(registries), registries);
        h.assertTrue(client.filter().equals(be.filter()), "synced " + client.filter());

        InserterBlockEntity plain = placeInserter(h, ModItems.STACK_INSERTER.get(), new BlockPos(6, 1, 2), Direction.EAST);
        h.assertTrue(plain.saveSettings().isEmpty() && plain.filter() == null, "stack inserter has settings");
        plain.loadSettings(be.saveSettings()); // ignored
        h.succeed();
    }

    /** Chest to chest: every drop delivers a full hand of 2 (Factorio 1.1 base stack inserter capacity). */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void stackMovesFullHandPerSwing(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2);
        BlockPos dst = new BlockPos(3, 1, 2);
        container(h, Blocks.CHEST, src, new ItemStack(ModItems.IRON_PLATE.get(), 10));
        h.setBlock(dst, Blocks.CHEST);
        InserterBlockEntity be = placeInserter(h, ModItems.STACK_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        h.assertTrue(be.type() == InserterType.STACK, "type " + be.type());
        List<Integer> drops = new ArrayList<>();
        int[] last = {0};
        h.onEachTick(() -> {
            int now = count(h, dst, ModItems.IRON_PLATE.get());
            if (now != last[0]) drops.add(now - last[0]);
            last[0] = now;
        });
        h.succeedWhen(() -> {
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 10, "delivered " + count(h, dst, ModItems.IRON_PLATE.get()));
            h.assertTrue(drops.equals(List.of(2, 2, 2, 2, 2)), "drops " + drops);
        });
    }

    /** From a belt the stack inserter waits for a full hand, then swings with both items. */
    @GameTest(template = "empty16")
    public static void stackWaitsAtBeltToFillHand(GameTestHelper h) {
        BeltBlockEntity b = beltWithItems(h, PLATE, null);
        BlockPos dst = new BlockPos(4, 1, 2);
        h.setBlock(dst, Blocks.CHEST);
        InserterBlockEntity be = placeInserter(h, ModItems.STACK_INSERTER.get(), new BlockPos(3, 1, 2), Direction.EAST);
        h.runAtTickTime(15, () -> {
            h.assertTrue(be.logic().isFilling(), "not waiting at the belt");
            h.assertTrue(be.logic().heldCount() == 1 && be.logic().armProgress() == 0, "hand " + be.logic().heldCount());
            b.lane(LaneSide.RIGHT).insertAt(PLATE, 0.5);
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 2, "delivered " + count(h, dst, ModItems.IRON_PLATE.get()));
            h.assertTrue(b.lane(LaneSide.RIGHT).isEmpty() && b.lane(LaneSide.LEFT).isEmpty(), "belt not empty");
            h.succeed();
        });
    }

    /** A lone item on the belt leaves after the fill timeout. */
    @GameTest(template = "empty16")
    public static void stackLeavesBeltAfterTimeout(GameTestHelper h) {
        beltWithItems(h, PLATE, null);
        BlockPos dst = new BlockPos(4, 1, 2);
        h.setBlock(dst, Blocks.CHEST);
        placeInserter(h, ModItems.STACK_INSERTER.get(), new BlockPos(3, 1, 2), Direction.EAST);
        h.runAtTickTime(InserterType.STACK.fillTimeoutTicks + 30, () -> {
            h.assertTrue(count(h, dst, ModItems.IRON_PLATE.get()) == 1, "delivered " + count(h, dst, ModItems.IRON_PLATE.get()));
            h.succeed();
        });
    }
}
