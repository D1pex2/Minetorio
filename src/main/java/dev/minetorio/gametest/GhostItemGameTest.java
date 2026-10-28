package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.drag.DragPlacement;
import dev.minetorio.content.drag.DragTerrain;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.robot.GhostItem;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.core.drag.DragDir;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Ghost items ({@link GhostItem}): where they come from, what they place, and that they stay with the player. */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class GhostItemGameTest {
    @AfterBatch(batch = "ghost_item_research")
    public static void after(ServerLevel level) {
        ResearchSavedData.get(level.getServer()).restore(Set.of(), null, Map.of());
    }

    private static int ghostsOf(ServerPlayer p, Item target) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (GhostItem.target(s).is(target)) n += s.getCount();
        return n;
    }

    /** A researched recipe gives a ghost item of its output into the hand; a locked one, or one without a placeable output, does not. */
    @GameTest(template = "empty16", batch = "ghost_item_research")
    public static void ghostItemFromUnlockedRecipeOnly(GameTestHelper h) {
        ResearchSavedData.get(h.getLevel().getServer()).restore(Set.of(), null, Map.of()); // start research: belts yes, assemblers no
        ServerPlayer p = HandGhostGameTest.player(h, ModItems.IRON_PLATE.get(), new BlockPos(3, 1, 3), Direction.EAST);
        h.assertTrue(HandGhosts.fromRecipe(p, "transport_belt") == HandGhosts.GiveResult.OK, "belt ghost refused");
        ItemStack hand = p.getMainHandItem();
        h.assertTrue(GhostItem.target(hand).is(ModItems.TRANSPORT_BELT.get()) && hand.getCount() == 1, "hand holds " + hand);
        h.assertTrue(hand.getHoverName().getString().contains("Ghost") || hand.getHoverName().getString().contains("ghost"),
                "name " + hand.getHoverName().getString());
        h.assertTrue(p.getInventory().countItem(ModItems.IRON_PLATE.get()) == 1, "the plate in the hand was lost");
        h.assertTrue(HandGhosts.fromRecipe(p, "assembling_machine_1") == HandGhosts.GiveResult.LOCKED, "locked recipe not refused");
        h.assertTrue(ghostsOf(p, ModItems.ASSEMBLING_MACHINE_1.get()) == 0, "got a ghost of a locked machine");
        h.assertTrue(HandGhosts.fromRecipe(p, "iron_gear_wheel") == HandGhosts.GiveResult.NOT_PLACEABLE, "gear ghost");
        h.assertTrue(HandGhosts.fromRecipe(p, "no_such_recipe") == HandGhosts.GiveResult.UNKNOWN, "unknown recipe");
        // a second request for the same ghost selects it instead of adding another
        h.assertTrue(HandGhosts.fromRecipe(p, "transport_belt") == HandGhosts.GiveResult.OK, "second request");
        h.assertTrue(ghostsOf(p, ModItems.TRANSPORT_BELT.get()) == 1, "duplicate ghost items");
        h.succeed();
    }

    /** Pick-block on a placed belt without belts in the inventory gives its ghost item; with belts it does not. */
    @GameTest(template = "empty16")
    public static void pipetteGivesAGhostItemOnlyWithoutTheItem(GameTestHelper h) {
        BlockPos belt = new BlockPos(5, 1, 5);
        h.setBlock(belt, ModBlocks.TRANSPORT_BELT.get());
        ServerPlayer p = HandGhostGameTest.player(h, ModItems.IRON_PLATE.get(), new BlockPos(3, 1, 5), Direction.EAST);
        p.getInventory().add(new ItemStack(ModItems.TRANSPORT_BELT.get(), 2));
        h.assertTrue(HandGhosts.pipette(p, h.absolutePos(belt)) == HandGhosts.GiveResult.HAS_ITEM, "pipette with belts in the inventory");
        p.getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.TRANSPORT_BELT.get()), -1, p.inventoryMenu.getCraftSlots());
        h.assertTrue(HandGhosts.pipette(p, h.absolutePos(belt)) == HandGhosts.GiveResult.OK, "pipette refused");
        h.assertTrue(GhostItem.target(p.getMainHandItem()).is(ModItems.TRANSPORT_BELT.get()), "hand holds " + p.getMainHandItem());
        h.setBlock(new BlockPos(5, 0, 5), Blocks.STONE);
        h.assertTrue(HandGhosts.pipette(p, h.absolutePos(new BlockPos(5, 0, 5))) == HandGhosts.GiveResult.NOT_PLACEABLE, "pipette of a vanilla block");
        h.succeed();
    }

    /** The ghost item places ghosts (by the client's packet and by the vanilla use path), drags ghost lines and is never used up. */
    @GameTest(template = "empty16")
    public static void ghostItemPlacesGhostsAndStays(GameTestHelper h) {
        HandGhostGameTest.floor(h, 0, 12, 0, 12);
        ServerPlayer p = HandGhostGameTest.player(h, ModItems.IRON_PLATE.get(), new BlockPos(2, 1, 5), Direction.EAST);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, GhostItem.of(ModItems.TRANSPORT_BELT.get()));
        h.assertTrue(HandGhostGameTest.click(h, p, new BlockPos(5, 0, 5), Direction.UP), "ghost refused");
        h.assertTrue(HandGhostGameTest.ghostAt(h, new BlockPos(5, 1, 5)).target().getValue(BeltBlock.FACING) == Direction.EAST, "facing");
        // the vanilla use path (no ghost key involved)
        BlockPos floorAbs = h.absolutePos(new BlockPos(5, 0, 7));
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floorAbs).add(0, 0.5, 0), Direction.UP, floorAbs, false));
        HandGhostGameTest.ghostAt(h, new BlockPos(5, 1, 7));
        // a drag line with it
        h.assertTrue(HandGhostGameTest.click(h, p, new BlockPos(2, 0, 9), Direction.UP), "drag start refused");
        DragPlacement.start(p, h.absolutePos(new BlockPos(2, 1, 9)), DragTerrain.fromMc(Direction.EAST), true);
        byte[] steps = new byte[4];
        for (int i = 0; i < steps.length; i++) steps[i] = (byte) DragDir.EAST.id();
        DragPlacement.steps(p, 0, steps, true);
        for (int x = 2; x <= 6; x++) HandGhostGameTest.ghostAt(h, new BlockPos(x, 1, 9));
        // a real drag with a ghost item is no drag at all
        DragPlacement.start(p, h.absolutePos(new BlockPos(2, 1, 11)), DragDir.EAST, false);
        h.assertFalse(DragPlacement.isDragging(p), "real drag with a ghost item");
        ItemStack hand = p.getMainHandItem();
        h.assertTrue(GhostItem.target(hand).is(ModItems.TRANSPORT_BELT.get()) && hand.getCount() == 1, "ghost item used: " + hand);
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(5, 1, 5));
        // building still needs the real item: the ghost item does not build the ghost
        BlockPos ghostAbs = h.absolutePos(new BlockPos(5, 1, 5));
        p.gameMode.useItemOn(p, h.getLevel(), hand, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ghostAbs), Direction.UP, ghostAbs, false));
        h.assertBlockNotPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(5, 1, 5));
        HandGhostGameTest.done(h);
    }

    /** An inserter moves the plate behind a ghost item but never the ghost item; dropped on the ground, a ghost item vanishes. */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void ghostItemStaysOutOfInsertersAndOffTheGround(GameTestHelper h) {
        BlockPos src = new BlockPos(1, 1, 2), dst = new BlockPos(3, 1, 2);
        InserterGameTest.container(h, Blocks.CHEST, src, GhostItem.of(ModItems.TRANSPORT_BELT.get()), new ItemStack(ModItems.IRON_PLATE.get(), 1));
        h.setBlock(dst, Blocks.CHEST);
        InserterGameTest.placeInserter(h, ModItems.INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);

        ItemEntity dropped = new ItemEntity(h.getLevel(), h.absolutePos(new BlockPos(8, 2, 8)).getX(), h.absolutePos(new BlockPos(8, 2, 8)).getY(),
                h.absolutePos(new BlockPos(8, 2, 8)).getZ(), GhostItem.of(ModItems.INSERTER.get()));
        h.getLevel().addFreshEntity(dropped);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(8, 2, 8))).inflate(2)).isEmpty(),
                "ghost item lies on the ground");

        h.succeedWhen(() -> {
            h.assertTrue(InserterGameTest.count(h, dst, ModItems.IRON_PLATE.get()) == 1, "plate not moved");
            h.assertTrue(InserterGameTest.count(h, dst, ModItems.GHOST_ITEM.get()) == 0, "ghost item moved");
            h.assertTrue(InserterGameTest.count(h, src, ModItems.GHOST_ITEM.get()) == 1, "ghost item gone from the source");
            h.assertTrue(InserterGameTest.inserter(h, new BlockPos(2, 1, 2)).logic().held() == null
                    || !InserterGameTest.inserter(h, new BlockPos(2, 1, 2)).logic().held().contains("ghost_item"), "inserter holds the ghost item");
        });
    }
}
