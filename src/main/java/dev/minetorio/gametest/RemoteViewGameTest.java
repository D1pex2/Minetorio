package dev.minetorio.gametest;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.blueprint.BlueprintData;
import dev.minetorio.content.blueprint.BlueprintItem;
import dev.minetorio.content.blueprint.BlueprintPaste;
import dev.minetorio.content.drag.DragPlacement;
import dev.minetorio.content.registry.ModDataComponents;
import java.util.Optional;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity.SelectResult;
import dev.minetorio.content.machine.AssemblerMenu;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.map.WatchedChunks;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.core.drag.DragDir;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The remote view's server side ({@link RemoteView}): entering only over watched (or own-view)
 * chunks, ghosts placed and removed through the remote path only inside its watched area, recipe
 * changes from a machine GUI opened remotely (no item moves), and its chunk ticket released on
 * every way out. A fake player far from the test (out of reach and out of his own view) stands in
 * for the client; the watched set is a stand-in radar's.
 *
 * <p>Its own batch, and every check runs within one tick right after the watched set is wiped and
 * the stand-in registered: no real radar (other tests', or leftovers) can change what is watched
 * in between.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class RemoteViewGameTest {
    private static final String BATCH = "remote_view";
    /** The fake player stands this far (blocks, along Z) from the test: no reach, no own view. */
    private static final int FAR = 800;
    private static final String PLATE = "minetorio:iron_plate";

    /** A survival fake player of its own, far from the test, holding one {@code item}. */
    private static ServerPlayer farPlayer(GameTestHelper h, Item item) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "remote-test"));
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        if (item != null) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 1));
        BlockPos at = h.absolutePos(new BlockPos(5, 1, 5)).offset(0, 0, FAR);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Direction.EAST.toYRot(), 0);
        HandGhosts.resetBudget(p);
        RemoteView.resetBudgets(p);
        return p;
    }

    private static ChunkPos chunkOf(GameTestHelper h, BlockPos rel) {
        return new ChunkPos(h.absolutePos(rel));
    }

    /** Wipes the watched set and lets a stand-in radar {@code id} watch the chunks within {@code radius} of {@code center}. */
    private static long watchOnly(GameTestHelper h, ChunkPos center, int radius) {
        ServerLevel level = h.getLevel();
        WatchedChunks.wipeForTests(level.getServer());
        long id = h.absolutePos(BlockPos.ZERO).asLong();
        WatchedChunks.watchForTests(level, id, center.x, center.z, radius);
        return id;
    }

    private static boolean ghostClick(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return HandGhosts.place(p, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    private static ChunkPos trackingCenter(ServerPlayer p) {
        return p.getChunkTrackingView() instanceof ChunkTrackingView.Positioned pos ? pos.center() : null;
    }

    private static double centerX(ChunkPos c) { return c.getMiddleBlockX() + 0.5; }

    private static double centerZ(ChunkPos c) { return c.getMiddleBlockZ() + 0.5; }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    /** Entering is refused over a chunk no radar watches and the player cannot see; nothing is loaded for it. */
    @GameTest(template = "empty16", batch = BATCH)
    public static void enteringIsRefusedOverAnUnwatchedChunk(GameTestHelper h) {
        ServerPlayer p = farPlayer(h, null);
        ChunkPos test = chunkOf(h, new BlockPos(5, 1, 5));
        watchOnly(h, test, 0);
        ChunkPos far = new ChunkPos(test.x + 40, test.z - 40);
        h.assertFalse(WatchedChunks.isWatched(h.getLevel(), far.x, far.z), "setup: far chunk watched");
        h.assertTrue(RemoteView.enter(p, centerX(far), centerZ(far)) == RemoteView.Result.NOT_WATCHED, "entered over an unwatched chunk");
        h.assertFalse(RemoteView.isRemote(p), "in remote view after a refusal");
        h.assertFalse(RemoteView.hasTicket(h.getLevel(), far), "ticket for a refused view");
        // the watched test chunk is fine
        h.assertTrue(RemoteView.enter(p, centerX(test), centerZ(test)) == RemoteView.Result.OK, "refused over a watched chunk");
        h.assertTrue(RemoteView.isRemote(p), "not in remote view");
        // moving the focus over the unwatched chunk is refused; the focus stays
        RemoteView.resetBudgets(p);
        h.assertTrue(RemoteView.move(p, centerX(far), centerZ(far)) == RemoteView.Result.NOT_WATCHED, "moved over an unwatched chunk");
        h.assertTrue(test.equals(RemoteView.focus(p)), "focus moved to " + RemoteView.focus(p));
        RemoteView.endForTests(p);
        h.assertFalse(RemoteView.hasTicket(h.getLevel(), test), "ticket kept after exit");
        h.succeed();
    }

    /**
     * A ghost in a watched chunk far from the player goes through the remote path; outside the
     * watched area, without the view, or once the radar stops (the view closes) it is refused.
     */
    @GameTest(template = "empty16", batch = BATCH)
    public static void remoteGhostsOnlyInTheWatchedArea(GameTestHelper h) {
        floor(h);
        ServerPlayer p = farPlayer(h, ModItems.TRANSPORT_BELT.get());
        ChunkPos a = chunkOf(h, new BlockPos(5, 1, 5));
        long id = watchOnly(h, a, 1); // the cells used (x 3..7) may straddle a chunk border
        h.assertFalse(ghostClick(h, p, new BlockPos(5, 0, 5)), "ghost placed from afar without the remote view");

        h.assertTrue(RemoteView.enter(p, centerX(a), centerZ(a)) == RemoteView.Result.OK, "remote view refused");
        h.assertTrue(a.equals(trackingCenter(p)), "chunk tracking not centred on the focus: " + trackingCenter(p));
        h.assertTrue(RemoteView.hasTicket(h.getLevel(), a), "no remote ticket at the focus");
        h.assertTrue(ghostClick(h, p, new BlockPos(5, 0, 5)), "remote ghost refused");
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).is(ModBlocks.GHOST.get()), "no ghost placed");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "item used");

        // left-click removes it; a real block is not removed
        h.assertTrue(RemoteView.removeGhost(p, h.absolutePos(new BlockPos(5, 1, 5))) == RemoteView.Result.OK, "ghost not removed");
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).isAir(), "ghost still there");
        h.assertTrue(RemoteView.removeGhost(p, h.absolutePos(new BlockPos(5, 0, 5))) == RemoteView.Result.NO_GHOST, "stone removed");
        h.assertTrue(h.getBlockState(new BlockPos(5, 0, 5)).is(Blocks.STONE), "stone gone");

        // a ghost drag line works remotely; a drag of real blocks does not
        BlockPos start = h.absolutePos(new BlockPos(3, 1, 3));
        DragPlacement.start(p, start, DragDir.EAST, false);
        h.assertFalse(DragPlacement.isDragging(p), "real drag started from the remote view");
        DragPlacement.start(p, start, DragDir.EAST, true);
        h.assertTrue(DragPlacement.isDragging(p), "ghost drag refused");
        DragPlacement.steps(p, 0, new byte[] {(byte) DragDir.EAST.id(), (byte) DragDir.EAST.id()}, true);
        h.assertTrue(h.getBlockState(new BlockPos(4, 1, 3)).is(ModBlocks.GHOST.get()), "drag laid no ghost");

        // a chunk inside the radius but unwatched is not in the area until a radar watches it
        BlockPos b = new ChunkPos(a.x + 2, a.z).getMiddleBlockPosition(h.absolutePos(BlockPos.ZERO).getY());
        h.assertFalse(RemoteView.inArea(p, b), "unwatched chunk in the area");
        WatchedChunks.watchForTests(h.getLevel(), id + 1, a.x + 2, a.z, 0);
        h.assertTrue(RemoteView.inArea(p, b), "watched chunk not in the area");
        WatchedChunks.unwatchForTests(h.getLevel(), id + 1);
        h.assertFalse(RemoteView.inArea(p, h.absolutePos(new BlockPos(5, 1, 5)).offset(16 * 5, 0, 0)), "chunk beyond the radius in the area");

        // the radar stops: the view closes, its ticket goes, ghosts are refused again
        WatchedChunks.unwatchForTests(h.getLevel(), id);
        h.assertFalse(RemoteView.check(p), "view still open without a radar");
        h.assertFalse(RemoteView.isRemote(p), "still remote");
        h.assertFalse(RemoteView.hasTicket(h.getLevel(), a), "ticket kept after the radar stopped");
        h.assertFalse(ghostClick(h, p, new BlockPos(7, 0, 5)), "ghost placed after the view closed");
        h.assertFalse(h.getBlockState(new BlockPos(7, 1, 5)).is(ModBlocks.GHOST.get()), "ghost there");
        for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 15, 4, 15)) {
            if (h.getBlockState(rel).is(ModBlocks.GHOST.get())) h.setBlock(rel, Blocks.AIR);
        }
        h.succeed();
    }

    /**
     * An assembler's GUI opened from the remote view: the recipe can be changed (what the machine
     * gives up spills at the machine, nothing reaches the player), slot clicks move nothing, and
     * closing the view closes the GUI.
     */
    @GameTest(template = "empty16", batch = BATCH)
    public static void remoteGuiChangesTheRecipeWithoutMovingItems(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 1, 4);
        AssemblerBlockEntity be = MachineGameTest.assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), rel, null);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = farPlayer(h, null);
        ChunkPos a = new ChunkPos(pos);
        watchOnly(h, a, 1);
        h.assertTrue(RemoteView.openMenu(p, pos) == RemoteView.Result.NOT_REMOTE, "GUI opened without the remote view");
        h.assertTrue(AssemblerBlockEntity.select(p, pos, "iron_gear_wheel") == SelectResult.TOO_FAR, "far recipe change accepted");

        h.assertTrue(RemoteView.enter(p, pos.getX() + 0.5, pos.getZ() + 0.5) == RemoteView.Result.OK, "remote view refused");
        // a click on a part of the machine opens the controller's GUI (a fake player opens no menus: done by hand as the server would)
        h.assertTrue(pos.equals(RemoteView.menuTarget(p, pos.east())), "remote GUI target " + RemoteView.menuTarget(p, pos.east()));
        h.assertTrue(RemoteView.menuTarget(p, pos.above(5)) == null, "GUI target in the air");
        AssemblerMenu menu = (AssemblerMenu) be.createMenu(7, p.getInventory(), p);
        p.containerMenu = menu;
        h.assertFalse(menu.stillValid(p), "far GUI valid before it is the remote one");
        h.assertTrue(RemoteView.attachMenu(p, pos), "remote GUI not attached");
        h.assertTrue(menu.stillValid(p), "remote GUI not valid");

        h.assertTrue(AssemblerBlockEntity.select(p, pos, "iron_gear_wheel") == SelectResult.OK, "remote recipe change refused");
        h.assertTrue(be.machine().recipe() != null && be.machine().recipe().id().equals("iron_gear_wheel"), "recipe " + be.machine().recipe());
        be.machine().insertInput(PLATE, 3, false);
        // slot clicks move nothing (quick-move of the plates' slot)
        menu.clicked(0, 0, ClickType.QUICK_MOVE, p);
        menu.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(be.machine().inputs().getOrDefault(PLATE, 0) == 3, "plates moved by a remote click: " + be.machine().inputs());
        h.assertTrue(p.containerMenu.getCarried().isEmpty() && p.getInventory().isEmpty(), "player got items");

        h.assertTrue(AssemblerBlockEntity.select(p, pos, "pipe") == SelectResult.OK, "second recipe change refused");
        h.assertTrue(be.machine().inputs().isEmpty(), "inputs kept " + be.machine().inputs());
        h.assertTrue(p.getInventory().isEmpty(), "plates went to the far player");
        List<ItemEntity> spilled = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
        int plates = spilled.stream().filter(e -> e.getItem().is(ModItems.IRON_PLATE.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(plates == 3, "spilled plates " + plates);
        spilled.forEach(e -> e.discard());

        RemoteView.endForTests(p);
        h.assertTrue(p.containerMenu == p.inventoryMenu, "GUI kept open after the view closed");
        h.assertFalse(menu.stillValid(p), "GUI still valid after the view closed");
        h.succeed();
    }

    /**
     * A blueprint pasted from the remote view over a watched chunk: refused without the view (the
     * player is far away), placed as ghosts with it; parts outside the view's area are refused.
     */
    @GameTest(template = "empty16", batch = BATCH)
    public static void blueprintPasteOverAWatchedChunk(GameTestHelper h) {
        floor(h);
        ServerPlayer p = farPlayer(h, null);
        BlueprintPaste.resetBudget(p);
        ItemStack bp = BlueprintItem.empty();
        bp.set(ModDataComponents.BLUEPRINT.get(), new BlueprintData(List.of(
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 0, DragDir.EAST.id(), 0, Optional.empty()),
                new BlueprintData.Entry("minetorio:transport_belt", 1, 0, 0, DragDir.EAST.id(), 0, Optional.empty()),
                new BlueprintData.Entry("minetorio:transport_belt", 2, 0, 0, DragDir.EAST.id(), 0, Optional.empty()))));
        p.setItemInHand(InteractionHand.MAIN_HAND, bp);
        ChunkPos a = chunkOf(h, new BlockPos(5, 1, 5));
        watchOnly(h, a, 1); // the three cells may straddle a chunk border
        BlockPos anchor = h.absolutePos(new BlockPos(5, 1, 5));
        h.assertFalse(BlueprintPaste.paste(p, anchor, 0, false).accepted(), "pasted from afar without the remote view");
        h.assertTrue(RemoteView.enter(p, centerX(a), centerZ(a)) == RemoteView.Result.OK, "remote view refused");
        BlueprintPaste.resetBudget(p);
        BlueprintPaste.Result r = BlueprintPaste.paste(p, anchor, 0, false);
        h.assertTrue(r.accepted() && r.placed() == 3, "remote paste " + r);
        for (int x = 4; x <= 6; x++) h.assertTrue(h.getBlockState(new BlockPos(x, 1, 5)).is(ModBlocks.GHOST.get()), "no ghost at x " + x);
        RemoteView.endForTests(p);
        for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 15, 4, 15)) {
            if (h.getBlockState(rel).is(ModBlocks.GHOST.get())) h.setBlock(rel, Blocks.AIR);
        }
        h.succeed();
    }

    /** Every way out of the view (exit, logout, death, dimension change) releases its ticket and re-centres chunk tracking. */
    @GameTest(template = "empty16", batch = BATCH)
    public static void leavingReleasesTheTicket(GameTestHelper h) {
        ServerPlayer p = farPlayer(h, null);
        ChunkPos a = chunkOf(h, new BlockPos(5, 1, 5));
        watchOnly(h, a, 0);
        ChunkPos own = p.chunkPosition();
        Runnable[] ways = {
            () -> RemoteView.exit(p),
            () -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p)),
            () -> NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, h.getLevel().damageSources().generic())),
            () -> NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(p, Level.NETHER)),
        };
        String[] names = {"exit", "logout", "death", "dimension change"};
        for (int i = 0; i < ways.length; i++) {
            RemoteView.resetBudgets(p);
            h.assertTrue(RemoteView.enter(p, centerX(a), centerZ(a)) == RemoteView.Result.OK, "enter refused before " + names[i]);
            h.assertTrue(RemoteView.hasTicket(h.getLevel(), a), "no ticket before " + names[i]);
            ways[i].run();
            h.assertFalse(RemoteView.isRemote(p), "still remote after " + names[i]);
            h.assertFalse(RemoteView.hasTicket(h.getLevel(), a), "ticket kept after " + names[i]);
        }
        // the player's own view is eligible without a radar; exiting puts tracking back on his body
        WatchedChunks.wipeForTests(h.getLevel().getServer());
        RemoteView.resetBudgets(p);
        h.assertTrue(RemoteView.enter(p, p.getX() + 16, p.getZ()) == RemoteView.Result.OK, "own view refused");
        h.assertTrue(new ChunkPos(own.x + 1, own.z).equals(trackingCenter(p)), "tracking not on the focus");
        RemoteView.exit(p);
        h.assertTrue(own.equals(trackingCenter(p)), "tracking not back on the body: " + trackingCenter(p));
        h.succeed();
    }
}
