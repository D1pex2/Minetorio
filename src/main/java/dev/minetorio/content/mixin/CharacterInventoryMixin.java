package dev.minetorio.content.mixin;

import dev.minetorio.content.character.CharacterInventory;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds main inventory slots without moving vanilla armor/offhand indices 36..40. */
@Mixin(Inventory.class)
public abstract class CharacterInventoryMixin {
    @Shadow @Final @Mutable private List<NonNullList<ItemStack>> compartments;
    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow @Final public NonNullList<ItemStack> armor;
    @Shadow @Final public NonNullList<ItemStack> offhand;
    @Shadow @Final public Player player;
    @Shadow public int selected;
    @Shadow public abstract int getFreeSlot();
    @Shadow public abstract int getSuitableHotbarSlot();
    @Shadow public abstract int findSlotMatchingItem(ItemStack stack);
    @Shadow public abstract void setItem(int index, ItemStack stack);
    @Unique private final NonNullList<ItemStack> minetorio$extra = NonNullList.withSize(44, ItemStack.EMPTY);
    @Unique private final NonNullList<ItemStack> minetorio$special = NonNullList.withSize(2, ItemStack.EMPTY);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void minetorio$init(Player p, CallbackInfo ci) {
        compartments = List.of(items, armor, offhand, minetorio$extra, minetorio$special);
    }

