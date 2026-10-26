package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.belt;
import static dev.minetorio.gametest.BeltGameTest.place;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.belt.SplitterBlockEntity;
import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.belt.UndergroundBeltBlockEntity;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.belt.LaneItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class FastReplaceGameTest {
    private static final String PLATE = "minetorio:iron_plate";

    /** A survival fake player with an empty inventory holding {@code count} of {@code item}. */
    private static ServerPlayer player(GameTestHelper h, Item item, int count) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        return p;
    }

    /** Right-clicks the top of the block at {@code rel} with the main hand, through the server game mode. */
    private static void rightClick(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static int inInventory(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        }
        return n;
    }

    @GameTest(template = "empty16")
    public static void beltUpgradeKeepsItems(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 1, 3);
        place(h, ModItems.TRANSPORT_BELT.get(), rel, Direction.EAST);
        belt(h, rel).lane(LaneSide.LEFT).insertAt(PLATE, 0.2);
        belt(h, rel).lane(LaneSide.LEFT).insertAt(PLATE, 0.7);
        belt(h, rel).lane(LaneSide.RIGHT).insertAt(PLATE, 0.4);
        List<LaneItem> left = belt(h, rel).lane(LaneSide.LEFT).items();
        ServerPlayer p = player(h, ModItems.FAST_TRANSPORT_BELT.get(), 4);
        rightClick(h, p, rel);
        h.assertTrue(h.getBlockState(rel).is(ModBlocks.FAST_TRANSPORT_BELT.get()), "not upgraded: " + h.getBlockState(rel));
        h.assertTrue(h.getBlockState(rel).getValue(BeltBlock.FACING) == Direction.EAST, "facing changed");
        h.assertTrue(belt(h, rel).lane(LaneSide.LEFT).items().equals(left), "left lane changed: " + belt(h, rel).lane(LaneSide.LEFT).items());
        h.assertTrue(belt(h, rel).lane(LaneSide.RIGHT).items().size() == 1, "right lane lost its item");
        h.assertTrue(p.getMainHandItem().getCount() == 3, "hand has " + p.getMainHandItem().getCount());
        h.assertTrue(inInventory(p, ModItems.TRANSPORT_BELT.get()) == 1, "yellow belt not refunded");
        h.assertTrue(BeltGameTest.dropped(h, ModItems.IRON_PLATE.get()) == 0, "plates dropped");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void sameTierDoesNotReplace(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 1, 3);
        place(h, ModItems.TRANSPORT_BELT.get(), rel, Direction.EAST);
        belt(h, rel).lane(LaneSide.LEFT).insertAt(PLATE, 0.2);
        ServerPlayer p = player(h, ModItems.TRANSPORT_BELT.get(), 4);
        rightClick(h, p, rel);
        h.assertTrue(belt(h, rel).lane(LaneSide.LEFT).items().size() == 1, "belt was replaced");
        h.assertTrue(inInventory(p, ModItems.TRANSPORT_BELT.get()) <= 4, "refund on same tier");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void undergroundPairUpgradeKeepsPairingAndContents(GameTestHelper h) {
        BlockPos in = new BlockPos(2, 1, 3);
        BlockPos out = new BlockPos(5, 1, 3);
        place(h, ModItems.UNDERGROUND_BELT.get(), in, Direction.EAST);
        place(h, ModItems.UNDERGROUND_BELT.get(), out, Direction.EAST);
        UndergroundBeltBlockEntity entrance = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(in));
        entrance.lane(LaneSide.LEFT).insertAt(PLATE, 0.2);
        entrance.lane(LaneSide.LEFT).insertAt(PLATE, 2.0);
        entrance.lane(LaneSide.RIGHT).insertAt(PLATE, 1.5);
        List<LaneItem> before = entrance.lane(LaneSide.LEFT).items();
        ServerPlayer p = player(h, ModItems.FAST_UNDERGROUND_BELT.get(), 2);
        rightClick(h, p, out);
        h.assertTrue(h.getBlockState(in).is(ModBlocks.FAST_UNDERGROUND_BELT.get()), "entrance not upgraded");
        h.assertTrue(h.getBlockState(out).is(ModBlocks.FAST_UNDERGROUND_BELT.get()), "exit not upgraded");
        h.assertTrue(h.getBlockState(in).getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.INPUT, "entrance type");
        h.assertTrue(h.getBlockState(out).getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT, "exit type");
        UndergroundBeltBlockEntity ne = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(in));
        UndergroundBeltBlockEntity nx = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(out));
        h.assertTrue(ne.distance() == 3 && nx.distance() == 3, "pairing lost: " + ne.distance() + "/" + nx.distance());
        h.assertTrue(ne.lane(LaneSide.LEFT).items().equals(before), "tunnel contents changed");
        h.assertTrue(ne.lane(LaneSide.RIGHT).items().size() == 1, "right lane lost its item");
        h.assertTrue(inInventory(p, ModItems.FAST_UNDERGROUND_BELT.get()) == 0, "both red pieces should be used");
        h.assertTrue(inInventory(p, ModItems.UNDERGROUND_BELT.get()) == 2, "refund " + inInventory(p, ModItems.UNDERGROUND_BELT.get()));
        h.assertTrue(BeltGameTest.dropped(h, ModItems.IRON_PLATE.get()) == 0, "plates dropped");
        h.succeed();
    }

    /** A red pair 7 apart is beyond yellow range (5): refused, nothing changes. */
    @GameTest(template = "empty16")
    public static void undergroundDowngradeRefusedWithoutRange(GameTestHelper h) {
        BlockPos in = new BlockPos(1, 1, 3);
        BlockPos out = new BlockPos(8, 1, 3);
        place(h, ModItems.FAST_UNDERGROUND_BELT.get(), in, Direction.EAST);
        place(h, ModItems.FAST_UNDERGROUND_BELT.get(), out, Direction.EAST);
        UndergroundBeltBlockEntity entrance = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(in));
        h.assertTrue(entrance.distance() == 7, "setup: red pair at 7, got " + entrance.distance());
        ServerPlayer p = player(h, ModItems.UNDERGROUND_BELT.get(), 4);
        rightClick(h, p, in);
        h.assertTrue(h.getBlockState(in).is(ModBlocks.FAST_UNDERGROUND_BELT.get()), "entrance replaced");
        h.assertTrue(h.getBlockState(out).is(ModBlocks.FAST_UNDERGROUND_BELT.get()), "exit replaced");
        h.assertTrue(p.getMainHandItem().getCount() == 4, "items taken");
        h.assertTrue(h.getBlockState(new BlockPos(1, 2, 3)).isAir(), "placed against the face instead");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void undergroundPairNeedsTwo(GameTestHelper h) {
        BlockPos in = new BlockPos(2, 1, 3);
        BlockPos out = new BlockPos(5, 1, 3);
        place(h, ModItems.UNDERGROUND_BELT.get(), in, Direction.EAST);
        place(h, ModItems.UNDERGROUND_BELT.get(), out, Direction.EAST);
        ServerPlayer p = player(h, ModItems.FAST_UNDERGROUND_BELT.get(), 1);
        rightClick(h, p, in);
        h.assertTrue(h.getBlockState(in).is(ModBlocks.UNDERGROUND_BELT.get()), "replaced with one item");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "item taken");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void splitterUpgradeKeepsContents(GameTestHelper h) {
        BlockPos rel = new BlockPos(5, 1, 5);
        place(h, ModItems.SPLITTER.get(), rel, Direction.NORTH);
        SplitterBlockEntity old = (SplitterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        old.input(1, LaneSide.LEFT).insertAt(PLATE, 0.1);
        old.output(0, LaneSide.RIGHT).insertAt(PLATE, 0.3);
        ServerPlayer p = player(h, ModItems.FAST_SPLITTER.get(), 2);
        rightClick(h, p, new BlockPos(6, 1, 5)); // the right column (a part)
        h.assertTrue(h.getBlockState(rel).is(ModBlocks.FAST_SPLITTER.get()), "controller not upgraded: " + h.getBlockState(rel));
        BlockPos part = new BlockPos(6, 1, 5);
        h.assertTrue(h.getBlockState(part).is(ModBlocks.MULTIBLOCK_PART.get()), "part missing");
        h.assertTrue(h.absolutePos(rel).equals(MultiblockPartBlock.controllerPos(h.getLevel(), h.absolutePos(part))), "part not linked");
        SplitterBlockEntity be = (SplitterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        h.assertTrue(be.input(1, LaneSide.LEFT).items().size() == 1 && be.output(0, LaneSide.RIGHT).items().size() == 1, "contents lost");
        h.assertTrue(be.facing() == Direction.NORTH, "facing changed");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "hand has " + p.getMainHandItem().getCount());
        h.assertTrue(inInventory(p, ModItems.SPLITTER.get()) == 1, "splitter not refunded");
        h.assertTrue(BeltGameTest.dropped(h, ModItems.SPLITTER.get()) == 0 && BeltGameTest.dropped(h, ModItems.IRON_PLATE.get()) == 0, "drops");
        h.succeed();
    }
}
