package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.ConstructionManager;
import dev.minetorio.content.robot.ConstructionMarks;
import dev.minetorio.content.robot.ConstructionRobotEntity;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.robot.Planners;
import dev.minetorio.content.robot.RoboportBlockEntity;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.robot.DeconstructionFilter;
import dev.minetorio.core.robot.UpgradeRules;
import dev.minetorio.core.selection.SelectionBox;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Deconstruction and upgrade planners: marking, unmarking, persistence and the robots carrying out
 * the orders. Tests with a roboport run in batches of their own (its area reaches into neighbouring
 * test areas).
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class PlannerGameTest {
    private static final BlockPos PORT = new BlockPos(5, 1, 2);
    private static final BlockPos POLE = new BlockPos(6, 1, 3);
    private static final BlockPos CHEST = new BlockPos(8, 1, 7);
    /** Where the marked things stand: z 9..15, clear of the roboport, its pole and the chest. */
    private static final int Z0 = 9;

    private static RoboportBlockEntity roboport(GameTestHelper h, int robots) {
        BlockPos abs = h.absolutePos(PORT);
        for (BlockPos cell : BlockPos.betweenClosed(abs.offset(-3, -1, 0), abs.offset(0, -1, 3))) {
            h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
        }
        h.assertTrue(Ghosts.place(h.getLevel(), abs, ModItems.ROBOPORT.get(), Direction.SOUTH), "no roboport ghost");
        h.assertTrue(Ghosts.build(h.getLevel(), abs, null, new ItemStack(ModItems.ROBOPORT.get())), "roboport not placed");
        RoboportBlockEntity be = (RoboportBlockEntity) h.getLevel().getBlockEntity(abs);
        be.robots().setItem(0, new ItemStack(ModItems.CONSTRUCTION_ROBOT.get(), robots));
        TestPower.powerAt(h, POLE);
        isolate(h, be);
        h.onEachTick(() -> isolate(h, be));
        return be;
    }

    /**
     * Roboports of earlier tests stay in the world and their areas reach into this one: they would
     * take this test's jobs (deconstruction only needs a chest with room). Their robots are taken
     * away (every tick: their chunks may load again later). Robots never go where they would not tick
     * (outside the test area), so nothing else needs clearing.
     */
    private static void isolate(GameTestHelper h, RoboportBlockEntity mine) {
        for (BlockPos p : ConstructionManager.get(h.getLevel()).roboports()) {
            if (!p.equals(mine.getBlockPos()) && h.getLevel().getBlockEntity(p) instanceof RoboportBlockEntity other) {
                other.robots().clearContent();
            }
        }
    }

    /** Passed: this test's roboport must not serve later tests. */
    private static void retire(RoboportBlockEntity port, ChestBlockEntity chest) {
        port.robots().clearContent();
        chest.clearContent();
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

    /** The test area's box from {@code (x0, y0, z0)} to {@code (x1, y1, z1)} (relative), in world coordinates. */
    private static SelectionBox box(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1) {
        BlockPos a = h.absolutePos(new BlockPos(x0, y0, z0)), b = h.absolutePos(new BlockPos(x1, y1, z1));
        return SelectionBox.of(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    private static SelectionBox markArea(GameTestHelper h) {
        return box(h, 0, 1, Z0, 15, 4, 15);
    }

    private static ConstructionMarks marks(GameTestHelper h) {
        return ConstructionMarks.get(h.getLevel());
    }

    private static boolean marked(GameTestHelper h, BlockPos rel) {
        return marks(h).isMarked(h.getLevel(), h.absolutePos(rel));
    }

    private static int robotsFlying(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ConstructionRobotEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).size();
    }

    private static String debug(GameTestHelper h, BlockPos rel, RoboportBlockEntity port) {
        var cell = ConstructionManager.cell(h.absolutePos(rel));
        var job = ConstructionManager.get(h.getLevel()).queue().get(cell);
        StringBuilder sb = new StringBuilder();
        sb.append("marked=").append(marks(h).deconstruction().isMarked(cell)).append(" up=").append(marks(h).upgrades().get(cell));
        sb.append(" job=").append(job.map(j -> j.kind() + "/claim " + j.claimant()).orElse("none"));
        sb.append(" area=").append(port.area().contains(cell)).append(" robots=").append(port.availableRobots()).append(" buf=").append(port.buffer());
        for (ConstructionRobotEntity r : h.getLevel().getEntitiesOfClass(ConstructionRobotEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))) {
            sb.append(" robot ").append(r.phase()).append(' ').append(r.jobKind()).append(' ').append(r.cargo())
                    .append(" at ").append(r.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)))
                    .append(" home ").append(r.home() == null ? null : r.home().subtract(h.absolutePos(BlockPos.ZERO)));
        }
        return sb.toString();
    }

    // --- marking -----------------------------------------------------------------------------------

    /** Minetorio blocks get marked (a machine by its controller, whatever piece is inside), terrain never; Shift unmarks. */
    @GameTest(template = "empty16")
    public static void markAndUnmarkWithShift(GameTestHelper h) {
        BlockPos belt = new BlockPos(3, 1, 12);
        BlockPos asm = new BlockPos(11, 1, 12);
        BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), belt, Direction.EAST);
        MachineGameTest.assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), asm, null);
        h.setBlock(new BlockPos(6, 1, 12), Blocks.STONE);
        var r = Planners.deconstruct(h.getLevel(), markArea(h), false, DeconstructionFilter.MACHINES, p -> true);
        h.assertTrue(r.changed() == 2, "marked " + r.changed());
        h.assertTrue(marked(h, belt) && marked(h, asm), "belt or assembler not marked");
        h.assertFalse(marked(h, new BlockPos(6, 1, 12)), "stone marked");
        // a piece of the machine stands for the whole machine
        BlockPos part = null;
        for (BlockPos p : ((MultiblockControllerBlock) h.getBlockState(asm).getBlock()).footprint(h.absolutePos(asm), h.getBlockState(asm))) {
            if (h.getLevel().getBlockState(p).getBlock() instanceof MultiblockPartBlock) part = p;
        }
        h.assertTrue(part != null && marks(h).isMarked(h.getLevel(), part), "part does not report its machine's mark");
        // the queue has deconstruction jobs for them
        h.assertTrue(ConstructionManager.get(h.getLevel()).queue().isMarkedForDeconstruction(ConstructionManager.cell(h.absolutePos(belt))),
                "no deconstruction job");
        // Shift: unmark only part of the area
        var u = Planners.deconstruct(h.getLevel(), box(h, 0, 1, Z0, 5, 2, 15), true, DeconstructionFilter.MACHINES, p -> true);
        h.assertTrue(u.changed() == 1, "unmarked " + u.changed());
        h.assertFalse(marked(h, belt), "belt still marked");
        h.assertTrue(marked(h, asm), "assembler lost its mark");
        h.assertFalse(ConstructionManager.get(h.getLevel()).queue().isMarkedForDeconstruction(ConstructionManager.cell(h.absolutePos(belt))),
                "job left after unmarking");
        Planners.deconstruct(h.getLevel(), markArea(h), true, DeconstructionFilter.MACHINES, p -> true);
        h.assertFalse(marked(h, asm), "assembler still marked");
        h.succeed();
    }

    /** Trees and rocks are marked only with the toggle; "only" leaves machines alone. */
    @GameTest(template = "empty16")
    public static void treesAndRocksToggle(GameTestHelper h) {
        BlockPos log = new BlockPos(5, 1, 12), leaves = new BlockPos(5, 2, 12), boulder = new BlockPos(7, 1, 12);
        BlockPos belt = new BlockPos(3, 1, 12), dirt = new BlockPos(9, 1, 12);
        h.setBlock(log, Blocks.OAK_LOG);
        h.setBlock(leaves, Blocks.OAK_LEAVES);
        h.setBlock(boulder, Blocks.MOSSY_COBBLESTONE);
        h.setBlock(dirt, Blocks.DIRT);
        BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), belt, Direction.EAST);
        Planners.deconstruct(h.getLevel(), markArea(h), false, DeconstructionFilter.MACHINES, p -> true);
        h.assertTrue(marked(h, belt) && !marked(h, log) && !marked(h, leaves) && !marked(h, boulder), "default marks trees");
        Planners.deconstruct(h.getLevel(), markArea(h), true, DeconstructionFilter.MACHINES, p -> true);
        Planners.deconstruct(h.getLevel(), markArea(h), false, DeconstructionFilter.NATURE_ONLY, p -> true);
        h.assertTrue(!marked(h, belt) && marked(h, log) && marked(h, leaves) && marked(h, boulder), "trees-only filter");
        h.assertFalse(marked(h, dirt), "dirt marked");
        Planners.deconstruct(h.getLevel(), markArea(h), false, DeconstructionFilter.MACHINES_AND_NATURE, p -> true);
        h.assertTrue(marked(h, belt) && marked(h, log), "both");
        Planners.deconstruct(h.getLevel(), markArea(h), true, DeconstructionFilter.MACHINES_AND_NATURE, p -> true);
        h.assertTrue(!marked(h, belt) && !marked(h, log) && !marked(h, leaves), "shift with both unmarks all");
        h.succeed();
    }

    /** Marks are saved (SavedData) and loaded back; a mark whose block changed is dropped by the sweep. */
    @GameTest(template = "empty16")
    public static void marksSurviveSaveAndLoad(GameTestHelper h) {
        BlockPos belt = new BlockPos(3, 1, 12), inserter = new BlockPos(4, 1, 12);
        BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), belt, Direction.EAST);
        BeltGameTest.place(h, ModItems.INSERTER.get(), inserter, Direction.EAST);
        ConstructionMarks m = marks(h);
        m.mark(h.getLevel(), h.absolutePos(belt));
        m.markUpgrade(h.getLevel(), h.absolutePos(inserter), "minetorio:fast_inserter", 1);
        h.assertTrue(m.isDirty(), "marks not flagged for saving");
        var registries = h.getLevel().registryAccess();
        CompoundTag tag = m.save(new CompoundTag(), registries);
        ConstructionMarks loaded = ConstructionMarks.load(tag, registries);
        h.assertTrue(loaded.isMarked(h.getLevel(), h.absolutePos(belt)), "deconstruction mark lost");
        var order = loaded.upgradeOrder(h.getLevel(), h.absolutePos(inserter));
        h.assertTrue(order != null && order.toItem().equals("minetorio:fast_inserter") && order.fromBlock().equals("minetorio:inserter"),
                "upgrade order lost: " + order);
        // the block changes: the mark goes
        h.setBlock(belt, Blocks.STONE);
        h.runAfterDelay(25, () -> {
            h.assertFalse(marked(h, belt), "mark kept on a changed block");
            h.assertTrue(m.upgradeOrder(h.getLevel(), h.absolutePos(inserter)) != null, "valid upgrade order dropped");
            m.unmarkUpgrade(h.getLevel(), h.absolutePos(inserter));
            h.succeed();
        });
    }

    /** A player breaking a marked block by hand clears its mark at once. */
    @GameTest(template = "empty16")
    public static void handBreakingClearsTheMark(GameTestHelper h) {
        BlockPos belt = new BlockPos(3, 1, 12);
        BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), belt, Direction.EAST);
        marks(h).mark(h.getLevel(), h.absolutePos(belt));
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setGameMode(GameType.SURVIVAL);
        p.gameMode.destroyBlock(h.absolutePos(belt));
        h.assertTrue(h.getBlockState(belt).isAir(), "belt not broken");
        h.assertFalse(marked(h, belt), "mark kept after hand breaking");
        h.succeed();
    }

    /** Ghosts in a deconstruction selection are cancelled at once. */
    @GameTest(template = "empty16")
    public static void deconstructionRemovesGhosts(GameTestHelper h) {
        BlockPos g = new BlockPos(3, 1, 12);
        h.assertTrue(Ghosts.place(h.getLevel(), h.absolutePos(g), ModItems.TRANSPORT_BELT.get(), Direction.EAST), "no ghost");
        var r = Planners.deconstruct(h.getLevel(), markArea(h), false, DeconstructionFilter.MACHINES, p -> true);
        h.assertTrue(r.ghostsRemoved() == 1 && h.getBlockState(g).isAir(), "ghost not removed");
        h.succeed();
    }

    /** The upgrade planner marks only blocks a rule maps; a paired underground belt once for the pair. */
    @GameTest(template = "empty16")
    public static void upgradeMarksFollowTheRules(GameTestHelper h) {
        BlockPos belt = new BlockPos(3, 1, 12), ins = new BlockPos(4, 1, 13);
        BlockPos in = new BlockPos(6, 1, 12), out = new BlockPos(9, 1, 12);
        BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), belt, Direction.EAST);
        BeltGameTest.place(h, ModItems.INSERTER.get(), ins, Direction.EAST);
        BeltGameTest.place(h, ModItems.UNDERGROUND_BELT.get(), in, Direction.EAST);
        BeltGameTest.place(h, ModItems.UNDERGROUND_BELT.get(), out, Direction.EAST);
        UpgradeRules rules = new UpgradeRules(List.of(
                new UpgradeRules.Rule("minetorio:transport_belt", "minetorio:fast_transport_belt"),
                new UpgradeRules.Rule("minetorio:underground_belt", "minetorio:fast_underground_belt")));
        var r = Planners.upgrade(h.getLevel(), markArea(h), false, rules, p -> true);
        h.assertTrue(r.changed() == 2, "marked " + r.changed());
        var o = marks(h).upgradeOrder(h.getLevel(), h.absolutePos(belt));
        h.assertTrue(o != null && o.toItem().equals("minetorio:fast_transport_belt"), "belt order " + o);
        h.assertTrue(marks(h).upgradeOrder(h.getLevel(), h.absolutePos(ins)) == null, "inserter marked without a rule");
        var a = marks(h).upgradeOrder(h.getLevel(), h.absolutePos(in));
        var b = marks(h).upgradeOrder(h.getLevel(), h.absolutePos(out));
        h.assertTrue((a == null) != (b == null), "pair marked " + a + " / " + b);
        h.assertTrue((a != null ? a : b).count() == 2, "pair order takes two items");
        var c = Planners.upgrade(h.getLevel(), markArea(h), true, rules, p -> true);
        h.assertTrue(c.changed() == 2 && marks(h).upgradeOrder(h.getLevel(), h.absolutePos(belt)) == null, "Shift did not cancel");
        h.succeed();
    }

    /** A blueprint marks another belt tier for replacement, including its required direction. */
    @GameTest(template = "empty16")
    public static void blueprintPasteMarksOtherTierForUpgrade(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 1, 12), turned = new BlockPos(6, 1, 12);
        BeltGameTest.place(h, ModItems.FAST_TRANSPORT_BELT.get(), at, Direction.EAST);
        BeltGameTest.place(h, ModItems.FAST_TRANSPORT_BELT.get(), turned, Direction.NORTH);
        ServerPlayer p = BlueprintGameTest.player(h, new BlockPos(1, 1, 1));
        BlueprintGameTest.beltBlueprint(p); // one yellow belt facing east
        var r = BlueprintGameTest.paste(h, p, at, dev.minetorio.core.blueprint.BlueprintTransform.IDENTITY, false);
        h.assertTrue(r.placed() == 1 && r.blocked() == 0, "paste " + r);
        var o = marks(h).upgradeOrder(h.getLevel(), h.absolutePos(at));
        h.assertTrue(o != null && o.toItem().equals("minetorio:transport_belt"), "no upgrade order: " + o);
        var r2 = BlueprintGameTest.paste(h, p, turned, dev.minetorio.core.blueprint.BlueprintTransform.IDENTITY, false);
        var turnedOrder = marks(h).upgradeOrder(h.getLevel(), h.absolutePos(turned));
        h.assertTrue(r2.placed() == 1 && r2.blocked() == 0 && turnedOrder != null && turnedOrder.facing() == 1,
                "a belt facing another way must get the blueprint's eastward replacement: " + r2);
        marks(h).unmarkUpgrade(h.getLevel(), h.absolutePos(at));
        marks(h).unmarkUpgrade(h.getLevel(), h.absolutePos(turned));
        p.getInventory().clearContent();
        h.succeed();
    }

    // --- robots ------------------------------------------------------------------------------------

    /** Belts and an assembler (with plates inside) are taken apart by robots; everything arrives in the chest. */
    @GameTest(template = "empty16", batch = "planner_robots_deconstruct", timeoutTicks = 1600)
    public static void robotsDeconstructIntoAChest(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4);
        // seeded with what comes back: robots prefer a chest already holding an item (old chests of
        // earlier tests stand nearby too)
        ChestBlockEntity c = chest(h, new ItemStack(ModItems.TRANSPORT_BELT.get(), 1), new ItemStack(ModItems.ASSEMBLING_MACHINE_1.get(), 1));
        for (int x = 2; x <= 4; x++) BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(x, 1, 12), Direction.EAST);
        BlockPos asm = new BlockPos(11, 1, 12);
        AssemblerBlockEntity be = MachineGameTest.assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), asm, "iron_gear_wheel");
        be.machine().insertInput("minetorio:iron_plate", 4, false);
        Planners.deconstruct(h.getLevel(), markArea(h), false, DeconstructionFilter.MACHINES, p -> true);
        h.succeedWhen(() -> {
            for (int x = 2; x <= 4; x++) h.assertTrue(h.getBlockState(new BlockPos(x, 1, 12)).isAir(), "belt " + x + " still there; " + debug(h, new BlockPos(x, 1, 12), port));
            h.assertTrue(h.getBlockState(asm).isAir(), "assembler still there");
            h.assertTrue(count(c, ModItems.TRANSPORT_BELT.get()) == 4, "belts in chest " + count(c, ModItems.TRANSPORT_BELT.get()));
            h.assertTrue(count(c, ModItems.ASSEMBLING_MACHINE_1.get()) == 2, "assembler not in the chest");
            h.assertTrue(count(c, ModItems.IRON_PLATE.get()) == 4, "plates in chest " + count(c, ModItems.IRON_PLATE.get()));
            h.assertTrue(BeltGameTest.dropped(h, ModItems.TRANSPORT_BELT.get()) == 0, "items dropped on the ground");
            h.assertTrue(robotsFlying(h) == 0 && port.availableRobots() == 4, "robots not home; " + debug(h, new BlockPos(2, 1, 12), port));
            h.assertFalse(marked(h, asm), "marks left");
            retire(port, c);
        });
    }

    private static dev.minetorio.content.logistics.LogisticChestBlockEntity logisticChest(GameTestHelper h, BlockPos rel,
            dev.minetorio.core.logistics.LogisticMode mode, ItemStack... items) {
        h.setBlock(rel, dev.minetorio.content.logistics.LogisticsRegistry.CHESTS.get(mode).get());
        var c = (dev.minetorio.content.logistics.LogisticChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        for (int i = 0; i < items.length; i++) c.container().setItem(i, items[i]);
        return c;
    }

    /**
     * Construction robots and logistic chests (Factorio): what they take apart goes to a storage
     * chest, never into a provider chest (here one already holding belts, which a plain chest would win with).
     */
    @GameTest(template = "empty16", batch = "planner_robots_logistic_storage", timeoutTicks = 1600)
    public static void robotsDeconstructIntoStorageNotProviderChests(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 2);
        var provider = logisticChest(h, new BlockPos(8, 1, 7), dev.minetorio.core.logistics.LogisticMode.PASSIVE_PROVIDER,
                new ItemStack(ModItems.TRANSPORT_BELT.get(), 1));
        var storage = logisticChest(h, new BlockPos(11, 1, 7), dev.minetorio.core.logistics.LogisticMode.STORAGE);
        for (int x = 2; x <= 4; x++) BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(x, 1, 12), Direction.EAST);
        Planners.deconstruct(h.getLevel(), markArea(h), false, DeconstructionFilter.MACHINES, p -> true);
        h.succeedWhen(() -> {
            for (int x = 2; x <= 4; x++) h.assertTrue(h.getBlockState(new BlockPos(x, 1, 12)).isAir(), "belt " + x + " still there; " + debug(h, new BlockPos(x, 1, 12), port));
            h.assertTrue(storage.counts().getOrDefault("minetorio:transport_belt", 0) == 3, "belts in storage " + storage.counts());
            h.assertTrue(provider.counts().getOrDefault("minetorio:transport_belt", 0) == 1, "belts in provider " + provider.counts());
            h.assertTrue(robotsFlying(h) == 0 && port.availableRobots() == 2, "robots not home");
            port.robots().clearContent();
            provider.container().clearContent();
            storage.container().clearContent();
        });
    }

    /** Construction robots take from provider chests, never from requester chests. */
    @GameTest(template = "empty16", batch = "planner_robots_logistic_supply", timeoutTicks = 1600)
    public static void robotsBuildFromProvidersNotRequesters(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 2);
        var requester = logisticChest(h, new BlockPos(8, 1, 7), dev.minetorio.core.logistics.LogisticMode.REQUESTER,
                new ItemStack(ModItems.TRANSPORT_BELT.get(), 5));
        var provider = logisticChest(h, new BlockPos(13, 1, 7), dev.minetorio.core.logistics.LogisticMode.PASSIVE_PROVIDER,
                new ItemStack(ModItems.TRANSPORT_BELT.get(), 5));
        BlockPos at = new BlockPos(3, 1, 12);
        h.setBlock(at.below(), Blocks.STONE);
        h.assertTrue(Ghosts.place(h.getLevel(), h.absolutePos(at), ModItems.TRANSPORT_BELT.get(), Direction.EAST), "no ghost");
        h.assertTrue(Ghosts.canBuild(h.getLevel(), h.absolutePos(at)), "belt ghost lacks a buildable foundation");
        h.succeedWhen(() -> {
            h.assertTrue(h.getBlockState(at).is(ModBlocks.TRANSPORT_BELT.get()), "belt not built; " + debug(h, at, port));
            h.assertTrue(provider.counts().getOrDefault("minetorio:transport_belt", 0) == 4, "provider " + provider.counts());
            h.assertTrue(requester.counts().getOrDefault("minetorio:transport_belt", 0) == 5, "requester " + requester.counts());
            h.assertTrue(robotsFlying(h) == 0 && port.availableRobots() == 2, "robots not home");
            port.robots().clearContent();
            provider.container().clearContent();
            requester.container().clearContent();
        });
    }

    /** Yellow belts upgraded to red by robots: red taken from the chest, yellow brought back; facing kept. */
    @GameTest(template = "empty16", batch = "planner_robots_upgrade_belts", timeoutTicks = 1600)
    public static void robotsUpgradeBelts(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 4);
        ChestBlockEntity c = chest(h, new ItemStack(ModItems.FAST_TRANSPORT_BELT.get(), 5), new ItemStack(ModItems.TRANSPORT_BELT.get(), 1));
        for (int x = 2; x <= 4; x++) BeltGameTest.place(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(x, 1, 12), Direction.SOUTH);
        UpgradeRules rules = new UpgradeRules(List.of(new UpgradeRules.Rule("minetorio:transport_belt", "minetorio:fast_transport_belt")));
        var r = Planners.upgrade(h.getLevel(), markArea(h), false, rules, p -> true);
        h.assertTrue(r.changed() == 3, "marked " + r.changed());
        h.succeedWhen(() -> {
            for (int x = 2; x <= 4; x++) {
                BlockPos p = new BlockPos(x, 1, 12);
                h.assertTrue(h.getBlockState(p).is(ModBlocks.FAST_TRANSPORT_BELT.get()), "not upgraded " + p + "; " + debug(h, p, port));
                h.assertTrue(h.getBlockState(p).getValue(BeltBlock.FACING) == Direction.SOUTH, "facing changed");
            }
            h.assertTrue(count(c, ModItems.FAST_TRANSPORT_BELT.get()) == 2, "red left " + count(c, ModItems.FAST_TRANSPORT_BELT.get()));
            h.assertTrue(count(c, ModItems.TRANSPORT_BELT.get()) == 4, "yellow back " + count(c, ModItems.TRANSPORT_BELT.get()) + "; " + debug(h, new BlockPos(2, 1, 12), port));
            h.assertTrue(robotsFlying(h) == 0 && port.availableRobots() == 4, "robots not home; " + debug(h, new BlockPos(2, 1, 12), port));
            retire(port, c);
        });
    }

    /** Assembler 1 → 2 by robots keeps its recipe, facing and contents. */
    @GameTest(template = "empty16", batch = "planner_robots_upgrade_assembler", timeoutTicks = 1600)
    public static void robotsUpgradeAssemblerKeepingItsRecipe(GameTestHelper h) {
        RoboportBlockEntity port = roboport(h, 2);
        ChestBlockEntity c = chest(h, new ItemStack(ModItems.ASSEMBLING_MACHINE_2.get(), 1), new ItemStack(ModItems.ASSEMBLING_MACHINE_1.get(), 1));
        BlockPos asm = new BlockPos(11, 1, 12);
        AssemblerBlockEntity be = MachineGameTest.assembler(h, ModItems.ASSEMBLING_MACHINE_1.get(), asm, "iron_gear_wheel");
        be.machine().insertInput("minetorio:iron_plate", 3, false);
        Direction facing = h.getBlockState(asm).getValue(MultiblockControllerBlock.FACING);
        UpgradeRules rules = new UpgradeRules(List.of(new UpgradeRules.Rule("minetorio:assembling_machine_1", "minetorio:assembling_machine_2")));
        Planners.upgrade(h.getLevel(), markArea(h), false, rules, p -> true);
        h.succeedWhen(() -> {
            h.assertBlockPresent(ModBlocks.ASSEMBLING_MACHINE_2.get(), asm);
            h.assertTrue(h.getBlockState(asm).getValue(MultiblockControllerBlock.FACING) == facing, "facing changed");
            AssemblerBlockEntity now = (AssemblerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(asm));
            h.assertTrue(now.machine().recipe() != null
                    && now.machine().recipe().equals(Phase1Data.recipes().get("iron_gear_wheel").orElseThrow()), "recipe lost");
            h.assertTrue(now.machine().inputs().getOrDefault("minetorio:iron_plate", 0) == 3, "contents lost " + now.machine().inputs());
            for (BlockPos p : ((MultiblockControllerBlock) h.getBlockState(asm).getBlock()).footprint(h.absolutePos(asm), h.getBlockState(asm))) {
                if (p.equals(h.absolutePos(asm))) continue;
                BlockPos ctl = MultiblockPartBlock.controllerPos(h.getLevel(), p);
                h.assertTrue(h.absolutePos(asm).equals(ctl), "part at " + p + " linked to " + ctl + " not " + h.absolutePos(asm));
            }
            h.assertTrue(count(c, ModItems.ASSEMBLING_MACHINE_1.get()) == 2 && count(c, ModItems.ASSEMBLING_MACHINE_2.get()) == 0,
                    "old machine not returned");
            h.assertTrue(BeltGameTest.dropped(h, ModItems.IRON_PLATE.get()) == 0, "contents dropped");
            h.assertTrue(robotsFlying(h) == 0 && port.availableRobots() == 2, "robots not home");
            retire(port, c);
        });
    }

    /** The shortcut puts a planner into the hand (moving one the player has, not making a second). */
    @GameTest(template = "empty16")
    public static void shortcutGivesOnePlanner(GameTestHelper h) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_PLATE.get(), 3));
        Planners.give(p, Planners.Kind.DECONSTRUCTION);
        h.assertTrue(p.getMainHandItem().is(ModItems.DECONSTRUCTION_PLANNER.get()), "no planner in hand: " + p.getMainHandItem());
        p.getMainHandItem().set(ModDataComponents.DECONSTRUCTION_FILTER.get(), DeconstructionFilter.NATURE_ONLY.ordinal());
        p.getInventory().selected = 5;
        Planners.give(p, Planners.Kind.DECONSTRUCTION);
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.DECONSTRUCTION_PLANNER.get())) n++;
        h.assertTrue(n == 1, "planners: " + n);
        h.assertTrue(p.getMainHandItem().is(ModItems.DECONSTRUCTION_PLANNER.get()), "planner not moved into the hand");
        h.assertTrue(p.getMainHandItem().get(ModDataComponents.DECONSTRUCTION_FILTER.get()) == DeconstructionFilter.NATURE_ONLY.ordinal(),
                "settings lost");
        p.getInventory().clearContent();
        h.succeed();
    }
}
