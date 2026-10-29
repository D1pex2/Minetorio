package dev.minetorio.content.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public final class BasicChestBlockEntity extends BlockEntity {
    private final SimpleContainer inventory;
    private final IItemHandler handler;
    public BasicChestBlockEntity(BlockPos pos, BlockState state) {
        super(BasicChests.CHEST.get(), pos, state);
        inventory = new SimpleContainer(((BasicChestBlock) state.getBlock()).size()) {
            @Override public void setChanged() { super.setChanged(); BasicChestBlockEntity.this.setChanged(); }
        };
        handler = new InvWrapper(inventory);
    }
    public SimpleContainer inventory() { return inventory; }
    public IItemHandler itemHandler() { return handler; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup); ContainerHelper.saveAllItems(tag, inventory.getItems(), lookup);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, lookup);
        for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
    }
}
