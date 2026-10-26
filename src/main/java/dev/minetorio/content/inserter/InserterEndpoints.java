package dev.minetorio.content.inserter;

import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.BeltGeometry;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.multiblock.MultiblockPartBlockEntity;
import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * What an inserter (or a drill) takes from and puts into, resolved per block, in this order:
 * <ol>
 *   <li>a machine: the block entity, or the controller of a multiblock part, implementing
 *       {@link MachineEndpoint};</li>
 *   <li>a transport belt: pickup takes the front-most wanted item of either lane; drop goes onto
 *       the lane far from the inserter ({@link BeltGeometry#farLane}) at mid-length (0.5 on a
 *       straight belt) if {@link BeltLane#canInsertAt} there;</li>
 *   <li>an {@code IItemHandler} (vanilla chests, barrels, hoppers, ...), queried on side
 *       {@code from}: pickup takes 1 item from the first slot holding a wanted item (simulated
 *       extraction first), drop inserts 1 item (simulated first).</li>
 * </ol>
 * Items are stack keys; items whose key is too large ({@link LaneStacks#toLane}) are never picked
 * up. A belt or container accepts any item it could hold ({@code wants}); whether there is room
 * right now is decided at the drop, so a full target makes the inserter wait holding the item.
 */
public final class InserterEndpoints {
    /** Offers nothing. */
    public static final PickupSource NO_SOURCE = new PickupSource() {
        @Override
        public Optional<String> peek(Predicate<String> wanted) { return Optional.empty(); }

        @Override
        public boolean take(String item) { return false; }
    };

    /** Accepts nothing. */
    public static final DropTarget NO_TARGET = new DropTarget() {
        @Override
        public boolean wants(String item) { return false; }

        @Override
        public boolean accept(String item) { return false; }
    };

    private InserterEndpoints() {}

    /**
     * The pickup endpoint at {@code pos}, or null if there is none (or its chunk is not loaded).
     * {@code from} is the side of that block the inserter stands on.
     */
    @Nullable
    public static PickupSource source(Level level, BlockPos pos, Direction from) {
        if (!level.isLoaded(pos)) return null;
        MachineEndpoint machine = machine(level, pos);
        if (machine != null) return machine.asSource();
        if (level.getBlockEntity(pos) instanceof BeltBlockEntity belt) return beltSource(belt);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, from);
        return handler == null ? null : handlerSource(handler, level.registryAccess());
    }

    /**
     * The drop endpoint at {@code pos}, or null if there is none (or its chunk is not loaded).
     * {@code from} is the side of that block the inserter stands on.
     */
    @Nullable
    public static DropTarget target(Level level, BlockPos pos, Direction from) {
        if (!level.isLoaded(pos)) return null;
        MachineEndpoint machine = machine(level, pos);
        if (machine != null) return machine.asTarget();
        if (level.getBlockEntity(pos) instanceof BeltBlockEntity belt) return beltTarget(belt, from);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, from);
        return handler == null ? null : handlerTarget(handler, level.registryAccess());
    }

    /** The machine at {@code pos}: its own block entity or, for a multiblock part, its controller. */
    @Nullable
    private static MachineEndpoint machine(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MultiblockPartBlockEntity part) be = part.controller();
        return be instanceof MachineEndpoint m ? m : null;
    }

    // --- belts -----------------------------------------------------------------------------------

    private static PickupSource beltSource(BeltBlockEntity belt) {
        return new PickupSource() {
            @Override
            public Optional<String> peek(Predicate<String> wanted) {
                LaneItem it = frontMost(belt, wanted);
                return it == null ? Optional.empty() : Optional.of(it.item());
            }

            @Override
            public boolean take(String item) {
                LaneSide side = frontMostSide(belt, item::equals);
                return side != null && belt.lane(side).takeFirstMatching(item::equals).isPresent();
            }
        };
    }

    /** The lane whose first item matching {@code p} is farthest along (relative to lane length). */
    @Nullable
    private static LaneSide frontMostSide(BeltBlockEntity belt, Predicate<String> p) {
        LaneSide best = null;
        double bestT = -1;
        for (LaneSide side : LaneSide.values()) {
            BeltLane lane = belt.lane(side);
            for (LaneItem it : lane.items()) { // front-first
                if (!p.test(it.item())) continue;
                double t = it.pos() / lane.length();
                if (t > bestT) {
                    bestT = t;
                    best = side;
                }
                break;
            }
        }
        return best;
    }

    @Nullable
    private static LaneItem frontMost(BeltBlockEntity belt, Predicate<String> p) {
        LaneSide side = frontMostSide(belt, p);
        if (side == null) return null;
        for (LaneItem it : belt.lane(side).items()) {
            if (p.test(it.item())) return it;
        }
        return null;
    }

    private static DropTarget beltTarget(BeltBlockEntity belt, Direction from) {
        return new DropTarget() {
            @Override
            public boolean wants(String item) { return true; }

            @Override
            public boolean accept(String item) {
                BeltLane lane = belt.lane(BeltGeometry.farLane(belt.facing(), from));
                return lane.insertAt(item, lane.length() / 2);
            }
        };
    }

    // --- item handlers ---------------------------------------------------------------------------

    private static PickupSource handlerSource(IItemHandler handler, HolderLookup.Provider registries) {
        return new PickupSource() {
            @Override
            public Optional<String> peek(Predicate<String> wanted) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    String key = keyIn(i, null);
                    if (key != null && wanted.test(key) && !handler.extractItem(i, 1, true).isEmpty()) return Optional.of(key);
                }
                return Optional.empty();
            }

            @Override
            public boolean take(String item) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (!item.equals(keyIn(i, item))) continue;
                    ItemStack simulated = handler.extractItem(i, 1, true);
                    if (simulated.isEmpty() || !item.equals(LaneStacks.key(simulated, registries))) continue;
                    if (!handler.extractItem(i, 1, false).isEmpty()) return true;
                }
                return false;
            }

            /** Key of slot {@code i}, or null if empty/too large; skips the key build when the item id differs from {@code expected}'s. */
            @Nullable
            private String keyIn(int i, @Nullable String expected) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) return null;
                if (expected != null && !LaneItem.baseId(expected).equals(stack.getItemHolder().getRegisteredName())) return null;
                return LaneStacks.toLane(stack, registries).orElse(null);
            }
        };
    }

    private static DropTarget handlerTarget(IItemHandler handler, HolderLookup.Provider registries) {
        return new DropTarget() {
            @Override
            public boolean wants(String item) {
                ItemStack stack = LaneStacks.stack(item, registries);
                if (stack.isEmpty()) return false;
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (handler.isItemValid(i, stack)) return true;
                }
                return false;
            }

            @Override
            public boolean accept(String item) {
                ItemStack stack = LaneStacks.stack(item, registries);
                if (stack.isEmpty() || !ItemHandlerHelper.insertItem(handler, stack, true).isEmpty()) return false;
                return ItemHandlerHelper.insertItem(handler, stack, false).isEmpty();
            }
        };
    }
}
