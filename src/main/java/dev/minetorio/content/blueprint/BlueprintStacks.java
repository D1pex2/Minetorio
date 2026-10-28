package dev.minetorio.content.blueprint;

import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Blueprint and book item stacks to and from {@link BlueprintRecord}s, and what a paste with one uses. */
public final class BlueprintStacks {
    private BlueprintStacks() {}

    /** Whether {@code stack} is a blueprint or a blueprint book. */
    public static boolean isBlueprintLike(ItemStack stack) {
        return stack.getItem() instanceof BlueprintItem || stack.getItem() instanceof BlueprintBookItem;
    }

    public static boolean isBook(ItemStack stack) {
        return stack.getItem() instanceof BlueprintBookItem;
    }

    public static BlueprintMeta meta(ItemStack stack) {
        BlueprintMeta m = stack.get(ModDataComponents.BLUEPRINT_META.get());
        return m == null ? BlueprintMeta.EMPTY : m;
    }

    /** A book's contents (empty when none, or not a book). */
    public static BlueprintBookData book(ItemStack stack) {
        if (!isBook(stack)) return BlueprintBookData.EMPTY;
        BlueprintBookData d = stack.get(ModDataComponents.BLUEPRINT_BOOK.get());
        return d == null ? BlueprintBookData.EMPTY : d;
    }

    /** The record of a blueprint or book stack, or null. */
    @Nullable
    public static BlueprintRecord record(ItemStack stack) {
        if (stack.getItem() instanceof BlueprintItem) return BlueprintRecord.ofBlueprint(meta(stack), BlueprintItem.data(stack));
        if (isBook(stack)) return BlueprintRecord.ofBook(meta(stack), book(stack));
        return null;
    }

    /** A new stack holding {@code r}. */
    public static ItemStack stack(BlueprintRecord r) {
        ItemStack s;
        if (r.isBook()) {
            s = new ItemStack(ModItems.BLUEPRINT_BOOK.get());
            if (!r.book().get().isEmpty()) s.set(ModDataComponents.BLUEPRINT_BOOK.get(), r.book().get());
        } else {
            s = BlueprintItem.empty();
            if (r.data() != null) s.set(ModDataComponents.BLUEPRINT.get(), r.data());
        }
        if (!r.meta().isEmpty()) s.set(ModDataComponents.BLUEPRINT_META.get(), r.meta());
        return s;
    }

    /** Sets the contents of a book stack (removing the component when empty). */
    public static void setBook(ItemStack stack, BlueprintBookData d) {
        if (d.isEmpty()) stack.remove(ModDataComponents.BLUEPRINT_BOOK.get());
        else stack.set(ModDataComponents.BLUEPRINT_BOOK.get(), d);
    }

    public static void setMeta(ItemStack stack, BlueprintMeta m) {
        if (m.isEmpty()) stack.remove(ModDataComponents.BLUEPRINT_META.get());
        else stack.set(ModDataComponents.BLUEPRINT_META.get(), m);
    }

    /** What a paste with {@code stack} uses: a filled blueprint's data, or a book's active blueprint; null otherwise. */
    @Nullable
    public static BlueprintData activeData(ItemStack stack) {
        if (stack.getItem() instanceof BlueprintItem) return BlueprintItem.data(stack);
        if (!isBook(stack)) return null;
        BlueprintRecord r = record(stack);
        return r == null ? null : r.activeData();
    }

    /** The name a record shows: its label, else the item's default name. */
    public static Component name(BlueprintRecord r) {
        if (r.meta().hasLabel()) return Component.literal(r.meta().label());
        if (r.isBook()) return Component.translatable("item.minetorio.blueprint_book");
        return Component.translatable(r.data() == null ? "item.minetorio.blueprint.empty" : "item.minetorio.blueprint");
    }
}
