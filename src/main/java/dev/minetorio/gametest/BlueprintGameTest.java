package dev.minetorio.gametest;

import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.blueprint.BlueprintCapture;
import dev.minetorio.content.blueprint.BlueprintData;
import dev.minetorio.content.blueprint.BlueprintItem;
import dev.minetorio.content.blueprint.BlueprintPaste;
import dev.minetorio.content.drag.DragPlacement;
import dev.minetorio.content.inserter.InserterBlock;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.power.ElectricPoleBlockEntity;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.content.robot.ConstructionRobotEntity;
import dev.minetorio.content.robot.DeconstructionMarking;
import dev.minetorio.content.robot.GhostBlockEntity;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.content.robot.RoboportBlockEntity;
import dev.minetorio.core.blueprint.BlueprintTransform;
import dev.minetorio.core.drag.DragDir;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Blueprints: copying a small build (a belt line with a curve and a ramp, a filter inserter with a
 * filter, an assembler with a recipe, two wired poles), pasting it turned elsewhere as ghosts that
 * carry the settings, building them (by hand and by robots), red parts refused, Shift paste through
 * vanilla obstacles, research (locked machines skipped, locked recipes dropped) and the ghost reach.
 * Research is global, so each batch sets it and puts it back to the start state.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class BlueprintGameTest {
    private static final String BATCH = "blueprint";
    private static final String LOCKED_BATCH = "blueprint_locked";
    private static final String ROBOT_BATCH = "blueprint_robots";

    static void research(GameTestHelper h, String... techs) {
        ResearchSavedData.get(h.getLevel().getServer()).restore(Set.of(techs), null, Map.of());
    }

    static void resetResearch(GameTestHelper h) {
        ResearchSavedData.get(h.getLevel().getServer()).restore(Set.of(), null, Map.of());
    }

    /** A survival fake player at {@code rel} holding an empty blueprint. */
    static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.empty());
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
        p.moveTo(at.x, at.y, at.z, Direction.NORTH.toYRot(), 0);
        HandGhosts.resetBudget(p);
        BlueprintPaste.resetBudget(p);
        return p;
    }

    static void floor(GameTestHelper h) {
        for (int x = 0; x <= 15; x++) for (int z = 0; z <= 15; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    /** Places a block as a player would (through a ghost and its build), {@code facing} the look. */
    static void build(GameTestHelper h, Item item, BlockPos rel, Direction facing) {
        BlockPos abs = h.absolutePos(rel);
        h.assertTrue(Ghosts.place(h.getLevel(), abs, item, facing), "no ghost for " + item + " at " + rel);
        h.assertTrue(Ghosts.build(h.getLevel(), abs, null, new ItemStack(item)), "not built: " + item + " at " + rel);
    }

    static GhostBlockEntity ghostAt(GameTestHelper h, BlockPos rel) {
        h.assertTrue(h.getBlockState(rel).is(ModBlocks.GHOST.get()), "no ghost at " + rel + ": " + h.getBlockState(rel));
        return (GhostBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    static BlueprintPaste.Result paste(GameTestHelper h, ServerPlayer p, BlockPos anchorRel, BlueprintTransform t, boolean force) {
        BlueprintPaste.resetBudget(p);
        return BlueprintPaste.paste(p, h.absolutePos(anchorRel), t.bits(), force);
    }

    /** Removes the test's ghosts (robot tests' areas reach far) and succeeds. */
    static void done(GameTestHelper h) {
        for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 15, 8, 15)) {
            if (h.getBlockState(rel).is(ModBlocks.GHOST.get())) h.setBlock(rel, Blocks.AIR);
        }
        resetResearch(h);
        h.succeed();
    }

    /**
     * The source build (rel): belts (1,1,1) E, (2,1,1) E, (3,1,1) S (a curve), (3,1,2) S (a ramp up
     * to) (3,2,3) S on a stone; a filter inserter (4,1,2) E with an iron plate filter feeding an
     * assembler (origin 5,1,1, facing E: x 5..7, z 1..3) set to iron gear wheels; poles (1,1,3) and (1,1,6).
     */
    static void sourceBuild(GameTestHelper h) {
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(1, 1, 1), Direction.EAST);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(2, 1, 1), Direction.EAST);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(3, 1, 1), Direction.SOUTH);
        h.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(3, 2, 3), Direction.SOUTH);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(3, 1, 2), Direction.SOUTH);
        build(h, ModItems.FILTER_INSERTER.get(), new BlockPos(4, 1, 2), Direction.EAST);
        build(h, ModItems.ASSEMBLING_MACHINE_1.get(), new BlockPos(5, 1, 1), Direction.EAST);
        build(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(1, 1, 3), Direction.NORTH);
        build(h, ModItems.SMALL_ELECTRIC_POLE.get(), new BlockPos(1, 1, 6), Direction.NORTH);
        h.assertTrue(h.getBlockState(new BlockPos(3, 1, 1)).getValue(BeltBlock.SHAPE) != BeltBlock.Shape.STRAIGHT, "setup: no curve");
        h.assertTrue(h.getBlockState(new BlockPos(3, 1, 2)).getValue(BeltBlock.SLOPE) == BeltBlock.Slope.ASCENDING, "setup: no ramp");
        InserterBlockEntity ins = (InserterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 1, 2)));
        ins.setFilter(0, "minetorio:iron_plate");
        AssemblerBlockEntity asm = (AssemblerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5, 1, 1)));
        CompoundTag recipe = new CompoundTag();
        recipe.putString(AssemblerBlockEntity.SETTINGS_RECIPE, "iron_gear_wheel");
        asm.loadSettings(recipe);
        h.assertTrue(asm.machine().recipe() != null, "setup: no recipe");
    }

    static BlueprintData capture(GameTestHelper h, ServerPlayer p, BlockPos a, BlockPos b) {
        BlueprintCapture.Result r = BlueprintCapture.capture(p, h.absolutePos(a), h.absolutePos(b), false);
        h.assertTrue(r == BlueprintCapture.Result.OK, "capture " + r);
        BlueprintData data = BlueprintItem.data(p.getMainHandItem());
        h.assertTrue(data != null, "blueprint still empty");
        return data;
    }

    /**
     * Copy the source build, paste it turned a quarter clockwise at x 9..14 (where the ramp's upper
     * belt gets its stone), build every ghost by hand: same shapes, the filter and recipe applied,
     * the poles wired.
     */
    @GameTest(template = "empty16", batch = BATCH, timeoutTicks = 200)
    public static void copyAndPasteTurned(GameTestHelper h) {
        research(h, "automation", "fast_inserter");
        floor(h);
        sourceBuild(h);
        ServerPlayer p = player(h, new BlockPos(8, 1, 8));
        BlueprintData data = capture(h, p, new BlockPos(1, 1, 1), new BlockPos(7, 2, 6));
        h.assertTrue(data.entries().size() == 9, "copied " + data.entries().size() + " parts: " + data.counts());
        long withSettings = data.entries().stream().filter(e -> e.settings().isPresent()).count();
        h.assertTrue(withSettings == 2, "parts with settings: " + withSettings);
        // the box is 7 x 6, turned 6 x 7; base (9,1,1) means the cursor on (12,1,4)
        h.setBlock(new BlockPos(12, 1, 3), Blocks.STONE); // under the ramp's upper belt
        BlueprintPaste.Result r = paste(h, p, new BlockPos(12, 1, 4), BlueprintTransform.IDENTITY.rotated(), false);
        h.assertTrue(r.accepted() && r.placed() == 9 && r.blocked() == 0, "paste " + r);
        // belts: A (14,1,1) S, B (14,1,2) S, C (14,1,3) W, D (13,1,3) W, E (12,2,3) W
        h.assertTrue(ghostAt(h, new BlockPos(14, 1, 1)).target().getValue(BeltBlock.FACING) == Direction.SOUTH, "A facing");
        h.assertTrue(ghostAt(h, new BlockPos(14, 1, 3)).target().getValue(BeltBlock.FACING) == Direction.WEST, "C facing");
        h.assertTrue(ghostAt(h, new BlockPos(12, 2, 3)).target().getValue(BeltBlock.FACING) == Direction.WEST, "E facing");
        GhostBlockEntity ins = ghostAt(h, new BlockPos(13, 1, 4));
        h.assertTrue(ins.target().getValue(InserterBlock.FACING) == Direction.SOUTH && ins.settings() != null, "inserter ghost " + ins.target());
        GhostBlockEntity asm = ghostAt(h, new BlockPos(14, 1, 5));
        h.assertTrue(asm.target().getValue(MultiblockControllerBlock.FACING) == Direction.SOUTH, "assembler facing " + asm.target());
        h.assertTrue(asm.settings() != null && "iron_gear_wheel".equals(asm.settings().getString(AssemblerBlockEntity.SETTINGS_RECIPE)),
                "assembler settings " + asm.settings());
        ghostAt(h, new BlockPos(12, 1, 1));
        ghostAt(h, new BlockPos(9, 1, 1));

        // build them all by hand
        List<BlockPos> ghosts = List.of(new BlockPos(14, 1, 1), new BlockPos(14, 1, 2), new BlockPos(14, 1, 3), new BlockPos(13, 1, 3),
                new BlockPos(12, 2, 3), new BlockPos(13, 1, 4), new BlockPos(14, 1, 5), new BlockPos(12, 1, 1), new BlockPos(9, 1, 1));
        for (BlockPos g : ghosts) {
            Item item = ghostAt(h, g).item();
            h.assertTrue(Ghosts.build(h.getLevel(), h.absolutePos(g), null, new ItemStack(item)), "not built at " + g);
        }
        h.assertTrue(h.getBlockState(new BlockPos(14, 1, 3)).getValue(BeltBlock.SHAPE) != BeltBlock.Shape.STRAIGHT, "no curve after paste");
        h.assertTrue(h.getBlockState(new BlockPos(13, 1, 3)).getValue(BeltBlock.SLOPE) == BeltBlock.Slope.ASCENDING, "no ramp after paste");
        InserterBlockEntity built = (InserterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(13, 1, 4)));
        h.assertTrue("minetorio:iron_plate".equals(built.filter().get(0)), "filter not applied: " + built.filter().get(0));
        AssemblerBlockEntity machine = (AssemblerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(14, 1, 5)));
        h.assertTrue(machine.machine().recipe() != null && machine.machine().recipe().id().equals("iron_gear_wheel"), "recipe not applied");
        h.runAfterDelay(5, () -> {
            ElectricPoleBlockEntity pole = (ElectricPoleBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(12, 1, 1)));
            h.assertTrue(pole.connections().contains(h.absolutePos(new BlockPos(9, 1, 1))), "pasted poles not wired: " + pole.connections());
            done(h);
        });
    }

    /**
     * Pipes keep their closed sides through a blueprint, turned with it; underground pipes keep their
     * six-way facing (a horizontal one turns, an upward one stays up). Source: a pipe (2,1,2) with its
     * east side closed, underground pipes (3,1,2) facing east and (2,2,2)... (2,1,4) facing up.
     */
    @GameTest(template = "empty16", batch = BATCH, timeoutTicks = 200)
    public static void pipeSidesAndUndergroundFacingsCopied(GameTestHelper h) {
        research(h);
        floor(h);
        var level = h.getLevel();
        BlockPos pipe = new BlockPos(2, 1, 2);
        h.setBlock(pipe, ModBlocks.PIPE.get());
        h.setBlock(new BlockPos(3, 1, 2), ModBlocks.PIPE_TO_GROUND.get().defaultBlockState()
                .setValue(dev.minetorio.content.fluid.UndergroundPipeBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(2, 1, 4), ModBlocks.PIPE_TO_GROUND.get().defaultBlockState()
                .setValue(dev.minetorio.content.fluid.UndergroundPipeBlock.FACING, Direction.UP));
        int eastMask = 1 << Direction.EAST.ordinal();
        level.setBlock(h.absolutePos(pipe), dev.minetorio.content.fluid.PipeBlock.withClosedMask(
                h.getBlockState(pipe), eastMask, level, h.absolutePos(pipe)), net.minecraft.world.level.block.Block.UPDATE_ALL);
        h.assertTrue(dev.minetorio.content.fluid.PipeBlock.closedMask(h.getBlockState(pipe)) == eastMask, "setup: side not closed");
        ServerPlayer p = player(h, new BlockPos(8, 1, 8));
        BlueprintData data = capture(h, p, new BlockPos(2, 1, 2), new BlockPos(3, 1, 4));
        h.assertTrue(data.entries().size() == 3, "copied " + data.entries().size() + " parts: " + data.counts());
        // the box (x 2..3, z 2..4) is 2 x 3, turned 3 x 2: base (9,1,9), cursor on its anchor cell
        int[] off = dev.minetorio.core.blueprint.Blueprints.anchorOffset(new int[] {3, 1, 2});
        BlockPos base = new BlockPos(9, 1, 9);
        BlueprintPaste.Result r = paste(h, p, base.offset(-off[0], 0, -off[1]), BlueprintTransform.IDENTITY.rotated(), false);
        h.assertTrue(r.accepted() && r.placed() == 3 && r.blocked() == 0, "paste " + r);
        // turned a quarter clockwise: (x, z) -> (-z, x), normalized; pipe (0,0) -> (2,0), east pipe (1,0) -> (2,1), up (0,2) -> (0,0)
        BlockPos newPipe = base.offset(2, 0, 0), newEast = base.offset(2, 0, 1), newUp = base;
        h.assertTrue(ghostAt(h, newEast).target().getValue(dev.minetorio.content.fluid.UndergroundPipeBlock.FACING) == Direction.SOUTH,
                "turned underground pipe " + ghostAt(h, newEast).target());
        h.assertTrue(ghostAt(h, newUp).target().getValue(dev.minetorio.content.fluid.UndergroundPipeBlock.FACING) == Direction.UP,
                "upward underground pipe " + ghostAt(h, newUp).target());
        for (BlockPos g : List.of(newPipe, newEast, newUp)) {
            Item item = ghostAt(h, g).item();
            h.assertTrue(Ghosts.build(h.getLevel(), h.absolutePos(g), null, new ItemStack(item)), "not built at " + g);
        }
        int mask = dev.minetorio.content.fluid.PipeBlock.closedMask(h.getBlockState(newPipe));
        h.assertTrue(mask == 1 << Direction.SOUTH.ordinal(), "pasted pipe's closed sides " + mask + ": " + h.getBlockState(newPipe));
        done(h);
    }

    /**
     * Pasting onto itself skips every part (same thing); a stone in a part's cell turns it red
     * (refused, the rest placed); over a drop the unsupported part is refused; Shift pastes through
     * the stone by marking it, and its ghost comes once the stone is broken.
     */
    @GameTest(template = "empty16", batch = BATCH, timeoutTicks = 200)
    public static void sameSkippedRedRefusedShiftMarks(GameTestHelper h) {
        research(h);
        floor(h);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(2, 1, 2), Direction.EAST);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(3, 1, 2), Direction.EAST);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(4, 1, 2), Direction.EAST);
        ServerPlayer p = player(h, new BlockPos(7, 1, 7));
        capture(h, p, new BlockPos(2, 1, 2), new BlockPos(4, 1, 2));
        // the cursor on the middle belt: all three are there already
        BlueprintPaste.Result same = paste(h, p, new BlockPos(3, 1, 2), BlueprintTransform.IDENTITY, false);
        h.assertTrue(same.accepted() && same.same() == 3 && same.placed() == 0, "self paste " + same);

        // elsewhere, a dirt block in the middle cell: red, the other two placed
        h.setBlock(new BlockPos(3, 1, 6), Blocks.DIRT);
        BlueprintPaste.Result red = paste(h, p, new BlockPos(3, 1, 6), BlueprintTransform.IDENTITY, false);
        h.assertTrue(red.placed() == 2 && red.blocked() == 1, "paste with an obstacle " + red);
        h.assertTrue(h.getBlockState(new BlockPos(3, 1, 6)).is(Blocks.DIRT), "obstacle replaced");
        ghostAt(h, new BlockPos(2, 1, 6));
        ghostAt(h, new BlockPos(4, 1, 6));

        // over a drop: the parts without ground are refused
        for (int x = 2; x <= 4; x++) h.setBlock(new BlockPos(x, 0, 10), Blocks.AIR);
        h.setBlock(new BlockPos(2, 0, 10), Blocks.STONE);
        BlueprintPaste.Result drop = paste(h, p, new BlockPos(3, 1, 10), BlueprintTransform.IDENTITY, false);
        h.assertTrue(drop.placed() == 1 && drop.blocked() == 2, "paste over a drop " + drop);

        // Shift: the dirt is marked, its ghost waits; breaking the dirt brings the ghost
        BlueprintPaste.Result forced = paste(h, p, new BlockPos(3, 1, 6), BlueprintTransform.IDENTITY, true);
        h.assertTrue(forced.placed() == 1 && forced.same() == 2 && forced.marked() == 1, "shift paste " + forced);
        BlockPos dirt = h.absolutePos(new BlockPos(3, 1, 6));
        h.assertTrue(DeconstructionMarking.isMarked(h.getLevel(), dirt), "dirt not marked");
        h.assertTrue(DeconstructionMarking.hasPending(h.getLevel(), dirt), "no waiting ghost");
        h.getLevel().destroyBlock(dirt, false);
        h.succeedWhen(() -> {
            GhostBlockEntity g = ghostAt(h, new BlockPos(3, 1, 6));
            h.assertTrue(g.target().getValue(BeltBlock.FACING) == Direction.EAST, "waiting ghost facing");
            h.assertFalse(DeconstructionMarking.isMarked(h.getLevel(), dirt), "mark kept after the block went");
            for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 15, 8, 15)) {
                if (h.getBlockState(rel).is(ModBlocks.GHOST.get())) h.setBlock(rel, Blocks.AIR);
            }
            resetResearch(h);
        });
    }

    /** A one-belt blueprint (east) in {@code p}'s hand. */
    static void beltBlueprint(ServerPlayer p) {
        ItemStack bp = BlueprintItem.empty();
        bp.set(ModDataComponents.BLUEPRINT.get(), new BlueprintData(List.of(
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 0, DragDir.EAST.id(), 0, Optional.empty()))));
        p.setItemInHand(InteractionHand.MAIN_HAND, bp);
    }

    /**
     * Ghost reach (the top-down camera's out-of-reach placement): 40 blocks away a ghost click, a
     * ghost drag and a blueprint paste go through; 100 blocks away they are refused; a real
     * placement 40 blocks away (vanilla reach, a real drag) is still refused.
     */
    @GameTest(template = "empty16", batch = BATCH)
    public static void ghostReachIsFarButBounded(GameTestHelper h) {
        floor(h);
        ServerPlayer p = player(h, new BlockPos(5, 1, -35));
        BlockPos target = h.absolutePos(new BlockPos(5, 0, 5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TRANSPORT_BELT.get()));
        h.assertTrue(RemoteView.canReachGhost(p, target), "40 blocks out of ghost reach (view distance "
                + h.getLevel().getServer().getPlayerList().getViewDistance() + ")");
        h.assertTrue(HandGhosts.place(p, new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5, 0), Direction.UP, target, false)),
                "far ghost refused");
        ghostAt(h, new BlockPos(5, 1, 5));
        // a real placement that far is refused (vanilla's reach), a real drag too; a ghost drag goes
        h.assertFalse(p.canInteractWithBlock(target, 1.0), "real placement in reach at 40 blocks");
        DragPlacement.start(p, h.absolutePos(new BlockPos(3, 1, 8)), DragDir.EAST, false);
        h.assertFalse(DragPlacement.isDragging(p), "real drag 40 blocks away");
        DragPlacement.start(p, h.absolutePos(new BlockPos(3, 1, 8)), DragDir.EAST, true);
        h.assertTrue(DragPlacement.isDragging(p), "ghost drag 40 blocks away refused");
        DragPlacement.steps(p, 0, new byte[] {(byte) DragDir.EAST.id(), (byte) DragDir.EAST.id()}, true);
        ghostAt(h, new BlockPos(4, 1, 8));
        // a blueprint paste 40 blocks away
        beltBlueprint(p);
        BlueprintPaste.Result ok = paste(h, p, new BlockPos(8, 1, 12), BlueprintTransform.IDENTITY, false);
        h.assertTrue(ok.accepted() && ok.placed() == 1, "far paste " + ok);
        ghostAt(h, new BlockPos(8, 1, 12));
        // ghost removal reaches as far as placement
        h.assertTrue(RemoteView.removeGhost(p, h.absolutePos(new BlockPos(8, 1, 12))) == RemoteView.Result.OK, "far ghost not removed");
        // 100 blocks away: all refused (the same fake player, moved)
        ServerPlayer far = player(h, new BlockPos(5, 1, -95));
        far.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TRANSPORT_BELT.get()));
        BlockPos t2 = h.absolutePos(new BlockPos(10, 0, 5));
        h.assertFalse(HandGhosts.place(far, new BlockHitResult(Vec3.atCenterOf(t2).add(0, 0.5, 0), Direction.UP, t2, false)),
                "ghost 100 blocks away");
        beltBlueprint(far);
        h.assertFalse(paste(h, far, new BlockPos(10, 1, 12), BlueprintTransform.IDENTITY, false).accepted(), "paste 100 blocks away");
        h.assertFalse(h.getBlockState(new BlockPos(10, 1, 12)).is(ModBlocks.GHOST.get()), "ghost from 100 blocks");
        h.assertTrue(RemoteView.removeGhost(far,h.absolutePos(new BlockPos(5, 1, 5))) == RemoteView.Result.OUT_OF_AREA, "removed from 100");
        done(h);
    }

    /**
     * Without the fast inserter research: the filter inserter part is red and skipped, the assembler
     * comes without its (locked) fast inserter recipe; the belt is placed.
     */
    @GameTest(template = "empty16", batch = LOCKED_BATCH)
    public static void lockedPartsSkippedLockedRecipesDropped(GameTestHelper h) {
        research(h, "automation");
        floor(h);
        CompoundTag recipe = new CompoundTag();
        recipe.putString(AssemblerBlockEntity.SETTINGS_RECIPE, "fast_inserter");
        ServerPlayer p = player(h, new BlockPos(7, 1, 12));
        ItemStack bp = BlueprintItem.empty();
        bp.set(ModDataComponents.BLUEPRINT.get(), new BlueprintData(List.of(
                new BlueprintData.Entry("minetorio:transport_belt", 0, 0, 1, DragDir.EAST.id(), 0, Optional.empty()),
                new BlueprintData.Entry("minetorio:filter_inserter", 1, 0, 1, DragDir.EAST.id(), 0, Optional.empty()),
                // assembler facing east: x 2..4, z 0..2
                new BlueprintData.Entry("minetorio:assembling_machine_1", 2, 0, 0, DragDir.EAST.id(), 0, Optional.of(recipe)))));
        p.setItemInHand(InteractionHand.MAIN_HAND, bp);
        // size 5 x 3: base (3,1,4) with the cursor on (5,1,5)
        BlueprintPaste.Result r = paste(h, p, new BlockPos(5, 1, 5), BlueprintTransform.IDENTITY, false);
        h.assertTrue(r.placed() == 2 && r.locked() == 1, "paste " + r);
        ghostAt(h, new BlockPos(3, 1, 5));
        h.assertFalse(h.getBlockState(new BlockPos(4, 1, 5)).is(ModBlocks.GHOST.get()), "locked inserter placed");
        GhostBlockEntity asm = ghostAt(h, new BlockPos(5, 1, 4));
        h.assertTrue(asm.settings() == null || !asm.settings().contains(AssemblerBlockEntity.SETTINGS_RECIPE), "locked recipe kept: " + asm.settings());
        done(h);
    }

    // --- robots ---------------------------------------------------------------------------------

    /** Roboport controller (facing SOUTH: footprint x 2..5, z 2..5), as in the robot tests. */
    private static final BlockPos PORT = new BlockPos(5, 1, 2);
    private static final BlockPos POLE = new BlockPos(6, 1, 3);
    private static final BlockPos CHEST = new BlockPos(8, 1, 7);

    /**
     * A copied belt, filter inserter (with its filter) and assembler (with its recipe) pasted as
     * ghosts in a roboport's area: robots build them from a chest and the settings are applied.
     */
    @GameTest(template = "empty16", batch = ROBOT_BATCH, timeoutTicks = 2400)
    public static void robotsBuildPastedSettings(GameTestHelper h) {
        research(h, "automation", "fast_inserter");
        floor(h);
        build(h, ModItems.TRANSPORT_BELT.get(), new BlockPos(8, 1, 2), Direction.EAST);
        build(h, ModItems.FILTER_INSERTER.get(), new BlockPos(9, 1, 2), Direction.EAST);
        build(h, ModItems.ASSEMBLING_MACHINE_1.get(), new BlockPos(10, 1, 1), Direction.EAST);
        ((InserterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(9, 1, 2)))).setFilter(0, "minetorio:copper_plate");
        CompoundTag recipe = new CompoundTag();
        recipe.putString(AssemblerBlockEntity.SETTINGS_RECIPE, "copper_cable");
        ((AssemblerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10, 1, 1)))).loadSettings(recipe);
        ServerPlayer p = player(h, new BlockPos(7, 1, 9));
        capture(h, p, new BlockPos(8, 1, 1), new BlockPos(12, 1, 3));
        // 5 x 3: base (8,1,10), cursor (10,1,11)
        BlueprintPaste.Result r = paste(h, p, new BlockPos(10, 1, 11), BlueprintTransform.IDENTITY, false);
        h.assertTrue(r.placed() == 3, "paste " + r);

        BlockPos portAbs = h.absolutePos(PORT);
        h.assertTrue(Ghosts.place(h.getLevel(), portAbs, ModItems.ROBOPORT.get(), Direction.SOUTH), "roboport ghost");
        h.assertTrue(Ghosts.build(h.getLevel(), portAbs, null, new ItemStack(ModItems.ROBOPORT.get())), "roboport");
        ((RoboportBlockEntity) h.getLevel().getBlockEntity(portAbs)).robots().setItem(0, new ItemStack(ModItems.CONSTRUCTION_ROBOT.get(), 4));
        TestPower.powerAt(h, POLE);
        h.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CHEST));
        chest.setItem(0, new ItemStack(ModItems.TRANSPORT_BELT.get(), 1));
        chest.setItem(1, new ItemStack(ModItems.FILTER_INSERTER.get(), 1));
        chest.setItem(2, new ItemStack(ModItems.ASSEMBLING_MACHINE_1.get(), 1));

        h.succeedWhen(() -> {
            h.assertTrue(h.getBlockState(new BlockPos(8, 1, 11)).is(ModBlocks.TRANSPORT_BELT.get()), "belt not built");
            h.assertTrue(h.getBlockState(new BlockPos(9, 1, 11)).is(ModBlocks.FILTER_INSERTER.get()), "inserter not built");
            h.assertTrue(h.getBlockState(new BlockPos(10, 1, 10)).is(ModBlocks.ASSEMBLING_MACHINE_1.get()), "assembler not built");
            InserterBlockEntity ins = (InserterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(9, 1, 11)));
            h.assertTrue("minetorio:copper_plate".equals(ins.filter().get(0)), "filter not applied by the robot");
            AssemblerBlockEntity asm = (AssemblerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10, 1, 10)));
            h.assertTrue(asm.machine().recipe() != null && asm.machine().recipe().id().equals("copper_cable"), "recipe not applied by the robot");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ConstructionRobotEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).isEmpty(),
                    "robots still out");
            resetResearch(h);
        });
    }
}
