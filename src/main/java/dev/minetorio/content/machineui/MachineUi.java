package dev.minetorio.content.machineui;

import dev.minetorio.Minetorio;
import dev.minetorio.content.machine.*;
import dev.minetorio.content.power.*;
import dev.minetorio.content.fluid.*;
import dev.minetorio.content.map.RadarBlockEntity;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.content.oil.PumpjackBlockEntity;
import dev.minetorio.core.machine.Burner;
import dev.minetorio.core.data.Phase1Data;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Opens genuine machine slots over the same core buffers that ticking and inserters use. */
public final class MachineUi {
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,Minetorio.MODID);
    public static final DeferredHolder<MenuType<?>,MenuType<MachineStatusMenu>> STATUS=MENUS.register("machine_status",()->IMenuTypeExtension.create(MachineStatusMenu::client));
    public enum Kind {BURNER_DRILL,ELECTRIC_DRILL,LAB,BOILER,ENGINE,OFFSHORE,PUMP,TANK,RADAR,SOLAR,ACCUMULATOR,POLE,PIPE,PUMPJACK}
    private MachineUi() {}
    public static void register(IEventBus bus) {MENUS.register(bus);}
    public static Kind kind(BlockEntity be) {
        if(be instanceof BurnerDrillBlockEntity)return Kind.BURNER_DRILL;
        if(be instanceof ElectricDrillBlockEntity)return Kind.ELECTRIC_DRILL;
        if(be instanceof LabBlockEntity)return Kind.LAB;
        if(be instanceof BoilerBlockEntity)return Kind.BOILER;
        if(be instanceof SteamEngineBlockEntity)return Kind.ENGINE;
        if(be instanceof OffshorePumpBlockEntity)return Kind.OFFSHORE;
        if(be instanceof PumpBlockEntity)return Kind.PUMP;
        if(be instanceof StorageTankBlockEntity)return Kind.TANK;
        if(be instanceof RadarBlockEntity)return Kind.RADAR;
        if(be instanceof SolarPanelBlockEntity)return Kind.SOLAR;
        if(be instanceof AccumulatorBlockEntity)return Kind.ACCUMULATOR;
        if(be instanceof PowerPoleEntity)return Kind.POLE;
        if(be instanceof PipeBlockEntity)return Kind.PIPE;
        if(be instanceof PumpjackBlockEntity)return Kind.PUMPJACK;
        return null;
    }
    public static boolean open(Player player,BlockEntity owner) {
        Kind kind=kind(owner); if(kind==null)return false;
        if(player instanceof ServerPlayer server && !player.isSpectator() && !owner.isRemoved()) {
            server.openMenu(new SimpleMenuProvider((id,inventory,p)->menu(id,inventory,owner),owner.getBlockState().getBlock().getName()),
                buf->{buf.writeBlockPos(owner.getBlockPos());buf.writeEnum(kind);});
        }
        return true;
    }
    public static MachineStatusMenu menu(int id,Inventory inventory,BlockEntity owner) {
        Kind kind=kind(owner); if(kind==null)throw new IllegalArgumentException("Unsupported machine "+owner);
        return new MachineStatusMenu(id,inventory,owner.getBlockPos(),kind,liveContainer(owner),MachineStatusMenu.liveData(owner),owner);
    }
    public static int slotCount(Kind kind) {return switch(kind) {case BURNER_DRILL->2;case ELECTRIC_DRILL->1;case LAB->LabBlockEntity.PACKS.size();case BOILER->Phase1Data.fuelValues().size();default->0;};}
    public static String fuelAt(int slot) {return Phase1Data.fuelValues().keySet().stream().sorted().toList().get(slot);}
    public static boolean accepts(Kind kind,int slot,ItemStack stack) {
        if(!MachineItems.isPlain(stack))return false;String item=MachineItems.id(stack);
        return switch(kind) {case BURNER_DRILL->slot==0 && Phase1Data.fuelValues().containsKey(item);case BOILER->fuelAt(slot).equals(item);case LAB->slot<LabBlockEntity.PACKS.size() && LabBlockEntity.PACKS.get(slot).equals(item);default->false;};
    }
    private static Burner burner(BlockEntity be) {return be instanceof BurnerDrillBlockEntity b?b.burner():be instanceof BoilerBlockEntity b?b.burner():null;}
    private static MachineContainer liveContainer(BlockEntity owner) {
        Kind kind=kind(owner);
        return new MachineContainer.View() {
            public int getContainerSize() {return slotCount(kind);}
            protected String itemIn(int slot) {
                if(kind==Kind.LAB)return LabBlockEntity.PACKS.get(slot);
                if(kind==Kind.BOILER)return fuelAt(slot);
                if(owner instanceof MiningDrillBlockEntity drill && slot==(kind==Kind.BURNER_DRILL?1:0))return drill.output().held();
                Burner b=burner(owner);return b==null?null:b.fuel().keySet().stream().sorted().findFirst().orElse(null);
            }
            protected int countIn(int slot) {
                String item=itemIn(slot);if(item==null)return 0;
                if(owner instanceof LabBlockEntity lab)return lab.lab().packs().getOrDefault(item,0);
                if(owner instanceof MiningDrillBlockEntity && slot==(kind==Kind.BURNER_DRILL?1:0))return 1;
                return burner(owner).fuel().getOrDefault(item,0);
            }
            public boolean accepts(int slot,ItemStack stack) {return MachineUi.accepts(kind,slot,stack);}
            public int room(int slot,ItemStack stack) {
                if(!accepts(slot,stack))return 0;
                if(owner instanceof LabBlockEntity lab)return lab.lab().packRoom(MachineItems.id(stack),false);
                return burner(owner).fuelRoom(MachineItems.id(stack),false);
            }
            protected int insert(int slot,String item,int n) {
                if(owner instanceof LabBlockEntity lab)return lab.lab().insertPack(item,n,false);
                return burner(owner).insertFuel(item,n,false);
            }
            protected int extract(int slot,int n) {
                String item=itemIn(slot);if(item==null || n<=0)return 0;
                if(owner instanceof LabBlockEntity lab)return lab.lab().extractPack(item,n);
                if(owner instanceof MiningDrillBlockEntity drill && slot==(kind==Kind.BURNER_DRILL?1:0))return drill.output().take()==null?0:1;
                return burner(owner).extractFuel(item,n);
            }
            protected boolean usableBy(Player player) {return !owner.isRemoved() && owner.getLevel()==player.level() && owner.getLevel().getBlockEntity(owner.getBlockPos())==owner && player.distanceToSqr(owner.getBlockPos().getCenter())<=64;}
            public void setChanged() {owner.setChanged();}
        };
    }
}
