package dev.minetorio.content.mixin;

import dev.minetorio.content.character.CharacterShortcuts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class CharacterShortcutsMixin implements CharacterShortcuts {
    @Unique private final int[] minetorio$shortcuts = new int[ROWS * COLUMNS];
    @Unique private int minetorio$row;
    @Unique private int minetorio$visible = 1;
    @Unique private boolean minetorio$emptyHand;

    @Override public int shortcut(int index) { return index >= 0 && index < minetorio$shortcuts.length ? minetorio$shortcuts[index] : 0; }
    @Override public void shortcut(int index, int itemId) {
        if (index >= 0 && index < minetorio$shortcuts.length) minetorio$shortcuts[index] = Math.max(0, itemId);
    }
    @Override public int selectedRow() { return minetorio$row; }
    @Override public void selectedRow(int row) { if (row >= 0 && row < ROWS) minetorio$row = row; }
    @Override public boolean emptyHand() { return minetorio$emptyHand; }
    @Override public void emptyHand(boolean empty) { minetorio$emptyHand = empty; }
    @Override public int visibleRows() { return minetorio$visible; }
    @Override public void visibleRows(int rows) { if (rows >= 1 && rows <= 4) minetorio$visible = rows; }


    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void minetorio$protectHiddenHand(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND || !minetorio$emptyHand) return;
        // An interaction may try to replace an empty hand; never overwrite the real occupied slot.
        Player self = (Player)(Object)this;
        if (!self.level().isClientSide && !stack.isEmpty()) {
            ItemStack replacement = stack.copyAndClear();
            self.getInventory().add(replacement);
            if (!replacement.isEmpty()) self.drop(replacement, false);
        }
        ci.cancel();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void minetorio$saveShortcuts(CompoundTag tag, CallbackInfo ci) {
        CompoundTag shortcuts = new CompoundTag();
        ListTag entries = new ListTag();
        for (int i = 0; i < minetorio$shortcuts.length; i++) {
            Item item = Item.byId(minetorio$shortcuts[i]);
            if (item == Items.AIR) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            entry.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
            entries.add(entry);
        }
        shortcuts.put("items", entries);
        shortcuts.putInt("row", minetorio$row);
        shortcuts.putInt("visible", minetorio$visible);
        shortcuts.putBoolean("emptyHand", minetorio$emptyHand);
        tag.put("MinetorioShortcuts", shortcuts);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void minetorio$loadShortcuts(CompoundTag tag, CallbackInfo ci) {
        java.util.Arrays.fill(minetorio$shortcuts, 0);
        CompoundTag shortcuts = tag.getCompound("MinetorioShortcuts");
        ListTag entries = shortcuts.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("item"));
            if (id != null) shortcut(entry.getInt("slot"), Item.getId(BuiltInRegistries.ITEM.get(id)));
        }
        emptyHand(shortcuts.getBoolean("emptyHand"));
        selectedRow(shortcuts.getInt("row"));
        visibleRows(shortcuts.getInt("visible"));
    }
}
