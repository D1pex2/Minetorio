package dev.minetorio.content.world;

import dev.minetorio.content.character.CharacterInventory;
import dev.minetorio.content.map.RemoteView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class BasicChestMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final int size;
    public BasicChestMenu(int id, Inventory inventory, BlockPos pos, Container chest) {
        super(BasicChests.MENU.get(), id); this.pos = pos; size = chest.getContainerSize();
        for (int i = 0; i < size; i++) addSlot(new Slot(chest, i, 26 + i % 8 * 18, 24 + i / 8 * 18));
        var main = CharacterInventory.main(inventory);
        for (int i = 0; i < 80; i++) addSlot(new Slot(main, i, 8 + i % 10 * 18, 106 + i / 10 * 18));
    }
    public static BasicChestMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        BlockPos pos = extra.readBlockPos();
        var state = inventory.player.level().getBlockState(pos);
        int size = state.getBlock() instanceof BasicChestBlock chest ? chest.size() : state.getBlock() instanceof CrashSiteChestBlock ? CrashSiteChestBlockEntity.SIZE : 16;
        return new BasicChestMenu(id, inventory, pos, new SimpleContainer(size));
    }
    @Override public boolean stillValid(Player player) { return (player.level().getBlockEntity(pos) instanceof BasicChestBlockEntity || player.level().getBlockEntity(pos) instanceof CrashSiteChestBlockEntity) && player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5) <= 64; }
    @Override public void clicked(int slot, int button, ClickType click, Player player) { if (!RemoteView.blocksTransfer(player)) super.clicked(slot, button, click, player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (RemoteView.blocksTransfer(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < size ? !moveItemStackTo(stack, size, slots.size(), false) : !moveItemStackTo(stack, 0, size, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
}
