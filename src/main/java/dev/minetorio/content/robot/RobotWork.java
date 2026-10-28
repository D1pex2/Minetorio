package dev.minetorio.content.robot;

import dev.minetorio.Minetorio;
import dev.minetorio.content.belt.BeltBlock;
import dev.minetorio.content.belt.SplitterBlock;
import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlockEntity;
import dev.minetorio.core.data.UpgradeGroups;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * What construction robots do to marked blocks: deconstruct them ({@link #deconstruct}, everything
 * that comes out goes into the robot's cargo instead of onto the ground) and upgrade them
 * ({@link #upgrade}, fast-replace semantics: orientation, shape, settings and contents kept), plus the
 * rules for what the deconstruction planner may mark.
 */
public final class RobotWork {
    /** Natural blocks the "trees and rocks" filter marks (data: {@code data/minetorio/tags/block/deconstructible_nature.json}). */
    public static final TagKey<Block> NATURE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "deconstructible_nature"));

    /** While non-null: item entities spawning in {@link #captureLevel} go here instead (main thread only). */
    @Nullable private static List<ItemStack> capture;
    @Nullable private static Level captureLevel;

    private RobotWork() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RobotWork::onJoin);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (capture == null || event.getLevel() != captureLevel || !(event.getEntity() instanceof ItemEntity item)) return;
        if (!item.getItem().isEmpty()) capture.add(item.getItem().copy());
        event.setCanceled(true);
    }

    /** Runs {@code action} and returns the items it would have dropped in {@code level} (merged). */
    public static List<ItemStack> captureDrops(ServerLevel level, Runnable action) {
        List<ItemStack> prev = capture;
        Level prevLevel = captureLevel;
        List<ItemStack> got = new ArrayList<>();
        capture = got;
        captureLevel = level;
        try {
            action.run();
        } finally {
            capture = prev;
            captureLevel = prevLevel;
        }
        return merge(got);
    }

    /** {@code stacks} with equal items merged (respecting stack sizes). */
    public static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            ItemStack rest = s.copy();
            for (ItemStack o : out) {
                if (rest.isEmpty()) break;
                if (ItemStack.isSameItemSameComponents(o, rest) && o.getCount() < o.getMaxStackSize()) {
                    int n = Math.min(rest.getCount(), o.getMaxStackSize() - o.getCount());
                    o.grow(n);
                    rest.shrink(n);
                }
            }
            if (!rest.isEmpty()) out.add(rest);
        }
        return out;
    }

    // --- what may be marked ---------------------------------------------------------------------

    /** Whether the deconstruction planner marks {@code state} as a Minetorio block: a placeable Minetorio block (not a ghost, ore field or machine piece). */
    public static boolean isMinetorioBuildable(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof GhostBlock || b instanceof MultiblockPartBlock) return false;
        Item item = b.asItem();
        return item instanceof BlockItem && Minetorio.MODID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace());
    }

    /** Whether {@code state} is a tree or rock ({@link #NATURE}). */
    public static boolean isNature(BlockState state) {
        return state.is(NATURE);
    }

    /** The item a deconstructed block mainly gives (its own item; may be AIR for blocks without one). */
    public static ItemStack mainItem(BlockState state) {
        return new ItemStack(state.getBlock().asItem());
    }

    // --- deconstruction -------------------------------------------------------------------------

    /**
     * Breaks the block at {@code pos} (a machine: all of it) as if mined without a tool and returns
     * what it gave: its item(s) from the loot table and whatever it held (machine inventories,
     * items on belts, an inserter's hand and fuel). Null when there is nothing to break.
     */
    @Nullable
    public static List<ItemStack> deconstruct(ServerLevel level, BlockPos pos) {
        BlockPos at = ConstructionMarks.markPos(level, pos);
        BlockState state = level.getBlockState(at);
        if (state.isAir() || state.getDestroySpeed(level, at) < 0 || state.getBlock() instanceof GhostBlock) return null;
        return captureDrops(level, () -> level.destroyBlock(at, true));
    }

    // --- upgrade --------------------------------------------------------------------------------

    /** Item id of the block at {@code pos} (a machine piece: its controller's). */
    public static String itemId(BlockState state) {
        return BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
    }

    /** How many of the new item an upgrade of the block at {@code pos} takes: 2 for a paired underground belt, else 1. */
    public static int piecesFor(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof UndergroundBeltBlock && UndergroundBeltBlock.pairedPartner(level, pos) != null ? 2 : 1;
    }

    /** What an upgrade gave back: the old items (and contents that did not fit), and how many new items it used. */
    public record Upgraded(List<ItemStack> old, int used) {}

    /**
     * Replaces the block at {@code pos} (a machine: its controller) with {@code target}'s block, with
     * {@code have} target items at hand. Belts, underground belts (with their partner) and splitters
     * use the hand's fast-replace; other blocks (inserters, assemblers) are swapped keeping their
     * block state properties and block entity data (recipe, filters, contents). Null when the swap
     * is not possible (not compatible, not enough items, out of range).
     */
    @Nullable
    public static Upgraded upgrade(ServerLevel level, BlockPos pos, Item target, int have) {
        BlockPos at = ConstructionMarks.markPos(level, pos);
        BlockState state = level.getBlockState(at);
        Block current = state.getBlock();
        if (!(target instanceof BlockItem bi) || have < 1) return null;
        Block next = bi.getBlock();
        String from = itemId(state);
        String to = BuiltInRegistries.ITEM.getKey(target).toString();
        if (!UpgradeGroups.compatible(from, to)) return null;
        if (current instanceof BeltBlock && next instanceof BeltBlock nb) {
            return BeltBlock.replaceTier(level, at, state, nb) == null ? null : new Upgraded(List.of(new ItemStack(current.asItem())), 1);
        }
        if (current instanceof SplitterBlock && next instanceof SplitterBlock ns) {
            return SplitterBlock.replaceTier(level, at, state, ns) == null ? null : new Upgraded(List.of(new ItemStack(current.asItem())), 1);
        }
        if (current instanceof UndergroundBeltBlock && next instanceof UndergroundBeltBlock nu) {
            if (piecesFor(level, at) > have) return null;
            int n = UndergroundBeltBlock.replaceTier(level, at, state, nu);
            return n == 0 ? null : new Upgraded(List.of(new ItemStack(current.asItem(), n)), n);
        }
        List<ItemStack> extra = swap(level, at, state, next);
        if (extra == null) return null;
        List<ItemStack> old = new ArrayList<>();
        old.add(new ItemStack(current.asItem()));
        old.addAll(extra);
        return new Upgraded(merge(old), 1);
    }

    /**
     * Swaps the block at {@code pos} for {@code next} keeping every block state property both share
     * and the block entity's saved data. A machine's parts stay and are relinked to the new
     * controller. Returns contents the new block cannot hold (a burner inserter's fuel going to an
     * electric one), or null when the swap fails.
     */
    @Nullable
    static List<ItemStack> swap(ServerLevel level, BlockPos pos, BlockState state, Block next) {
        if (state.getBlock() instanceof MultiblockControllerBlock a
                && !(next instanceof MultiblockControllerBlock b && a.shape().equals(b.shape()))) {
            return null;
        }
        var registries = level.registryAccess();
        BlockEntity oldBe = level.getBlockEntity(pos);
        CompoundTag data = oldBe == null ? null : oldBe.saveWithoutMetadata(registries);
        List<ItemStack> extra = new ArrayList<>();
        BlockState placed = next.defaultBlockState();
        for (Property<?> p : state.getProperties()) placed = copy(state, placed, p);
        java.util.Map<String, Integer> oldFuel = oldBe instanceof InserterBlockEntity ib && ib.burner() != null
                ? ib.burner().fuel() : java.util.Map.of();
        // a machine's controller keeps its parts and drops nothing; any other block loses its block entity first, so its removal drops nothing
        if (oldBe instanceof dev.minetorio.content.multiblock.MultiblockControllerBlockEntity mc) mc.prepareReplace();
        else if (oldBe != null) level.removeBlockEntity(pos);
        BlockState finalPlaced = placed;
        level.setBlock(pos, finalPlaced, Block.UPDATE_ALL);
        BlockEntity nb = level.getBlockEntity(pos);
        if (nb != null && data != null) {
            nb.loadWithComponents(data, registries);
            nb.setChanged();
        }
        if (!oldFuel.isEmpty() && !(nb instanceof InserterBlockEntity ni && ni.burner() != null)) {
            extra.addAll(captureDrops(level, () -> MachineItems.drop(level, pos, oldFuel)));
        }
        if (next instanceof MultiblockControllerBlock mb) {
            for (BlockPos p : mb.footprint(pos, finalPlaced)) {
                if (!p.equals(pos) && level.getBlockEntity(p) instanceof MultiblockPartBlockEntity part) {
                    part.link(pos.subtract(p), next);
                    part.setChanged();
                }
            }
        }
        level.sendBlockUpdated(pos, state, finalPlaced, Block.UPDATE_ALL);
        return extra;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }
}
