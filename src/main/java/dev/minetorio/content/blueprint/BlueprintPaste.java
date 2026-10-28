package dev.minetorio.content.blueprint;

import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.content.robot.DeconstructionMarking;
import dev.minetorio.content.robot.GhostBlockEntity;
import dev.minetorio.content.robot.GhostReservations;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.content.robot.HandGhosts;
import dev.minetorio.core.blueprint.BlueprintPart;
import dev.minetorio.core.blueprint.BlueprintTransform;
import dev.minetorio.core.blueprint.Blueprints;
import dev.minetorio.core.blueprint.Blueprints.Below;
import dev.minetorio.core.drag.DragDir;
import dev.minetorio.core.robot.RateBudget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Pasting a filled blueprint as ghosts. {@link #plan} decides every part (both sides: the client
 * draws it as the preview, the server re-plans and commits it): the parts are transformed
 * ({@link BlueprintTransform}), centred on the cursor cell with their bottom layer at its level,
 * then taken bottom layer first. Each part is
 * <ul>
 *   <li>{@link Status#SAME} (skipped) when the same block stands there already (or its ghost);</li>
 *   <li>{@link Status#LOCKED} (red, skipped) when its machine is not researched;</li>
 *   <li>{@link Status#UPGRADE}: the same kind of block of another tier stands there facing the
 *       same way (a yellow belt where the blueprint has a red one, assembler 1 for 2): it is marked
 *       for upgrade ({@code ConstructionMarks#markUpgrade}) and robots fast-replace it;</li>
 *   <li>{@link Status#BLOCKED} (red, skipped) when its cells are taken (another block, a belt
 *       facing another way), hold fluid, overlap another part or ghost, lack support
 *       ({@link Blueprints#supported}) or are out of reach;</li>
 *   <li>{@link Status#OK}: planned exactly like a hand ghost ({@link HandGhosts#planAt});</li>
 *   <li>{@link Status#FORCED} (Shift paste): it fits once the vanilla blocks in its way
 *       ({@link DeconstructionMarking#markable}) are deconstructed; those are marked.</li>
 * </ul>
 * The server ({@link #paste}) takes the blueprint from the player's hand (never from the packet; a
 * book in hand pastes its active blueprint),
 * checks the anchor's ghost reach, build rights per cell, and a paste budget; the settings lose
 * recipes that are not unlocked ({@link BlueprintRules#sanitize}). A ghost blocked only by a marked
 * block in its own cell waits for it ({@link DeconstructionMarking#addPending}).
 */
public final class BlueprintPaste {
    /** Pastes: a burst, refilled per tick. */
    static final int BURST = 4;
    static final double REFILL_PER_TICK = 0.1;

    private static final Map<UUID, RateBudget> BUDGETS = new HashMap<>();

    private BlueprintPaste() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BUDGETS.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> BUDGETS.clear());
        BlueprintCapture.register();
        BlueprintImport.register();
    }

    public enum Status { OK, FORCED, SAME, BLOCKED, LOCKED, UPGRADE }

    /**
     * One part's fate: where it goes ({@code origin}, the controller cell; {@code cells} its
     * footprint), what it is, its ghost {@code state} (null when red or skipped), the vanilla
     * blocks in its way ({@code obstacles}, Shift paste) and its settings.
     */
    public record PartPlan(int index, Status status, BlockPos origin, Direction facing, Block block, List<BlockPos> cells,
                           @Nullable BlockState state, List<BlockPos> obstacles, @Nullable CompoundTag settings) {
        public boolean placeable() { return status == Status.OK || status == Status.FORCED || status == Status.UPGRADE; }
    }

    /** What the plan needs from its side: reach, research, and (server) a commit right after each part. */
    public interface Context {
        /** Whether a ghost may go into {@code pos} (reach, area, build rights). */
        boolean reachable(BlockPos pos);

        boolean recipeUnlocked(String recipeId);

        /** Hardness of the block at {@code pos} for {@link DeconstructionMarking#markable}. */
        default boolean markable(Level level, BlockPos pos) {
            BlockState s = level.getBlockState(pos);
            return DeconstructionMarking.markable(s, s.getDestroySpeed(level, pos))
                    && !"minetorio".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace());
        }

        /** Server: places the part's ghost now, so later parts see it; false if that failed (the part turns red). */
        default boolean commit(PartPlan plan) { return true; }
    }

    /** Where a blueprint of {@code size} goes with the cursor on cell {@code anchor}: world cell of blueprint (0, 0, 0). */
    public static BlockPos base(BlockPos anchor, int[] size) {
        int[] off = Blueprints.anchorOffset(size);
        return anchor.offset(off[0], 0, off[1]);
    }

    /** The parts of {@code data} transformed by {@code t} (normalized). */
    public static List<BlueprintPart> transformed(BlueprintData data, BlueprintTransform t) {
        return t.apply(data.parts());
    }

    /** Plans every part (in paste order) with the cursor on cell {@code anchor}; {@code force}: Shift paste. */
    public static List<PartPlan> plan(Level level, @Nullable Player player, BlueprintData data, BlockPos anchor, BlueprintTransform t,
                                      boolean force, Context ctx) {
        List<BlueprintPart> parts = transformed(data, t);
        BlockPos base = base(anchor, Blueprints.size(parts));
        Set<BlockPos> claimed = new HashSet<>();
        List<PartPlan> out = new ArrayList<>(parts.size());
        for (int i : Blueprints.pasteOrder(parts)) {
            BlueprintPart p = parts.get(i);
            CompoundTag settings = BlueprintRules.transformed(
                    BlueprintRules.sanitize(data.entries().get(i).settings().orElse(null), ctx::recipeUnlocked), t);
            PartPlan plan = planPart(level, player, i, p, base, force, claimed, settings, ctx);
            if (plan.placeable() && !ctx.commit(plan)) {
                plan = new PartPlan(i, Status.BLOCKED, plan.origin(), plan.facing(), plan.block(), plan.cells(), null, List.of(), settings);
            }
            if (plan.placeable()) claimed.addAll(plan.cells());
            out.add(plan);
        }
        return out;
    }

    private static PartPlan planPart(Level level, @Nullable Player player, int i, BlueprintPart p, BlockPos base, boolean force,
                                     Set<BlockPos> claimed, @Nullable CompoundTag settings, Context ctx) {
        BlockPos origin = base.offset(p.x(), p.y(), p.z());
        DragDir d = p.facing();
        Direction facing = d == null ? Direction.NORTH : Direction.valueOf(d.name());
        Block block = BlueprintData.block(p.block());
        List<BlockPos> cells = new ArrayList<>();
        for (int[] c : p.cells()) cells.add(base.offset(c[0], c[1], c[2]));
        Item item = block.asItem();
        ItemStack stack = new ItemStack(item);
        if (!BlueprintRules.capturable(block)) return red(i, Status.BLOCKED, origin, facing, block, cells, settings);
        if (!BlueprintRules.itemUnlocked(item, ctx::recipeUnlocked)) return red(i, Status.LOCKED, origin, facing, block, cells, settings);
        for (BlockPos c : cells) {
            if (claimed.contains(c) || !ctx.reachable(c) || level.isOutsideBuildHeight(c)) return red(i, Status.BLOCKED, origin, facing, block, cells, settings);
        }
        if (same(level, origin, block, facing, p.variant())) {
            return new PartPlan(i, Status.SAME, origin, facing, block, cells, null, List.of(), settings);
        }
        if (upgradable(level, origin, block, facing, p.variant())) {
            return new PartPlan(i, Status.UPGRADE, origin, facing, block, cells, null, List.of(), settings);
        }
        for (int[] c : p.bottom()) {
            BlockPos below = base.offset(c[0], c[1], c[2]).below();
            if (!Blueprints.supported(List.of(below(level, below, claimed)))) return red(i, Status.BLOCKED, origin, facing, block, cells, settings);
        }
        HandGhosts.Plan hand = HandGhosts.planAt(level, player, stack, MultiblockBlockItem.targetFor(item, origin, facing), facing);
        if (hand != null && hand.pos().equals(origin)) {
            return new PartPlan(i, Status.OK, origin, facing, block, cells, withVariant(hand.state(), facing, p.variant()), List.of(), settings);
        }
        if (!force) return red(i, Status.BLOCKED, origin, facing, block, cells, settings);
        // Shift: fits once the vanilla blocks in the way are gone?
        List<BlockPos> obstacles = new ArrayList<>();
        for (BlockPos c : cells) {
            BlockState s = level.getBlockState(c);
            if (!s.getFluidState().isEmpty() || !GhostReservations.freeFor(level, c, origin)
                    || (!c.equals(origin) && s.is(ModBlocks.GHOST.get()))) {
                return red(i, Status.BLOCKED, origin, facing, block, cells, settings);
            }
            if (s.canBeReplaced()) continue;
            if (!ctx.markable(level, c)) return red(i, Status.BLOCKED, origin, facing, block, cells, settings);
            obstacles.add(c.immutable());
        }
        if (obstacles.isEmpty()) return red(i, Status.BLOCKED, origin, facing, block, cells, settings); // refused for another reason
        BlockState target = withVariant(Ghosts.planned(level, origin, defaultState(block, facing)), facing, p.variant());
        return new PartPlan(i, Status.FORCED, origin, facing, block, cells, target, obstacles, settings);
    }

    private static PartPlan red(int i, Status s, BlockPos origin, Direction facing, Block block, List<BlockPos> cells, @Nullable CompoundTag settings) {
        return new PartPlan(i, s, origin, facing, block, cells, null, List.of(), settings);
    }

    private static Below below(Level level, BlockPos pos, Set<BlockPos> claimed) {
        if (claimed.contains(pos)) return Below.GHOST;
        BlockState s = level.getBlockState(pos);
        if (s.is(ModBlocks.GHOST.get())) return Below.GHOST;
        BlockPos owner = GhostReservations.owner(level, pos);
        if (owner != null && level.getBlockState(owner).is(ModBlocks.GHOST.get())) return Below.GHOST;
        return !s.canBeReplaced() && s.getFluidState().isEmpty() ? Below.SOLID : Below.EMPTY;
    }

    private static BlockState defaultState(Block block, Direction facing) {
        BlockState s = block.defaultBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : s;
    }

    /** Variant of a six-way block ({@link BlockStateProperties#FACING}, e.g. an underground pipe) facing up or down. */
    public static final int VARIANT_UP = 2, VARIANT_DOWN = 3;

    static int verticalVariant(Direction d) {
        return d == Direction.UP ? VARIANT_UP : VARIANT_DOWN;
    }

    /** The six-way facing a part of {@code variant} and horizontal {@code facing} gets. */
    static Direction sixWayFacing(Direction facing, int variant) {
        return variant == VARIANT_UP ? Direction.UP : variant == VARIANT_DOWN ? Direction.DOWN : facing;
    }

    /**
     * An underground belt's entrance/exit as captured (placement alone cannot tell before its partner
     * is built); a six-way block's (underground pipe's) facing as captured, not the placement guess.
     */
    private static BlockState withVariant(BlockState s, Direction facing, int variant) {
        if (s.getBlock() instanceof UndergroundBeltBlock) {
            return s.setValue(UndergroundBeltBlock.TYPE, variant == 1 ? UndergroundBeltBlock.Type.OUTPUT : UndergroundBeltBlock.Type.INPUT);
        }
        if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, sixWayFacing(facing, variant));
        return s;
    }

    /**
     * Whether a real block at {@code origin} can be fast-replaced by {@code block}: another tier of
     * the same fast-replace group ({@code UpgradeGroups}), facing the same way (an underground belt
     * also the same entrance/exit). Not a ghost.
     */
    static boolean upgradable(Level level, BlockPos origin, Block block, Direction facing, int variant) {
        BlockState s = level.getBlockState(origin);
        if (s.is(ModBlocks.GHOST.get()) || s.is(block)) return false;
        var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        if (!dev.minetorio.core.data.UpgradeGroups.compatible(items.getKey(s.getBlock().asItem()).toString(),
                items.getKey(block.asItem()).toString())) {
            return false;
        }
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && s.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing) return false;
        return !s.hasProperty(UndergroundBeltBlock.TYPE)
                || (s.getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT) == (variant == 1);
    }

    /** Whether the same thing already stands at {@code origin}: the block (or its ghost) facing the same way. */
    static boolean same(Level level, BlockPos origin, Block block, Direction facing, int variant) {
        BlockState s = level.getBlockState(origin);
        if (s.is(ModBlocks.GHOST.get()) && level.getBlockEntity(origin) instanceof GhostBlockEntity g && g.isValid()) {
            s = g.target();
        }
        if (!s.is(block)) return false;
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && s.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing) return false;
        if (s.hasProperty(UndergroundBeltBlock.TYPE) && (s.getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT) != (variant == 1)) {
            return false;
        }
        if (s.hasProperty(BlockStateProperties.FACING) && s.getValue(BlockStateProperties.FACING) != sixWayFacing(facing, variant)) return false;
        return !s.hasProperty(ElectricPoleBlock.PART) || s.getValue(ElectricPoleBlock.PART) == 0;
    }

    // --- server --------------------------------------------------------------------------------

    /** What a paste did (for tests and the message). */
    public record Result(boolean accepted, int placed, int same, int blocked, int locked, int marked) {
        static final Result REFUSED = new Result(false, 0, 0, 0, 0, 0);
    }

    /**
     * A paste from {@code player}'s client: the filled blueprint in the main hand, the cursor on
     * cell {@code anchor}, transform {@code bits} ({@link BlueprintTransform#bits}), {@code force}
     * (Shift). Every part is re-planned and placed here.
     */
    public static Result paste(ServerPlayer player, BlockPos anchor, int bits, boolean force) {
        ServerLevel level = player.serverLevel();
        BlueprintData data = BlueprintStacks.activeData(player.getMainHandItem());
        if (data == null || !data.valid() || player.isSpectator() || !player.mayBuild() || !player.isAlive()) return Result.REFUSED;
        if (!level.isLoaded(anchor) || !RemoteView.canReachGhost(player, anchor)) return Result.REFUSED;
        if (!BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(BURST, REFILL_PER_TICK)).take(player.server.getTickCount(), 1)) {
            return Result.REFUSED;
        }
        int[] marked = {0};
        Context ctx = new Context() {
            @Override
            public boolean reachable(BlockPos pos) {
                return level.isLoaded(pos) && RemoteView.canReachGhost(player, pos) && level.mayInteract(player, pos);
            }

            @Override
            public boolean recipeUnlocked(String recipeId) {
                return RecipeLocks.isUnlocked(player.server, recipeId);
            }

            @Override
            public boolean commit(PartPlan plan) {
                if (!HandGhosts.mayCommit(player, level, plan.cells())) return false;
                if (plan.status() == Status.OK) return Ghosts.placePlanned(level, plan.origin(), plan.state(), plan.facing(), plan.settings());
                if (plan.status() == Status.UPGRADE) {
                    String to = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(plan.block().asItem()).toString();
                    var marks = dev.minetorio.content.robot.ConstructionMarks.get(level);
                    var order = marks.upgradeOrder(level, plan.origin());
                    if (order != null && order.toItem().equals(to)) return true;
                    // a paired underground belt is swapped as a pair: one order for both
                    BlockPos partner = UndergroundBeltBlock.pairedPartner(level, plan.origin());
                    if (partner != null) {
                        var po = marks.upgradeOrder(level, partner);
                        if (po != null && po.toItem().equals(to)) return true;
                    }
                    return marks.markUpgrade(level, plan.origin(), to, dev.minetorio.content.robot.RobotWork.piecesFor(level, plan.origin()));
                }
                for (BlockPos o : plan.obstacles()) if (DeconstructionMarking.mark(level, o)) marked[0]++;
                if (plan.obstacles().contains(plan.origin())) {
                    DeconstructionMarking.addPending(level, plan.origin(), plan.state(), plan.facing(), plan.settings());
                    return true;
                }
                return Ghosts.placePlanned(level, plan.origin(), plan.state(), plan.facing(), plan.settings());
            }
        };
        List<PartPlan> plans = plan(level, player, data, anchor.immutable(), BlueprintTransform.ofBits(bits), force, ctx);
        int placed = 0, same = 0, blocked = 0, locked = 0;
        for (PartPlan p : plans) {
            switch (p.status()) {
                case OK, FORCED, UPGRADE -> placed++;
                case SAME -> same++;
                case BLOCKED -> blocked++;
                case LOCKED -> locked++;
            }
        }
        Result r = new Result(true, placed, same, blocked, locked, marked[0]);
        player.displayClientMessage(Component.translatable("minetorio.blueprint.pasted", placed, blocked + locked, marked[0]), true);
        return r;
    }

    /** Forgets {@code player}'s paste budget (tests share one fake player). */
    public static void resetBudget(Player player) {
        BUDGETS.remove(player.getUUID());
        BlueprintCapture.resetBudget(player);
    }
}
