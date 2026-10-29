package dev.minetorio.content.oil;

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

public final class SteelChestBlockEntity extends BlockEntity {
    public static final int SIZE=48;
    private final SimpleContainer inventory=new SimpleContainer(SIZE) {
        @Override public void setChanged() { super.setChanged(); SteelChestBlockEntity.this.setChanged(); }
    };
    private final IItemHandler handler=new InvWrapper(inventory);
    public SteelChestBlockEntity(BlockPos pos,BlockState state) { super(OilRegistry.STEEL_CHEST_BE.get(),pos,state); }
    public SimpleContainer inventory() { return inventory; }
    public IItemHandler itemHandler() { return handler; }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup) {
        super.saveAdditional(tag,lookup); ContainerHelper.saveAllItems(tag,inventory.getItems(),lookup);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup) {
        super.loadAdditional(tag,lookup); NonNullList<ItemStack> items=NonNullList.withSize(SIZE,ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag,items,lookup); for(int i=0;i<SIZE;i++)inventory.setItem(i,items.get(i));
    }
}