    @Inject(method = "getContainerSize", at = @At("RETURN"), cancellable = true)
    private void minetorio$size(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(87); }

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void minetorio$emptySelected(CallbackInfoReturnable<ItemStack> cir) {
        if (((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand()) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void minetorio$emptyDestroySpeed(net.minecraft.world.level.block.state.BlockState state, CallbackInfoReturnable<Float> cir) {
        if (((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand()) {
            cir.setReturnValue(ItemStack.EMPTY.getDestroySpeed(state));
        }
    }

    @Inject(method = "swapPaint", at = @At("HEAD"))
    private void minetorio$scroll(double direction, CallbackInfo ci) {
        ((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand(false);
    }

    @Inject(method = "getFreeSlot", at = @At("RETURN"), cancellable = true)
    private void minetorio$free(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() != -1) return;
        for (int i = 0; i < minetorio$extra.size(); i++) {
            if (minetorio$extra.get(i).isEmpty()) {
                cir.setReturnValue(CharacterInventory.EXTRA_OFFSET + i);
                return;
            }
        }
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("RETURN"), cancellable = true)
    private void minetorio$stack(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() != -1) return;
        for (int i = 0; i < minetorio$extra.size(); i++) {
            ItemStack existing = minetorio$extra.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.isStackable() && existing.getCount() < Math.min(((Inventory)(Object)this).getMaxStackSize(), existing.getMaxStackSize())) {
                cir.setReturnValue(CharacterInventory.EXTRA_OFFSET + i);
                return;
            }
        }
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void minetorio$damaged(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !stack.isDamaged()) return;
        int index = slot == -1 ? getFreeSlot() : slot;
        if (index < CharacterInventory.EXTRA_OFFSET || index >= 85) return;
        ItemStack stored = stack.copyAndClear();
        stored.setPopTime(5);
        setItem(index, stored);
        cir.setReturnValue(true);
    }

    @Inject(method = "findSlotMatchingItem", at = @At("RETURN"), cancellable = true)
    private void minetorio$findExtra(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() != -1) return;
        for (int i = 0; i < minetorio$extra.size(); i++) {
            ItemStack found = minetorio$extra.get(i);
            if (!found.isEmpty() && ItemStack.isSameItemSameComponents(stack, found)) {
                cir.setReturnValue(CharacterInventory.EXTRA_OFFSET + i);
                return;
            }
        }
    }

    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    private void minetorio$pickExtra(int index, CallbackInfo ci) {
        ((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand(false);
        if (index >= 0 && index < CharacterInventory.VANILLA_MAIN_SIZE) return;
        if (index >= CharacterInventory.EXTRA_OFFSET && index < CharacterInventory.EXTRA_OFFSET + minetorio$extra.size()) {
            int hotbar = getSuitableHotbarSlot();
            ItemStack old = items.get(hotbar);
            items.set(hotbar, minetorio$extra.get(index - CharacterInventory.EXTRA_OFFSET));
            minetorio$extra.set(index - CharacterInventory.EXTRA_OFFSET, old);
            selected = hotbar;
        }
        // Reject invalid or equipment indices rather than indexing the 36-element main list.
        ci.cancel();
    }

    @Inject(method = "setPickedItem", at = @At("HEAD"), cancellable = true)
    private void minetorio$pick(ItemStack stack, CallbackInfo ci) {
        ((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand(false);
        if (findSlotMatchingItem(stack) != -1) return;
        int chosen = getSuitableHotbarSlot();
        if (items.get(chosen).isEmpty()) return;
        int free = getFreeSlot();
        if (free < 0) { ci.cancel(); return; }
        if (free < CharacterInventory.EXTRA_OFFSET) return;
        setItem(free, items.get(chosen));
        selected = chosen;
        items.set(chosen, stack);
        ci.cancel();
    }

    @Inject(method = "isEmpty", at = @At("RETURN"), cancellable = true)
    private void minetorio$empty(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            for (ItemStack stack : minetorio$extra) if (!stack.isEmpty()) { cir.setReturnValue(false); return; }
            for (ItemStack stack : minetorio$special) if (!stack.isEmpty()) { cir.setReturnValue(false); return; }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void minetorio$tool(CallbackInfo ci) {
        if (!player.level().isClientSide && minetorio$special.get(0).isEmpty()) {
            minetorio$special.set(0, new ItemStack(dev.minetorio.content.registry.BaseMaterials.ENGINEER_TOOL.get()));
        }
    }

    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
    private void minetorio$dropExceptTool(CallbackInfo ci) {
        // The locked character tool survives death; everything else follows vanilla drop semantics.
        for (int section = 0; section < compartments.size(); section++) {
            NonNullList<ItemStack> list = compartments.get(section);
            for (int i = 0; i < list.size(); i++) {
                if (section == compartments.size() - 1 && i == 0) continue;
                ItemStack stack = list.get(i);
                if (!stack.isEmpty()) { player.drop(stack, true, false); list.set(i, ItemStack.EMPTY); }
            }
        }
        ci.cancel();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void minetorio$save(ListTag tag, CallbackInfoReturnable<ListTag> cir) {
        for (int i = 0; i < minetorio$extra.size(); i++) {
            ItemStack stack = minetorio$extra.get(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte)(CharacterInventory.VANILLA_MAIN_SIZE + i));
                tag.add(stack.save(player.registryAccess(), entry));
            }
        }
        for (int i = 0; i < minetorio$special.size(); i++) {
            ItemStack stack = minetorio$special.get(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte)(CharacterInventory.TOOL_SLOT + i));
                tag.add(stack.save(player.registryAccess(), entry));
            }
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void minetorio$load(ListTag tag, CallbackInfo ci) {
        minetorio$extra.clear();
        minetorio$special.clear();
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag entry = tag.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot >= CharacterInventory.VANILLA_MAIN_SIZE && slot < CharacterInventory.MAIN_SIZE) {
                minetorio$extra.set(slot - CharacterInventory.VANILLA_MAIN_SIZE,
                        ItemStack.parse(player.registryAccess(), entry).orElse(ItemStack.EMPTY));
            } else if (slot >= CharacterInventory.TOOL_SLOT && slot <= CharacterInventory.TRASH_SLOT) {
                minetorio$special.set(slot - CharacterInventory.TOOL_SLOT,
                        ItemStack.parse(player.registryAccess(), entry).orElse(ItemStack.EMPTY));
            }
        }
    }
}
