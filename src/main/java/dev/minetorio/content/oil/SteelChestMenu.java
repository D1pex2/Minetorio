package dev.minetorio.content.oil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SteelChestMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    public SteelChestMenu(int id,Inventory inv,BlockPos pos,Container chest) {
        super(OilRegistry.STEEL_CHEST_MENU.get(),id); this.pos=pos;
        checkContainerSize(chest,SteelChestBlockEntity.SIZE);
        for(int row=0;row<6;row++)for(int col=0;col<8;col++) addSlot(new Slot(chest,col+row*8,17+col*18,18+row*18));
        var main=dev.minetorio.content.character.CharacterInventory.main(inv);
        for(int row=0;row<8;row++)for(int col=0;col<10;col++)addSlot(new Slot(main,col+row*10,8+col*18,140+row*18));
    }
    public static SteelChestMenu client(int id,Inventory inv,RegistryFriendlyByteBuf extra) { return new SteelChestMenu(id,inv,extra.readBlockPos(),new SimpleContainer(SteelChestBlockEntity.SIZE)); }
    @Override public boolean stillValid(Player p) { return p.level().getBlockEntity(pos) instanceof SteelChestBlockEntity && p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64; }
    @Override public void clicked(int slot,int button,net.minecraft.world.inventory.ClickType click,Player p) {
        if(!dev.minetorio.content.map.RemoteView.blocksTransfer(p))super.clicked(slot,button,click,p);
    }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(index<0 || index>=slots.size() || dev.minetorio.content.map.RemoteView.blocksTransfer(p))return ItemStack.EMPTY;
        Slot slot=slots.get(index); if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy(); int n=SteelChestBlockEntity.SIZE;
        if(index<n ? !moveItemStackTo(stack,n,slots.size(),true) : !moveItemStackTo(stack,0,n,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); return copy;
    }
}
