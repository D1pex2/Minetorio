package dev.minetorio.content.oil;

import dev.minetorio.content.machine.MachineContainer;
import dev.minetorio.content.machine.MachineMenu;
import dev.minetorio.content.machine.MachineSlot;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.data.OilData;
import dev.minetorio.core.oil.OilRecipe;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public final class OilMenu extends MachineMenu {
    public static final List<OilRecipe> RECIPES = OilData.recipes();
    private final BlockPos pos;
    private final OilProcessingBlockEntity owner;
    public OilMenu(int id, Inventory inv, MachineContainer container, ContainerData data, BlockPos pos, OilProcessingBlockEntity owner) {
        super(OilRegistry.PROCESSING_MENU.get(),id,container,data,9); this.pos=pos; this.owner=owner;
        for(int i=0;i<OilProcessingBlockEntity.INPUTS;i++) addMachineSlot(new MachineSlot(container,i,14+i*18,121));
        for(int i=0;i<OilProcessingBlockEntity.OUTPUTS;i++) addMachineSlot(new MachineSlot(container,i+OilProcessingBlockEntity.INPUTS,148+i*18,121));
        addPlayerInventory(inv,254,32);
    }
    public static OilMenu client(int id, Inventory inv, RegistryFriendlyByteBuf extra) {
        BlockPos pos=extra.readBlockPos(); SimpleContainerData data=new SimpleContainerData(9);
        MachineContainer c=MachineContainer.client(OilProcessingBlockEntity.SLOTS,(slot,stack)-> {
            int index=data.get(0); if(index<0 || index>=RECIPES.size() || slot>=OilProcessingBlockEntity.INPUTS || !MachineItems.isPlain(stack))return 0;
            var items=RECIPES.get(index).itemInputs(); return slot<items.size() && items.get(slot).item().equals(MachineItems.id(stack))?dev.minetorio.core.item.ItemStackSizes.limit(MachineItems.id(stack)):0;
        });
        return new OilMenu(id,inv,c,data,pos,null);
    }
    public BlockPos pos() { return pos; }
    public int selected() { return data.get(0); }
    public float progress() { return data.get(1)/1000f; }
    public float satisfaction() { return data.get(2)/1000f; }
    public int fluidAmount(int port) { return port>=0 && port<5?data.get(3+port):0; }
    public int status() { return data.get(8); }
    @Override public boolean clickMenuButton(Player player,int index) {
        if(owner==null || !stillValid(player) || player.isSpectator())return false;
        if(index == -2) return owner.flushFluids();
        if(index<0 || index>=RECIPES.size())return false;
        OilRecipe recipe=RECIPES.get(index);
        if(!RecipeLocks.isUnlocked(player.getServer(),recipe.id()))return false;
        var returned=owner.changeRecipe(recipe); if(returned==null)return false;
        MachineItems.give(player,returned); return true;
    }
}
