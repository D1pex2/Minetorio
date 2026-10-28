package dev.minetorio.content.blueprint;

import dev.minetorio.core.blueprint.BlueprintBooks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of the book window and the name/icon editor: every change names the inventory slot
 * of the book or blueprint ({@code slot}, 0..35) and a {@code path} (entry indices from the outer
 * book in; empty means the item itself). Each change is applied to the record, the whole record is
 * checked again ({@link BlueprintRecord#validate}, {@link BlueprintRecord#fitsSize}) and only then
 * written back. Also the wheel ({@link #scroll}) on the book in the main hand.
 */
public final class BlueprintBookOps {
    private BlueprintBookOps() {}

    /** Book window operations ({@link BlueprintPayloads.BookEdit#op}). */
    public enum Op {
        /** Make entry {@code a} active. */
        SELECT,
        /** Move entry {@code a} to index {@code b}. */
        MOVE,
        /** Delete entry {@code a} (blueprints are free, as in Factorio). */
        REMOVE,
        /** Put the blueprint or book in inventory slot {@code a} into the book at index {@code b}. */
        INSERT,
        /** Take entry {@code a} out into inventory slot {@code b} (or a free slot). */
        EXTRACT;

        @Nullable
        public static Op byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : null;
        }
    }

    static boolean validSlot(int slot) {
        return slot >= 0 && slot < Inventory.INVENTORY_SIZE;
    }

    static boolean mayEdit(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator();
    }

    /** The record at {@code path} in {@code root}, or null. */
    @Nullable
    public static BlueprintRecord at(BlueprintRecord root, List<Integer> path) {
        BlueprintRecord r = root;
        for (int i : path) {
            if (r == null || !r.isBook() || i < 0 || i >= r.book().get().size()) return null;
            r = r.book().get().entries().get(i);
        }
        return r;
    }

    /** {@code root} with the record at {@code path} replaced by {@code r} (the path must exist). */
    public static BlueprintRecord replaced(BlueprintRecord root, List<Integer> path, BlueprintRecord r) {
        if (path.isEmpty()) return r;
        BlueprintBookData book = root.book().orElseThrow();
        int i = path.getFirst();
        BlueprintRecord child = replaced(book.entries().get(i), path.subList(1, path.size()), r);
        return root.withBook(book.replaced(i, child));
    }

    /** Writes {@code r} into {@code stack} (same kind) if it is within the limits. */
    static boolean store(ItemStack stack, BlueprintRecord r) {
        if (r.validate().isError() || !r.fitsSize()) return false;
        BlueprintStacks.setMeta(stack, r.meta());
        if (r.isBook()) BlueprintStacks.setBook(stack, r.book().get());
        return true;
    }

    /** The book window's change {@code op} with arguments {@code a}, {@code b} on the book at {@code path} in slot {@code slot}. */
    public static boolean edit(ServerPlayer player, int slot, List<Integer> path, Op op, int a, int b) {
        if (op == null || !mayEdit(player) || !validSlot(slot) || !BlueprintBooks.validPath(path)) return false;
        Inventory inv = player.getInventory();
        ItemStack stack = inv.getItem(slot);
        if (!BlueprintStacks.isBook(stack)) return false;
        BlueprintRecord root = BlueprintStacks.record(stack);
        BlueprintRecord target = root == null ? null : at(root, path);
        if (target == null || !target.isBook()) return false;
        BlueprintBookData book = target.book().get();
        boolean inRange = a >= 0 && a < book.size();
        switch (op) {
            case SELECT -> {
                if (!inRange) return false;
                return store(stack, replaced(root, path, target.withBook(book.withActive(a))));
            }
            case MOVE -> {
                if (!inRange || b < 0 || b >= book.size()) return false;
                return store(stack, replaced(root, path, target.withBook(book.moved(a, b))));
            }
            case REMOVE -> {
                if (!inRange) return false;
                return store(stack, replaced(root, path, target.withBook(book.removed(a))));
            }
            case INSERT -> {
                if (!validSlot(a) || a == slot || book.size() >= BlueprintBooks.MAX_ENTRIES) return false;
                ItemStack from = inv.getItem(a);
                BlueprintRecord r = BlueprintStacks.record(from);
                if (r == null) return false;
                if (!store(stack, replaced(root, path, target.withBook(book.inserted(b < 0 ? book.size() : b, r))))) return false;
                inv.setItem(a, ItemStack.EMPTY);
            }
            case EXTRACT -> {
                if (!inRange) return false;
                int to = validSlot(b) && b != slot && inv.getItem(b).isEmpty() ? b : inv.getFreeSlot();
                if (to < 0 || to >= Inventory.INVENTORY_SIZE) return false;
                ItemStack out = BlueprintStacks.stack(book.entries().get(a));
                if (!store(stack, replaced(root, path, target.withBook(book.removed(a))))) return false;
                inv.setItem(to, out);
            }
        }
        inv.setChanged();
        return true;
    }

    /** Sets the name and icons of the record at {@code path} in slot {@code slot} (a blueprint or a book). */
    public static boolean setMeta(ServerPlayer player, int slot, List<Integer> path, BlueprintMeta meta) {
        if (!mayEdit(player) || !validSlot(slot) || !BlueprintBooks.validPath(path)) return false;
        ItemStack stack = player.getInventory().getItem(slot);
        BlueprintRecord root = BlueprintStacks.record(stack);
        BlueprintRecord target = root == null ? null : at(root, path);
        if (target == null) return false;
        BlueprintMeta clean = meta.withLabel(meta.label()).withIcons(meta.icons());
        if (!store(stack, replaced(root, path, target.withMeta(clean)))) return false;
        player.getInventory().setChanged();
        return true;
    }

    /** Ctrl + wheel: the active entry of the book in the main hand {@code delta} steps on. */
    public static boolean scroll(ServerPlayer player, int delta) {
        ItemStack stack = player.getMainHandItem();
        if (!mayEdit(player) || !BlueprintStacks.isBook(stack) || delta == 0) return false;
        BlueprintBookData d = BlueprintStacks.book(stack);
        if (d.isEmpty()) return false;
        BlueprintStacks.setBook(stack, d.cycled(Integer.signum(delta)));
        return true;
    }
}
