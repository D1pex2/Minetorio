package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.belt.UndergroundBeltBlockEntity;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.belt.LaneItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class BeltGameTest {
    private static final String PLATE = "minetorio:iron_plate";

    // --- helpers ---------------------------------------------------------------------------------

    /**
     * Places {@code item} like a player looking {@code facing} would; for a multiblock machine
     * {@code rel} is the controller position (the targeted cell is its centered-placement anchor).
     */
    static void place(GameTestHelper h, Item item, BlockPos rel, Direction facing) {
        BlockPos abs = MultiblockBlockItem.targetFor(item, h.absolutePos(rel), facing);
        ItemStack stack = new ItemStack(item);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockPlaceContext ctx = new BlockPlaceContext(h.getLevel(), null, InteractionHand.MAIN_HAND, stack, hit) {
            @Override
            public Direction getHorizontalDirection() {
                return facing;
            }
        };
        h.assertTrue(((BlockItem) item).place(ctx).consumesAction(), "could not place " + item + " at " + rel);
    }

    static BeltBlockEntity belt(GameTestHelper h, BlockPos rel) {
        return (BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    static int count(GameTestHelper h, BlockPos chest, Item item) {
        IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(chest), null);
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) n += handler.getStackInSlot(i).getCount();
        }
        return n;
    }

    static int dropped(GameTestHelper h, Item item) {
        int n = 0;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
            if (e.getItem().is(item)) n += e.getItem().getCount();
        }
        return n;
    }

    /** Row of belts along +x at z, y=1, from x0 to x1 inclusive. */
    static void row(GameTestHelper h, Item belt, int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) place(h, belt, new BlockPos(x, 1, z), Direction.EAST);
    }

    static void travel(GameTestHelper h, Item beltItem, long earliest, long latest) {
        row(h, beltItem, 1, 10, 2);
        BlockPos chest = new BlockPos(11, 1, 2);
        h.setBlock(chest, Blocks.CHEST);
        h.assertTrue(belt(h, new BlockPos(1, 1, 2)).lane(LaneSide.LEFT).insertAt(PLATE, 0), "insert failed");
        h.onEachTick(() -> {
            if (count(h, chest, ModItems.IRON_PLATE.get()) > 0) {
                long t = h.getTick();
                if (t < earliest || t > latest) h.fail("arrived at tick " + t + ", expected " + earliest + ".." + latest);
                h.succeed();
            }
        });
    }

    // --- tests -----------------------------------------------------------------------------------

    /** ceil(10 / 0.09375) + 5 = 112. */
    @GameTest(template = "empty16", timeoutTicks = 140)
    public static void yellowItemTravelsTenBelts(GameTestHelper h) {
        travel(h, ModItems.TRANSPORT_BELT.get(), 100, 112);
    }

    /** ceil(10 / 0.1875) + 5 = 59. */
    @GameTest(template = "empty16", timeoutTicks = 100)
    public static void redFasterThanYellow(GameTestHelper h) {
        travel(h, ModItems.FAST_TRANSPORT_BELT.get(), 50, 59);
    }

    /**
     * A compressed lane across 10 chained belts delivers 7.5 items/s: hand-offs keep the sub-tick
     * overflow when belts tick downstream-first. Front item at 0.75 → item k arrives when
     * 0.25 + 0.25k ≤ 0.09375t, so 37 items by tick 100 (36 with a tick of registration lag).
     */
    @GameTest(template = "empty16", timeoutTicks = 140)
    public static void chainedThroughputIsFull(GameTestHelper h) {
        row(h, ModItems.TRANSPORT_BELT.get(), 1, 10, 2);
        BlockPos chest = new BlockPos(11, 1, 2);
        h.setBlock(chest, Blocks.CHEST);
        for (int x = 1; x <= 10; x++) {
            for (double p = 0; p < 1; p += 0.25) h.assertTrue(belt(h, new BlockPos(x, 1, 2)).lane(LaneSide.RIGHT).insertAt(PLATE, p), "fill");
        }
        h.runAtTickTime(101, () -> {
            int n = count(h, chest, ModItems.IRON_PLATE.get());
            h.assertTrue(n >= 36 && n <= 38, "delivered " + n + " by tick 101, expected 36..38");
            h.succeed();
        });
    }

    @GameTest(template = "empty16")
    public static void sideLoadFillsNearLane(GameTestHelper h) {
        Item yellow = ModItems.TRANSPORT_BELT.get();
        row(h, yellow, 4, 6, 5);                            // B, ends in the open: items queue up
        place(h, yellow, new BlockPos(5, 1, 4), Direction.SOUTH); // A, feeds B's north (left) side
        BeltBlockEntity a = belt(h, new BlockPos(5, 1, 4));
        h.assertTrue(belt(h, new BlockPos(5, 1, 5)).shape() == dev.minetorio.core.belt.BeltShape.STRAIGHT, "B must stay straight");
        a.lane(LaneSide.LEFT).insertAt(PLATE, 0.5);
        a.lane(LaneSide.LEFT).insertAt(PLATE, 0);
        a.lane(LaneSide.RIGHT).insertAt(PLATE, 0.5);
        a.lane(LaneSide.RIGHT).insertAt(PLATE, 0);
        h.succeedWhen(() -> {
            int near = 0;
            int far = 0;
            for (int x = 4; x <= 6; x++) {
                near += belt(h, new BlockPos(x, 1, 5)).lane(LaneSide.LEFT).items().size();
                far += belt(h, new BlockPos(x, 1, 5)).lane(LaneSide.RIGHT).items().size();
            }
            h.assertTrue(far == 0, "far lane got " + far + " items");
            h.assertTrue(near == 4, "near lane has " + near + " items");
            h.assertTrue(a.lane(LaneSide.LEFT).isEmpty() && a.lane(LaneSide.RIGHT).isEmpty(), "A not empty");
        });
    }

    @GameTest(template = "empty16")
    public static void curveFormsAutomatically(GameTestHelper h) {
        Item yellow = ModItems.TRANSPORT_BELT.get();
        // Feeder first, then the belt: shape at placement.
        place(h, yellow, new BlockPos(4, 1, 5), Direction.EAST);
        place(h, yellow, new BlockPos(5, 1, 5), Direction.NORTH);
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).getValue(BeltBlock.SHAPE) == BeltBlock.Shape.CURVE_LEFT,
                "expected curve_left, got " + h.getBlockState(new BlockPos(5, 1, 5)).getValue(BeltBlock.SHAPE));
        // Belt first, then the feeder from the right: shape updates.
        place(h, yellow, new BlockPos(10, 1, 5), Direction.NORTH);
        place(h, yellow, new BlockPos(11, 1, 5), Direction.WEST);
        h.assertTrue(h.getBlockState(new BlockPos(10, 1, 5)).getValue(BeltBlock.SHAPE) == BeltBlock.Shape.CURVE_RIGHT,
                "expected curve_right");
        // Something behind straightens it again.
        place(h, yellow, new BlockPos(10, 1, 6), Direction.NORTH);
        h.assertTrue(h.getBlockState(new BlockPos(10, 1, 5)).getValue(BeltBlock.SHAPE) == BeltBlock.Shape.STRAIGHT,
                "expected straight with a back input");
        // Items follow the curve into a chest.
        BlockPos chest = new BlockPos(5, 1, 4);
        h.setBlock(chest, Blocks.CHEST);
        belt(h, new BlockPos(4, 1, 5)).lane(LaneSide.LEFT).insertAt(PLATE, 0);
        belt(h, new BlockPos(4, 1, 5)).lane(LaneSide.RIGHT).insertAt(PLATE, 0);
        h.succeedWhen(() -> h.assertTrue(count(h, chest, ModItems.IRON_PLATE.get()) == 2, "not through the curve yet"));
    }

    @GameTest(template = "empty16")
    public static void undergroundCarriesItems(GameTestHelper h) {
        place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(1, 1, 3), Direction.EAST);
        place(h, ModItems.UNDERGROUND_BELT.get(), new BlockPos(2, 1, 3), Direction.EAST);
        for (int x = 3; x <= 6; x++) h.setBlock(new BlockPos(x, 1, 3), Blocks.STONE);
        place(h, ModItems.UNDERGROUND_BELT.get(), new BlockPos(7, 1, 3), Direction.EAST);
        BlockPos chest = new BlockPos(8, 1, 3);
        h.setBlock(chest, Blocks.CHEST);
        h.assertTrue(h.getBlockState(new BlockPos(2, 1, 3)).getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.INPUT, "entrance");
        h.assertTrue(h.getBlockState(new BlockPos(7, 1, 3)).getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT, "exit");
        UndergroundBeltBlockEntity in = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 3)));
        h.assertTrue(in.distance() == 5 && in.lane(LaneSide.LEFT).length() == 5, "entrance not paired at 5: " + in.distance());
        belt(h, new BlockPos(1, 1, 3)).lane(LaneSide.LEFT).insertAt(PLATE, 0);
        belt(h, new BlockPos(1, 1, 3)).lane(LaneSide.RIGHT).insertAt(PLATE, 0);
        h.succeedWhen(() -> h.assertTrue(count(h, chest, ModItems.IRON_PLATE.get()) == 2, "not through yet"));
    }

    @GameTest(template = "empty16")
    public static void splitterAlternates(GameTestHelper h) {
        place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(5, 1, 6), Direction.NORTH);
        place(h, ModItems.SPLITTER.get(), new BlockPos(5, 1, 5), Direction.NORTH); // columns x=5 (left), x=6 (right)
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).is(dev.minetorio.content.registry.ModBlocks.SPLITTER.get()), "controller at the clicked spot");
        h.assertTrue(h.getBlockState(new BlockPos(6, 1, 5)).is(dev.minetorio.content.registry.ModBlocks.MULTIBLOCK_PART.get()), "part to the right");
        BlockPos leftChest = new BlockPos(5, 1, 4);
        BlockPos rightChest = new BlockPos(6, 1, 4);
        h.setBlock(leftChest, Blocks.CHEST);
        h.setBlock(rightChest, Blocks.CHEST);
        BeltBlockEntity feeder = belt(h, new BlockPos(5, 1, 6));
        for (double p = 0; p < 1; p += 0.25) feeder.lane(LaneSide.LEFT).insertAt(PLATE, p);
        h.succeedWhen(() -> {
            int l = count(h, leftChest, ModItems.IRON_PLATE.get());
            int r = count(h, rightChest, ModItems.IRON_PLATE.get());
            h.assertTrue(l == 2 && r == 2, "left " + l + ", right " + r);
        });
    }

    @GameTest(template = "empty16")
    public static void fullChestStopsBelt(GameTestHelper h) {
        row(h, ModItems.TRANSPORT_BELT.get(), 2, 4, 3);
        BlockPos chest = new BlockPos(5, 1, 3);
        h.setBlock(chest, Blocks.CHEST);
        IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(chest), null);
        for (int i = 0; i < handler.getSlots(); i++) handler.insertItem(i, new ItemStack(Items.STONE, 64), false);
        belt(h, new BlockPos(2, 1, 3)).lane(LaneSide.LEFT).insertAt(PLATE, 0.5);
        belt(h, new BlockPos(2, 1, 3)).lane(LaneSide.LEFT).insertAt(PLATE, 0);
        belt(h, new BlockPos(3, 1, 3)).lane(LaneSide.RIGHT).insertAt(PLATE, 0);
        h.runAtTickTime(80, () -> {
            int onBelts = 0;
            for (int x = 2; x <= 4; x++) {
                BeltBlockEntity b = belt(h, new BlockPos(x, 1, 3));
                onBelts += b.lane(LaneSide.LEFT).items().size() + b.lane(LaneSide.RIGHT).items().size();
            }
            h.assertTrue(onBelts == 3, "items on belts: " + onBelts);
            BeltBlockEntity last = belt(h, new BlockPos(4, 1, 3));
            h.assertTrue(last.lane(LaneSide.LEFT).items().size() == 2, "left lane should queue at the end");
            h.assertTrue(last.lane(LaneSide.LEFT).items().get(0).pos() == 1.0, "front item waits at the lane end");
            h.assertTrue(count(h, chest, ModItems.IRON_PLATE.get()) == 0, "chest took a plate");
            h.assertTrue(dropped(h, ModItems.IRON_PLATE.get()) == 0, "plate dropped");
            h.succeed();
        });
    }

    @GameTest(template = "empty16")
    public static void beltNbtRoundTrip(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 1, 3);
        place(h, ModItems.TRANSPORT_BELT.get(), rel, Direction.EAST);
        BeltBlockEntity be = belt(h, rel);
        be.lane(LaneSide.LEFT).insertAt(PLATE, 0.1);
        be.lane(LaneSide.LEFT).insertAt("minetorio:copper_plate", 0.6);
        be.lane(LaneSide.RIGHT).insertAt("minecraft:coal", 0.35);
        var registries = h.getLevel().registryAccess();
        CompoundTag tag = be.saveWithFullMetadata(registries);
        BeltBlockEntity copy = new BeltBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(tag, registries);
        for (LaneSide side : LaneSide.values()) {
            List<LaneItem> a = be.lane(side).items();
            List<LaneItem> b = copy.lane(side).items();
            h.assertTrue(a.equals(b), side + ": " + a + " != " + b);
            h.assertTrue(be.lane(side).length() == copy.lane(side).length(), "length");
        }
        h.assertTrue(copy.lane(LaneSide.LEFT).items().size() == 2 && copy.lane(LaneSide.RIGHT).items().size() == 1, "count");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void breakingBeltDropsItems(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 1, 3);
        place(h, ModItems.TRANSPORT_BELT.get(), rel, Direction.EAST);
        BeltBlockEntity be = belt(h, rel);
        be.lane(LaneSide.LEFT).insertAt(PLATE, 0.1);
        be.lane(LaneSide.LEFT).insertAt(PLATE, 0.6);
        be.lane(LaneSide.RIGHT).insertAt(PLATE, 0.35);
        h.getLevel().destroyBlock(h.absolutePos(rel), true);
        h.assertTrue(h.getBlockState(rel).isAir(), "belt still there");
        h.assertTrue(dropped(h, ModItems.TRANSPORT_BELT.get()) == 1, "belts dropped: " + dropped(h, ModItems.TRANSPORT_BELT.get()));
        h.assertTrue(dropped(h, ModItems.IRON_PLATE.get()) == 3, "plates dropped: " + dropped(h, ModItems.IRON_PLATE.get()));
        h.succeed();
    }

    /** Entities standing on a belt ride it at belt speed (an armor stand: no AI of its own). */
    @GameTest(template = "empty16")
    public static void entityRidesBelt(GameTestHelper h) {
        row(h, ModItems.TRANSPORT_BELT.get(), 1, 8, 3);
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, new Vec3(1.5, 1.2, 3.5));
        double x0 = stand.getX();
        double z0 = stand.getZ();
        h.runAtTickTime(60, () -> {
            double moved = stand.getX() - x0;
            // 60 ticks at 0.09375 = 5.6 blocks at full speed; allow the ramp-up.
            h.assertTrue(moved > 4.0 && moved < 6.0, "moved " + moved + " blocks");
            h.assertTrue(Math.abs(stand.getZ() - z0) < 0.01, "drifted sideways");
            h.succeed();
        });
    }

    /** R21: an item stack dropped on a belt is taken onto the lanes item by item and delivered. */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void droppedStackIsAbsorbed(GameTestHelper h) {
        row(h, ModItems.TRANSPORT_BELT.get(), 1, 6, 3);
        BlockPos chest = new BlockPos(7, 1, 3);
        h.setBlock(chest, Blocks.CHEST);
        Vec3 at = h.absoluteVec(new Vec3(1.5, 1.2, 3.5));
        ItemEntity e = new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(ModItems.IRON_PLATE.get(), 3));
        e.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(e);
        h.runAtTickTime(40, () -> {
            h.assertTrue(dropped(h, ModItems.IRON_PLATE.get()) == 0, "still on the ground: " + dropped(h, ModItems.IRON_PLATE.get()));
            int onBelts = 0;
            for (int x = 1; x <= 6; x++) {
                BeltBlockEntity b = belt(h, new BlockPos(x, 1, 3));
                onBelts += b.lane(LaneSide.LEFT).items().size() + b.lane(LaneSide.RIGHT).items().size();
            }
            h.assertTrue(onBelts + count(h, chest, ModItems.IRON_PLATE.get()) == 3, "on belts " + onBelts);
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 40, "wait for the absorption check");
            h.assertTrue(count(h, chest, ModItems.IRON_PLATE.get()) == 3, "chest has " + count(h, chest, ModItems.IRON_PLATE.get()));
        });
    }

    /** R22: an item whose stack key would exceed 32 KB is not absorbed; it stays an entity. */
    @GameTest(template = "empty16")
    public static void hugeItemNotAbsorbed(GameTestHelper h) {
        row(h, ModItems.TRANSPORT_BELT.get(), 1, 3, 3);
        ItemStack huge = new ItemStack(ModItems.IRON_PLATE.get());
        huge.set(DataComponents.CUSTOM_NAME, Component.literal("x".repeat(40_000)));
        h.assertTrue(LaneStacks.toLane(huge, h.getLevel().registryAccess()).isEmpty(), "huge key accepted");
        h.assertTrue(LaneStacks.toLane(new ItemStack(ModItems.IRON_PLATE.get()), h.getLevel().registryAccess())
                .equals(java.util.Optional.of(PLATE)), "plain key");
        Vec3 at = h.absoluteVec(new Vec3(1.5, 1.2, 3.5));
        ItemEntity e = new ItemEntity(h.getLevel(), at.x, at.y, at.z, huge);
        e.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(e);
        h.runAtTickTime(30, () -> {
            h.assertTrue(!e.isRemoved() && e.getItem().getCount() == 1, "entity was absorbed");
            for (int x = 1; x <= 3; x++) {
                BeltBlockEntity b = belt(h, new BlockPos(x, 1, 3));
                h.assertTrue(b.lane(LaneSide.LEFT).isEmpty() && b.lane(LaneSide.RIGHT).isEmpty(), "lane got the huge item");
            }
            h.succeed();
        });
    }

    /** Enchantments, custom name and damage survive belt travel (lane keys carry data components). */
    @GameTest(template = "empty16")
    public static void componentsSurviveBelt(GameTestHelper h) {
        row(h, ModItems.TRANSPORT_BELT.get(), 1, 3, 3);
        BlockPos chest = new BlockPos(4, 1, 3);
        h.setBlock(chest, Blocks.CHEST);
        var registries = h.getLevel().registryAccess();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Belt Rider"));
        sword.setDamageValue(17);
        String key = LaneStacks.key(sword, registries);
        h.assertTrue(key.startsWith("minecraft:diamond_sword#") && LaneItem.baseId(key).equals("minecraft:diamond_sword"), "key " + key);
        belt(h, new BlockPos(1, 1, 3)).lane(LaneSide.LEFT).insertAt(key, 0);
        h.succeedWhen(() -> {
            IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(chest), null);
            ItemStack got = handler.getStackInSlot(0);
            h.assertTrue(!got.isEmpty(), "not delivered yet");
            h.assertTrue(ItemStack.isSameItemSameComponents(sword, got), "components changed: " + got.getComponentsPatch());
            h.assertTrue(got.getDamageValue() == 17 && got.getHoverName().getString().equals("Belt Rider"), "damage/name");
        });
    }

    /** The exit can also be placed while looking back at the entrance; it then faces like the entrance. */
    @GameTest(template = "empty16")
    public static void undergroundExitPlacedLookingBack(GameTestHelper h) {
        place(h, ModItems.UNDERGROUND_BELT.get(), new BlockPos(2, 1, 3), Direction.EAST);
        place(h, ModItems.UNDERGROUND_BELT.get(), new BlockPos(5, 1, 3), Direction.WEST);
        var exit = h.getBlockState(new BlockPos(5, 1, 3));
        h.assertTrue(exit.getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT, "not an exit");
        h.assertTrue(exit.getValue(UndergroundBeltBlock.FACING) == Direction.EAST, "exit faces " + exit.getValue(UndergroundBeltBlock.FACING));
        UndergroundBeltBlockEntity in = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 3)));
        h.assertTrue(in.distance() == 3, "distance " + in.distance());
        h.succeed();
    }

    /**
     * A stack whose component patch cannot be encoded (a negative repair cost fails its codec) has
     * no lane key: it must not enter the lanes as the plain item, losing its data.
     */
    @GameTest(template = "empty16")
    public static void unencodableStackHasNoLaneKey(GameTestHelper h) {
        ItemStack odd = new ItemStack(Items.DIAMOND_SWORD);
        odd.set(DataComponents.REPAIR_COST, -1);
        h.assertTrue(LaneStacks.toLane(odd, h.getLevel().registryAccess()).isEmpty(),
                "unencodable stack got key " + LaneStacks.toLane(odd, h.getLevel().registryAccess()));
        h.succeed();
    }

    /**
     * A piece saved as paired whose partner is gone (removed while this piece's chunk was unloaded,
     * so nothing unlinked it) unpairs once the partner's position is loaded, and can pair again.
     */
    @GameTest(template = "empty16")
    public static void undergroundWithVanishedPartnerUnpairs(GameTestHelper h) {
        BlockPos in = new BlockPos(2, 1, 3);
        BlockPos out = new BlockPos(5, 1, 3);
        place(h, ModItems.UNDERGROUND_BELT.get(), in, Direction.EAST);
        place(h, ModItems.UNDERGROUND_BELT.get(), out, Direction.EAST);
        var registries = h.getLevel().registryAccess();
        UndergroundBeltBlockEntity entrance = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(in));
        h.assertTrue(entrance.distance() == 3, "setup: distance " + entrance.distance());
        CompoundTag saved = entrance.saveWithoutMetadata(registries);
        h.setBlock(out, Blocks.AIR);
        h.setBlock(in, Blocks.AIR);
        place(h, ModItems.UNDERGROUND_BELT.get(), in, Direction.EAST);
        UndergroundBeltBlockEntity reloaded = (UndergroundBeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(in));
        reloaded.loadWithComponents(saved, registries); // as if its chunk was loaded from disk, still paired at 3
        h.assertTrue(reloaded.distance() == 3, "setup: reloaded distance " + reloaded.distance());
        h.runAtTickTime(3, () -> {
            h.assertTrue(reloaded.distance() == 0, "still paired to nothing: " + reloaded.distance());
            place(h, ModItems.UNDERGROUND_BELT.get(), new BlockPos(4, 1, 3), Direction.EAST);
            h.assertTrue(h.getBlockState(new BlockPos(4, 1, 3)).getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT,
                    "new piece is not an exit");
            h.assertTrue(reloaded.distance() == 2, "not re-paired: " + reloaded.distance());
            h.succeed();
        });
    }
}
