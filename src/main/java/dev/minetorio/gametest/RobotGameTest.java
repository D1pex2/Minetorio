package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.inserter.InserterBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.ConstructionManager;
import dev.minetorio.content.robot.ConstructionRobotEntity;
import dev.minetorio.content.robot.GhostBlockEntity;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.robot.RoboportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ghosts, the roboport and construction robots. Every test with a roboport runs in its own batch:
 * a construction area (54×54) reaches into neighbouring test areas, whose ghosts and chests it
 * would otherwise use.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class RobotGameTest {
    /** Roboport controller (facing SOUTH: footprint x 2..5, z 2..5). */
    private static final BlockPos PORT = new BlockPos(5, 1, 2);
    private static final BlockPos POLE = new BlockPos(6, 1, 3);
    private static final BlockPos CHEST = new BlockPos(8, 1, 7);

    private static void ghost(GameTestHelper h, BlockPos rel, Item item, Direction facing) {
        h.assertTrue(Ghosts.place(h.getLevel(), h.absolutePos(rel), item, facing), "no ghost at " + rel);
    }

    private static GhostBlockEntity ghostAt(GameTestHelper h, BlockPos rel) {
        return (GhostBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    /** A roboport placed like a player would (via a ghost), with {@code robots} robots, powered from a test pole if asked. */
    private static RoboportBlockEntity roboport(GameTestHelper h, int robots, boolean powered) {
        // The 4x4 footprint and the construction destinations need real ground at y=0.
        HandGhostGameTest.floor(h, 0, 15, 0, 15);
        BlockPos abs = h.absolutePos(PORT);
        ghost(h, PORT, ModItems.ROBOPORT.get(), Direction.SOUTH);
        h.assertTrue(Ghosts.build(h.getLevel(), abs, null, new ItemStack(ModItems.ROBOPORT.get())), "roboport not placed");
        RoboportBlockEntity be = (RoboportBlockEntity) h.getLevel().getBlockEntity(abs);
        if (robots > 0) be.robots().setItem(0, new ItemStack(ModItems.CONSTRUCTION_ROBOT.get(), robots));
        if (powered) TestPower.powerAt(h, POLE);
        return be;
    }

    private static ChestBlockEntity chest(GameTestHelper h, ItemStack... items) {
        h.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity c = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CHEST));
        for (int i = 0; i < items.length; i++) c.setItem(i, items[i]);
        return c;
    }

    private static int count(ChestBlockEntity c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static int robotsFlying(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ConstructionRobotEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).size();
    }

    // --- by hand -------------------------------------------------------------------------------

    @GameTest(template = "empty16")
    public static void playerBuildsAGhostWithTheMatchingItem(GameTestHelper h) {
        HandGhostGameTest.floor(h, 0, 15, 0, 15);
        BlockPos belt = new BlockPos(4, 1, 4);
        BlockPos furnace = new BlockPos(8, 1, 8);
        ghost(h, belt, ModItems.TRANSPORT_BELT.get(), Direction.EAST);
        ghost(h, furnace, ModItems.STONE_FURNACE.get(), Direction.SOUTH);
        h.assertTrue(h.getBlockState(belt).is(ModBlocks.GHOST.get()), "ghost placed");
        h.assertTrue(h.getBlockState(belt).getCollisionShape(h.getLevel(), h.absolutePos(belt)).isEmpty(), "ghost has no collision");

        ServerPlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(h.absolutePos(new BlockPos(0, 1, 0)).getCenter());
        // a wrong item does not build it (it would be placed into the replaceable ghost instead: use a non-block item)
        ItemStack plates = new ItemStack(ModItems.IRON_PLATE.get(), 5);
        click(h, player, plates, belt);
        h.assertTrue(h.getBlockState(belt).is(ModBlocks.GHOST.get()) && plates.getCount() == 5, "built with a wrong item");

        ItemStack belts = new ItemStack(ModItems.TRANSPORT_BELT.get(), 3);
        click(h, player, belts, belt);
        h.assertTrue(h.getBlockState(belt).is(ModBlocks.TRANSPORT_BELT.get()), "belt not built: " + h.getBlockState(belt));
        h.assertTrue(h.getBlockState(belt).getValue(BeltBlock.FACING) == Direction.EAST, "belt facing");
        h.assertTrue(belts.getCount() == 2, "one belt used, left " + belts.getCount());

        ItemStack furnaces = new ItemStack(ModItems.STONE_FURNACE.get(), 1);
        click(h, player, furnaces, furnace);
        h.assertTrue(h.getBlockState(furnace).is(ModBlocks.STONE_FURNACE.get()), "furnace controller not at the ghost: " + h.getBlockState(furnace));
        h.assertTrue(h.getBlockState(furnace).getValue(MultiblockControllerBlock.FACING) == Direction.SOUTH, "furnace facing");
        h.assertTrue(h.getBlockState(new BlockPos(7, 1, 9)).is(ModBlocks.MULTIBLOCK_PART.get()), "furnace footprint (x 8..7, z 8..9)");
        h.assertTrue(furnaces.isEmpty(), "furnace used");
        h.assertTrue(ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(h.absolutePos(belt))).isEmpty(),
                "built ghost left its job");
        h.succeed();
    }

    private static void click(GameTestHelper h, ServerPlayer player, ItemStack stack, BlockPos rel) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.gameMode.useItemOn(player, h.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = "empty16")
    public static void breakingAGhostRemovesItWithoutDrops(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 1, 4);
        ghost(h, rel, ModItems.INSERTER.get(), Direction.NORTH);
        BlockPos abs = h.absolutePos(rel);
        h.assertTrue(ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(abs)).isPresent(), "job registered");
        h.getLevel().destroyBlock(abs, true);
        h.assertTrue(h.getBlockState(rel).isAir(), "ghost still there");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2)).isEmpty(), "ghost dropped something");
        h.assertTrue(ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(abs)).isEmpty(), "job left behind");
        h.succeed();
    }

    /** A ghost saved and loaded back (as a chunk reload does) keeps its target, facing and queue place. */
    @GameTest(template = "empty16")
    public static void ghostSurvivesSaveAndLoad(GameTestHelper h) {
        BlockPos rel = new BlockPos(5, 1, 5);
        ghost(h, rel, ModItems.INSERTER.get(), Direction.WEST);
        GhostBlockEntity be = ghostAt(h, rel);
        long seq = be.seq();
        h.assertTrue(seq > 0, "ghost has a queue place");
        var registries = h.getLevel().registryAccess();
        CompoundTag tag = be.saveWithFullMetadata(registries);
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(abs)).isEmpty(), "job left after unload");
        h.getLevel().setBlock(abs, ModBlocks.GHOST.get().defaultBlockState(), Block.UPDATE_ALL);
        GhostBlockEntity loaded = ghostAt(h, rel);
        loaded.loadWithComponents(tag, registries);
        h.runAfterDelay(2, () -> { // onLoad runs with the level's next block entity tick
            h.assertTrue(loaded.target().is(ModBlocks.INSERTER.get()), "target " + loaded.target());
            h.assertTrue(loaded.target().getValue(InserterBlock.FACING) == Direction.WEST, "target facing");
            h.assertTrue(loaded.facing() == Direction.WEST, "placement facing");
            h.assertTrue(loaded.seq() == seq, "seq " + loaded.seq() + " != " + seq);
            var job = ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(abs));
            h.assertTrue(job.isPresent() && job.get().seq() == seq && "minetorio:inserter".equals(job.get().item()), "job not back");
            h.succeed();
        });
    }

    // --- robots --------------------------------------------------------------------------------

    /** Belts with a curve, an inserter and a stone furnace, built by robots from a chest; the robots come home. */
    @GameTest(template = "empty16", batch = "robots_build", timeoutTicks = 1200)
    public static void robotsBuildGhostsFromAChest(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4, true);
        ChestBlockEntity c = chest(h, new ItemStack(ModItems.TRANSPORT_BELT.get(), 10), new ItemStack(ModItems.INSERTER.get(), 2),
                new ItemStack(ModItems.STONE_FURNACE.get(), 1));
        for (int x = 2; x <= 5; x++) ghost(h, new BlockPos(x, 1, 10), ModItems.TRANSPORT_BELT.get(), Direction.EAST);
        ghost(h, new BlockPos(6, 1, 10), ModItems.TRANSPORT_BELT.get(), Direction.SOUTH); // curve
        ghost(h, new BlockPos(6, 1, 11), ModItems.TRANSPORT_BELT.get(), Direction.SOUTH);
        ghost(h, new BlockPos(6, 1, 12), ModItems.INSERTER.get(), Direction.SOUTH);
        ghost(h, new BlockPos(11, 1, 11), ModItems.STONE_FURNACE.get(), Direction.SOUTH);
        boolean[] launched = {false};
        boolean[] opened = {false};
        boolean[] charged = {false};
        h.onEachTick(() -> {
            if (robotsFlying(h) > 0) launched[0] = true;
            if (port.doorOpen()) opened[0] = true;
            if (port.chargingRobots() > 0) charged[0] = true;
        });
        h.succeedWhen(() -> {
            for (int x = 2; x <= 6; x++) h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(x, 1, 10));
            h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(6, 1, 11));
            h.assertBlockPresent(ModBlocks.INSERTER.get(), new BlockPos(6, 1, 12));
            h.assertBlockPresent(ModBlocks.STONE_FURNACE.get(), new BlockPos(11, 1, 11));
            h.assertTrue(h.getBlockState(new BlockPos(6, 1, 10)).getValue(BeltBlock.SHAPE) != BeltBlock.Shape.STRAIGHT, "no curve");
            h.assertTrue(h.getBlockState(new BlockPos(6, 1, 12)).getValue(InserterBlock.FACING) == Direction.SOUTH, "inserter facing");
            h.assertTrue(count(c, ModItems.TRANSPORT_BELT.get()) == 4 && count(c, ModItems.INSERTER.get()) == 1
                    && count(c, ModItems.STONE_FURNACE.get()) == 0, "items not taken from the chest");
            h.assertTrue(launched[0], "never saw a robot fly");
            h.assertTrue(robotsFlying(h) == 0 && port.availableRobots() == 4 && port.robotsOut() == 0,
                    "robots not home: " + port.availableRobots() + " stored, " + robotsFlying(h) + " flying");
            h.assertTrue(opened[0], "the roof iris never opened");
            h.assertTrue(charged[0], "never saw a robot charging");
            h.assertTrue(!port.doorOpen() && port.chargingRobots() == 0, "the iris did not close after the robots came home");
            c.clearContent(); // passed: the chest stays in the world, its leftovers must not feed later roboport tests
        });
    }

    @GameTest(template = "empty16", batch = "robots_no_item", timeoutTicks = 200)
    public static void ghostStaysWithoutItemInChests(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4, true);
        chest(h, new ItemStack(ModItems.INSERTER.get(), 3));
        ghost(h, new BlockPos(3, 1, 10), ModItems.TRANSPORT_BELT.get(), Direction.EAST);
        h.runAfterDelay(120, () -> {
            h.assertBlockPresent(ModBlocks.GHOST.get(), new BlockPos(3, 1, 10));
            h.assertTrue(port.availableRobots() == 4 && robotsFlying(h) == 0, "a robot was sent");
            h.assertTrue(port.buffer() > 0, "roboport unpowered");
            h.succeed();
        });
    }

    @GameTest(template = "empty16", batch = "robots_out_of_area", timeoutTicks = 200)
    public static void robotsIgnoreGhostsOutsideTheArea(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4, true);
        chest(h, new ItemStack(ModItems.TRANSPORT_BELT.get(), 5));
        BlockPos high = new BlockPos(3, 1 + 30, 3); // 30 blocks above the roboport floor: outside its 27-block area
        ghost(h, high, ModItems.TRANSPORT_BELT.get(), Direction.EAST);
        h.assertFalse(port.area().contains(ConstructionManager.cell(h.absolutePos(high))), "test ghost inside the area");
        h.runAfterDelay(120, () -> {
            h.assertTrue(h.getBlockState(high).is(ModBlocks.GHOST.get()), "out-of-area ghost built");
            h.assertTrue(port.availableRobots() == 4 && robotsFlying(h) == 0, "a robot was sent");
            h.getLevel().setBlock(h.absolutePos(high), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            h.succeed();
        });
    }

    @GameTest(template = "empty16", batch = "robots_no_power", timeoutTicks = 200)
    public static void unpoweredRoboportSendsNoRobots(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4, false);
        chest(h, new ItemStack(ModItems.TRANSPORT_BELT.get(), 5));
        ghost(h, new BlockPos(3, 1, 10), ModItems.TRANSPORT_BELT.get(), Direction.EAST);
        h.runAfterDelay(120, () -> {
            h.assertBlockPresent(ModBlocks.GHOST.get(), new BlockPos(3, 1, 10));
            h.assertTrue(port.buffer() == 0, "buffer filled without power: " + port.buffer());
            // (only this roboport's robots: a neighbouring test area's robots may fly past)
            h.assertTrue(port.availableRobots() == 4 && port.robotsOut() == 0, "a robot was sent");
            // powered afterwards, the same roboport builds it
            TestPower.powerAt(h, POLE);
            h.succeedWhen(() -> h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(3, 1, 10)));
        });
    }

    /** A ghost line laid by hand (ghost key + drag, no items) is built by the robots from the chest, curve included. */
    @GameTest(template = "empty16", batch = "robots_hand_ghosts", timeoutTicks = 1200)
    public static void robotsBuildAHandPlacedGhostLine(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4, true);
        ChestBlockEntity c = chest(h, new ItemStack(ModItems.TRANSPORT_BELT.get(), 10));
        HandGhostGameTest.floor(h, 1, 10, 11, 13);
        ServerPlayer player = HandGhostGameTest.player(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(5, 1, 13), Direction.EAST);
        java.util.List<dev.minetorio.core.drag.DragDir> path = new java.util.ArrayList<>(
                java.util.Collections.nCopies(4, dev.minetorio.core.drag.DragDir.EAST));
        path.add(dev.minetorio.core.drag.DragDir.SOUTH);
        HandGhostGameTest.ghostDrag(h, player, new BlockPos(2, 1, 11), Direction.EAST, path);
        for (int x = 2; x <= 6; x++) h.assertBlockPresent(ModBlocks.GHOST.get(), new BlockPos(x, 1, 11));
        h.assertBlockPresent(ModBlocks.GHOST.get(), new BlockPos(6, 1, 12));
        h.assertTrue(player.getMainHandItem().getCount() == 1, "the drag used items");
        h.succeedWhen(() -> {
            for (int x = 2; x <= 5; x++) {
                h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(x, 1, 11));
                h.assertTrue(h.getBlockState(new BlockPos(x, 1, 11)).getValue(BeltBlock.FACING) == Direction.EAST, "belt facing at x " + x);
            }
            h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(6, 1, 11));
            h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), new BlockPos(6, 1, 12));
            h.assertTrue(h.getBlockState(new BlockPos(6, 1, 11)).getValue(BeltBlock.FACING) == Direction.SOUTH, "corner facing");
            h.assertTrue(h.getBlockState(new BlockPos(6, 1, 11)).getValue(BeltBlock.SHAPE) != BeltBlock.Shape.STRAIGHT, "no curve");
            h.assertTrue(count(c, ModItems.TRANSPORT_BELT.get()) == 4, "items not taken from the chest");
            h.assertTrue(robotsFlying(h) == 0 && port.availableRobots() == 4, "robots not home");
            c.clearContent(); // passed: the chest stays in the world, its leftovers must not feed later roboport tests
        });
    }

    // --- robots and chunks that do not tick ---------------------------------------------------

    /**
     * A chunk edge-adjacent to the chest's chunk (a robot flies there from the chest through those two
     * chunks only) that is loaded but not entity-ticking, with its middle cell inside the roboport's
     * area: that cell (absolute), at floor height. Null if the test layout offers none.
     */
    private static BlockPos nonTickingCell(GameTestHelper h, RoboportBlockEntity port) {
        BlockPos chest = h.absolutePos(CHEST);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int cx = (chest.getX() >> 4) + d[0], cz = (chest.getZ() >> 4) + d[1];
            BlockPos mid = new BlockPos((cx << 4) + 8, chest.getY(), (cz << 4) + 8);
            if (h.getLevel().hasChunk(cx, cz) && !h.getLevel().isPositionEntityTicking(mid)
                    && port.area().contains(ConstructionManager.cell(mid))) {
                return mid;
            }
        }
        return null;
    }

    // positions outside the test structure are absolute (GameTestHelper.relativePos turns them by 180°)

    /** A belt ghost outside the test structure, with a floor under it. */
    private static void outsideGhost(GameTestHelper h, BlockPos abs) {
        h.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        h.getLevel().setBlock(abs, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(Ghosts.place(h.getLevel(), abs, ModItems.TRANSPORT_BELT.get(), Direction.EAST), "no ghost at " + abs);
    }

    private static void forceChunk(GameTestHelper h, BlockPos abs, boolean on) {
        h.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, on);
    }

    private static boolean ticking(GameTestHelper h, BlockPos abs) {
        return h.getLevel().isPositionEntityTicking(abs);
    }

    private static boolean isAt(GameTestHelper h, BlockPos abs, Block block) {
        return h.getLevel().getBlockState(abs).is(block);
    }

    private static java.util.Optional<dev.minetorio.core.robot.ConstructionQueue.Job> job(GameTestHelper h, BlockPos abs) {
        return ConstructionManager.get(h.getLevel()).queue().get(ConstructionManager.cell(abs));
    }

    private static java.util.List<ConstructionRobotEntity> robots(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ConstructionRobotEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(60));
    }

    /**
     * A ghost in the area but in a chunk that does not tick (a roboport at the edge of the loaded
     * area) gets no robot: one flying there would freeze holding the job. The ghost in the test
     * area is built and every robot comes home.
     */
    @GameTest(template = "empty16", batch = "robots_non_ticking_ghost", timeoutTicks = 600)
    public static void ghostInANonTickingChunkIsNotDispatched(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4, true);
        ChestBlockEntity c = chest(h, new ItemStack(ModItems.TRANSPORT_BELT.get(), 5));
        BlockPos away = nonTickingCell(h, port);
        h.assertTrue(away != null, "no loaded, non-ticking chunk next to the test area");
        outsideGhost(h, away);
        BlockPos inside = new BlockPos(3, 1, 10);
        ghost(h, inside, ModItems.TRANSPORT_BELT.get(), Direction.EAST);
        boolean[] sent = {false};
        h.onEachTick(() -> {
            if (job(h, away).map(j -> j.isClaimed()).orElse(false)) sent[0] = true;
        });
        h.runAfterDelay(200, () -> h.succeedWhen(() -> {
            h.assertBlockPresent(ModBlocks.TRANSPORT_BELT.get(), inside);
            h.assertFalse(sent[0], "a robot took the ghost in the non-ticking chunk");
            h.assertTrue(isAt(h, away, ModBlocks.GHOST.get()), "the ghost in the non-ticking chunk changed");
            h.assertTrue(robots(h).isEmpty() && port.availableRobots() == 4 && port.robotsOut() == 0,
                    "robots not home: " + port.availableRobots() + " stored, " + robots(h).size() + " flying");
            h.assertTrue(count(c, ModItems.TRANSPORT_BELT.get()) == 4, "chest " + count(c, ModItems.TRANSPORT_BELT.get()));
            h.getLevel().removeBlock(away, false);
            c.clearContent();
        }));
    }

    /**
     * A robot on its way to a ghost whose chunk stops ticking under it (the player walked away)
     * freezes; its claim is released, and what it saves (as an unload does) holds no job. Once the
     * chunk ticks again, the robot loaded back takes its belt to the chest and comes home, and
     * another robot builds the ghost.
     */
    @GameTest(template = "empty16", batch = "robots_chunk_stops_ticking", timeoutTicks = 1600)
    public static void robotInAChunkThatStopsTickingReleasesItsJob(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 2, true);
        ChestBlockEntity c = chest(h, new ItemStack(ModItems.TRANSPORT_BELT.get(), 5));
        BlockPos away = nonTickingCell(h, port);
        h.assertTrue(away != null, "no loaded, non-ticking chunk next to the test area");
        outsideGhost(h, away);
        forceChunk(h, away, true);
        int[] stage = {0};
        long[] firstId = {0};
        boolean[] other = {false};
        CompoundTag saved = new CompoundTag();
        ConstructionRobotEntity[] first = {null};
        int[] wait = {0};
        h.onEachTick(() -> {
            var job = job(h, away);
            if (job.isPresent() && job.get().isClaimed() && firstId[0] != 0 && job.get().claimant() != firstId[0]) other[0] = true;
            switch (stage[0]) {
                case 0 -> { // the first robot flies into the ghost's chunk: it stops ticking
                    for (ConstructionRobotEntity r : robots(h)) {
                        if (r.phase() == ConstructionRobotEntity.Phase.TO_GHOST
                                && (r.blockPosition().getX() >> 4) == (away.getX() >> 4)
                                && (r.blockPosition().getZ() >> 4) == (away.getZ() >> 4)) {
                            first[0] = r;
                            firstId[0] = r.robotId();
                            forceChunk(h, away, false);
                            stage[0] = 1;
                            break;
                        }
                    }
                }
                case 1 -> { // frozen there: the sweep releases its claim
                    if (!ticking(h, away) && job.isPresent() && !job.get().isClaimed() && !first[0].holdsJob()) {
                        h.assertTrue(first[0].save(saved), "robot not saved");
                        h.assertFalse(saved.contains("job"), "the saved robot still holds the job");
                        first[0].remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                        stage[0] = 2;
                    }
                }
                case 2 -> { // unloaded: the job stays free, nobody is sent to the non-ticking chunk
                    h.assertTrue(job.isPresent() && !job.get().isClaimed(), "the job was claimed while its chunk does not tick");
                    h.assertTrue(port.availableRobots() == 1, "a robot was sent to the non-ticking chunk");
                    if (++wait[0] >= 40) {
                        forceChunk(h, away, true);
                        stage[0] = 3;
                    }
                }
                case 3 -> { // the chunk ticks again: the robot is loaded back
                    if (ticking(h, away)) {
                        var loaded = net.minecraft.world.entity.EntityType.create(saved, h.getLevel());
                        h.assertTrue(loaded.isPresent(), "robot not loaded");
                        h.assertTrue(h.getLevel().addFreshEntity(loaded.get()), "robot not added");
                        stage[0] = 4;
                    }
                }
                default -> {}
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stage[0] == 4, "stage " + stage[0] + " ticking " + ticking(h, away) + " job " + job(h, away).map(j -> j.claimant())
                    + " away " + away + " port " + port.availableRobots() + " robots " + robots(h).stream()
                    .map(r -> r.phase() + "@" + r.blockPosition()).toList());
            h.assertTrue(isAt(h, away, ModBlocks.TRANSPORT_BELT.get()), "the ghost was not built");
            h.assertTrue(other[0], "the job was not done by another robot");
            h.assertTrue(robots(h).isEmpty() && port.availableRobots() == 2 && port.robotsOut() == 0,
                    "robots not home: " + port.availableRobots() + " stored, " + robots(h).size() + " flying, out " + port.robotsOut());
            h.assertTrue(count(c, ModItems.TRANSPORT_BELT.get()) == 4, "chest " + count(c, ModItems.TRANSPORT_BELT.get()));
            forceChunk(h, away, false);
            h.getLevel().removeBlock(away, false);
            c.clearContent();
        });
    }
}
