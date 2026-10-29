package dev.minetorio.content.machineui;

import dev.minetorio.content.machine.*;
import dev.minetorio.content.fluid.*;
import dev.minetorio.content.power.*;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.content.map.RadarBlockEntity;
import dev.minetorio.content.oil.PumpjackBlockEntity;
import dev.minetorio.core.fluid.*;
import dev.minetorio.core.power.Accumulator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Server-authoritative shared machine menu; the client receives slots and small numeric telemetry. */
public final class MachineStatusMenu extends MachineMenu {
    public static final int DATA_COUNT=20, INVENTORY_X=210, INVENTORY_Y=32;
    public static final List<String> FLUIDS=FluidData.fluids().stream().map(FluidData.FluidType::id).toList();
    private final BlockPos pos;
    private final MachineUi.Kind kind;
    private final BlockEntity owner;
    public MachineStatusMenu(int id,Inventory inventory,BlockPos pos,MachineUi.Kind kind,MachineContainer slots,ContainerData data,BlockEntity owner) {
        super(MachineUi.STATUS.get(),id,slots,data,DATA_COUNT);this.pos=pos;this.kind=kind;this.owner=owner;
        for(int i=0;i<MachineUi.slotCount(kind);i++)addMachineSlot(new MachineSlot(slots,i,12+i*18,43));
        addPlayerInventory(inventory,INVENTORY_X,INVENTORY_Y);
    }
    public static MachineStatusMenu client(int id,Inventory inventory,RegistryFriendlyByteBuf buf) {
        BlockPos pos=buf.readBlockPos();MachineUi.Kind kind=buf.readEnum(MachineUi.Kind.class);
        return new MachineStatusMenu(id,inventory,pos,kind,MachineContainer.client(MachineUi.slotCount(kind),(slot,stack)->MachineUi.accepts(kind,slot,stack)?dev.minetorio.core.item.ItemStackSizes.limit(MachineItems.id(stack)):0),new SimpleContainerData(DATA_COUNT),null);
    }
    public BlockPos pos() {return pos;}
    public MachineUi.Kind kind() {return kind;}
    public int value(int i) {return data.get(i);}
    private long combined(int low,int high){return (data.get(low)&0xFFFFL)|((data.get(high)&0xFFFFL)<<16);}
    public long availableKw(){return combined(12,17);}
    public long demandKw(){return combined(15,18);}
    public long remainingOre(){return combined(9,19);}
    public double progress() {return value(0)/1000.0;}
    public double satisfaction() {return value(1)/1000.0;}
    public String fluid(int port) {int n=value(4+port*3);return n>0 && n<=FLUIDS.size()?FLUIDS.get(n-1):null;}
    public int fluidAmount(int port) {return value(2+port*3);}
    public int fluidCapacity(int port) {return value(3+port*3);}
    @Override public boolean clickMenuButton(net.minecraft.world.entity.player.Player player,int button) {
        if(owner==null || !stillValid(player) || player.isSpectator() || dev.minetorio.content.map.RemoteView.blocksTransfer(player))return false;
        if(kind==MachineUi.Kind.PIPE && button>=0 && button<6 && owner.getBlockState().getBlock() instanceof PipeBlock)
            return PipeBlock.toggle(player.level(),pos,net.minecraft.core.Direction.values()[button],player);
        return false;
    }
    private static void wide(int[] data,int low,int high,double number){long n=Math.clamp(Math.round(number),0,0xFFFFFFFFL);data[low]=(int)(n&0xFFFF);data[high]=(int)(n>>>16);}
    private static int scaled(double n) {return (int)Math.clamp(Math.round(n),0,32767);}
    private static int fluidId(String id) {return FLUIDS.indexOf(id)+1;}
    private static void fluid(int[] v,int port,FluidStore box) {
        if(box==null)return;v[2+port*3]=scaled(box.amount());v[3+port*3]=scaled(box.capacity());v[4+port*3]=fluidId(box.fluid());
    }
    public static ContainerData liveData(BlockEntity owner) {
        return new ContainerData() {
            private long tick=Long.MIN_VALUE;
            private int[] snapshot=new int[DATA_COUNT];
            public int getCount(){return DATA_COUNT;}
            public void set(int i,int value) {}
            public int get(int i) {long now=owner.getLevel()==null?0:owner.getLevel().getGameTime();if(now!=tick) {snapshot=telemetry(owner);tick=now;}return snapshot[i];}
        };
    }
    private static int[] telemetry(BlockEntity be) {
        int[] v=new int[DATA_COUNT];
        if(be instanceof MiningDrillBlockEntity drill) {
            v[0]=scaled(drill.progress()*1000);v[8]=drill.isWorking()?1:0;
            wide(v,9,19,drill.getLevel()==null?0:drill.areaRemaining(drill.getLevel()));
            v[10]=drill instanceof BurnerDrillBlockEntity?2:5;
        }
        if(be instanceof BurnerDrillBlockEntity b) {v[1]=b.burner().remainingJ()>0 || !b.burner().fuel().isEmpty()?1000:0;v[11]=scaled(b.burner().remainingJ()/1000.0);}
        if(be instanceof ElectricDrillBlockEntity d)v[1]=scaled(d.satisfaction()*1000);
        if(be instanceof LabBlockEntity lab) {v[0]=scaled(lab.lab().progress()*1000);v[1]=scaled(lab.satisfaction()*1000);v[8]=lab.isWorking()?1:0;}
        if(be instanceof BoilerBlockEntity b) {fluid(v,0,b.water());fluid(v,1,b.steam());v[1]=b.burner().remainingJ()>0 || !b.burner().fuel().isEmpty()?1000:0;v[8]=b.isWorking()?1:0;v[11]=scaled(b.burner().remainingJ()/1000.0);}
        if(be instanceof SteamEngineBlockEntity e) {fluid(v,0,e.steam());wide(v,12,17,e.producer().maxOutput()/50.0);v[8]=e.isWorking()?1:0;}
        if(be instanceof OffshorePumpBlockEntity p) {fluid(v,0,p.water());v[8]=p.hasWater()?1:0;}
        if(be instanceof PumpBlockEntity p) {fluid(v,0,p.box());v[1]=scaled(p.satisfaction()*1000);}
        if(be instanceof FluidNodeEntity n) {var node=n.fluidNode();v[2]=scaled(node.amount());v[3]=scaled(node.capacity());v[4]=fluidId(node.fluid());}
        if(be instanceof RadarBlockEntity r) {v[0]=scaled(r.logic().progress()*1000);v[1]=scaled(r.satisfaction()*1000);v[8]=r.isWorking()?1:0;}
        if(be instanceof SolarPanelBlockEntity s) {wide(v,12,17,s.output()/50.0);v[8]=s.isExposed()?1:0;v[13]=s.isConnected()?1:0;}
        if(be instanceof AccumulatorBlockEntity a) {v[0]=scaled(a.accumulator().level()*1000);v[11]=scaled(a.accumulator().energy()/1000.0);v[13]=a.isConnected()?1:0;v[14]=a.flow().ordinal();}
        if(be instanceof PowerPoleEntity p) {
            v[10]=p.connections().size();
            if(be.getLevel() instanceof ServerLevel server) {var grid=PowerGridManager.getIfPresent(server);var network=grid==null?null:grid.networkAtPole(be.getBlockPos());v[13]=grid==null?0:1;v[1]=grid==null?0:1000;if(network!=null) {v[1]=scaled(network.satisfaction()*1000);wide(v,12,17,network.lastGenerated()/50.0);wide(v,15,18,network.lastDemand()/50.0);v[13]=1;}}
        }
        if(be instanceof PumpjackBlockEntity p) {fluid(v,0,p.fluidBox(0));v[1]=scaled(p.satisfaction()*1000);v[8]=p.isWorking()?1:0;}
        if(be instanceof PipeBlockEntity && be.getBlockState().getBlock() instanceof PipeBlock)v[16]=PipeBlock.closedMask(be.getBlockState());
        return v;
    }
}
