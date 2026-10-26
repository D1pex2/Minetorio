package dev.minetorio.content.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The slots of a machine GUI as a vanilla {@link Container}, plus how much more of a stack the
 * player may put into each slot ({@link #room}), which {@link MachineSlot} uses for its limits.
 * The server side is a live view of the machine's core state ({@link View}); the client side is a
 * plain container filled by the menu sync ({@link #client}) with a predicted {@code room}.
 */
public interface MachineContainer extends Container {
    /** How many more of {@code stack} the player may put into {@code slot} now (0 = not allowed). */
    int room(int slot, ItemStack stack);

    /**
     * Whether {@code stack}'s type belongs in {@code slot} at all, regardless of what the slot holds
     * now (plain stack; fuel for a fuel slot, a recipe input for an input slot; never an output slot).
     */
    boolean accepts(int slot, ItemStack stack);

    /**
     * Stacks a {@link #setItem} could not store (see {@link View#setItem}); the menu hands them back
     * to the player after each click. Taking clears them.
     */
    default List<ItemStack> takeOverflow() { return List.of(); }

    /** Client side: a plain container whose {@code room} (and so {@code accepts}) is predicted by {@code room}. */
    static MachineContainer client(int size, BiFunction<Integer, ItemStack, Integer> room) {
        class Client extends SimpleContainer implements MachineContainer {
            Client() { super(size); }

            @Override
            public int room(int slot, ItemStack stack) { return room.apply(slot, stack); }

            @Override
            public boolean accepts(int slot, ItemStack stack) { return room(slot, stack) > 0; }
        }
        return new Client();
    }

    /**
     * Server side: each slot shows one item id and count of the machine and maps the vanilla
     * container operations onto inserts and extracts by id. A slot never holds a stack with data
     * components ({@link #room} is 0 for them), so the id is the whole stack.
     */
    abstract class View implements MachineContainer {
        private final List<ItemStack> overflow = new ArrayList<>();

        /** Item id shown in {@code slot}, or null when it is empty. */
        @Nullable
        protected abstract String itemIn(int slot);

        protected abstract int countIn(int slot);

        /** Player insert of {@code n} × {@code id}; returns the amount accepted. */
        protected abstract int insert(int slot, String id, int n);

        /** Player extract of up to {@code n} from {@code slot}; returns the amount taken. */
        protected abstract int extract(int slot, int n);

        /** Whether the player can still use the machine. */
        protected abstract boolean usableBy(Player player);

        @Override
        public ItemStack getItem(int slot) {
            String id = itemIn(slot);
            return id == null ? ItemStack.EMPTY : MachineItems.stack(id, countIn(slot));
        }

        /**
         * Makes the slot hold {@code stack} by extracting/inserting the difference (a different item
         * replaces the current one, which the caller already took). {@link MachineSlot} keeps callers
         * within {@link #room}; anything the machine still refuses is kept in {@link #takeOverflow}
         * so it is returned, never lost.
         */
        @Override
        public void setItem(int slot, ItemStack stack) {
            ItemStack current = getItem(slot);
            if (!current.isEmpty() && (stack.isEmpty() || !ItemStack.isSameItemSameComponents(current, stack))) {
                extract(slot, current.getCount());
                current = ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                setChanged();
                return;
            }
            int delta = stack.getCount() - current.getCount();
            int accepted = 0;
            if (delta > 0 && MachineItems.isPlain(stack)) accepted = insert(slot, MachineItems.id(stack), delta);
            else if (delta < 0) extract(slot, -delta);
            if (delta > accepted) overflow.add(stack.copyWithCount(delta - accepted));
            setChanged();
        }

        @Override
        public List<ItemStack> takeOverflow() {
            List<ItemStack> out = List.copyOf(overflow);
            overflow.clear();
            return out;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int taken = extract(slot, amount);
            if (taken > 0) setChanged();
            return current.copyWithCount(taken);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, Integer.MAX_VALUE);
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < getContainerSize(); i++) if (itemIn(i) != null) return false;
            return true;
        }

        @Override
        public boolean stillValid(Player player) { return usableBy(player); }

        /** Machine contents are not cleared through the GUI. */
        @Override
        public void clearContent() {}
    }
}
