package dev.minetorio.content.oil;

import dev.minetorio.content.fluid.FluidMachine;
import dev.minetorio.content.fluid.FluidMachineHelper;
import dev.minetorio.content.fluid.FluidGaugeSource;
import dev.minetorio.content.fluid.FluidNode;
import dev.minetorio.core.fluid.FluidGauge;
import dev.minetorio.content.inserter.MachineEndpoint;
import dev.minetorio.content.machine.MachineContainer;
import dev.minetorio.content.machine.MachineItemHandler;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.nbt.CopyableSettings;
import dev.minetorio.content.power.ElectricConsumerHelper;
import dev.minetorio.core.data.OilData;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.inserter.DropTarget;
import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.oil.OilMachine;
import dev.minetorio.core.oil.OilRecipe;
import dev.minetorio.core.recipe.Ingredient;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.neoforged.neoforge.items.IItemHandler;

public final class OilProcessingBlockEntity extends MultiblockControllerBlockEntity implements FluidMachine, FluidGaugeSource, MachineEndpoint, MenuProvider, CopyableSettings, dev.minetorio.content.alt.AltSource {
    public static final int INPUTS = 4, OUTPUTS = 4, SLOTS = INPUTS + OUTPUTS;
    private final OilMachine machine;
    private final FluidBox unused = new FluidBox(0);
    private final FluidMachineHelper fluids = new FluidMachineHelper(this);
    private final ElectricConsumerHelper power;
    private final Slots slots = new Slots();
    private final IItemHandler itemHandler = new MachineItemHandler(slots);
    private boolean working;
    private final dev.minetorio.core.alt.AltSync altSync = new dev.minetorio.core.alt.AltSync();
    private dev.minetorio.core.alt.AltInfo clientAlt = dev.minetorio.core.alt.AltInfo.NONE;
    private FluidGauge clientGauge = FluidGauge.EMPTY;
    private final ContainerData data = new ContainerData() {
        public int get(int i) {
            if(i>=3 && i<8)return (int)Math.round(fluidBox(i-3).amount());
            return switch(i) { case 0 -> OilMenu.RECIPES.indexOf(machine.recipe()); case 1 -> (int)(machine.progress()*1000); case 2 -> (int)(power.satisfaction()*1000);
                case 8 -> machine.recipe()==null?0:machine.isOutputFull()?3:machine.powerDemand()==0?1:power.satisfaction()<=0?2:4; default -> 0; };
        }
        public void set(int i, int value) {} public int getCount() { return 9; }
    };
    public OilProcessingBlockEntity(BlockPos pos, BlockState state) {
        super(OilRegistry.PROCESSING_BE.get(), pos, state);
        machine = new OilMachine(1, ((OilProcessingBlock)state.getBlock()).kind() == OilRecipe.MachineKind.REFINERY ? OilData.REFINERY_POWER_J : OilData.CHEMICAL_PLANT_POWER_J, 100);
        machine.onCraftCompleted(recipe->dev.minetorio.content.stats.Statistics.oilRecipe(getLevel(),recipe));
        power = new ElectricConsumerHelper(this, () -> machine.powerDemand(),
            () -> ElectricConsumerHelper.box(((OilProcessingBlock)getBlockState().getBlock()).shape().footprint(worldPosition, getBlockState().getValue(MultiblockControllerBlock.FACING))));
    }
    public OilMachine machine() { return machine; }
    public boolean isWorking() { return working; }
    public double satisfaction() { return power.satisfaction(); }
    public IItemHandler itemHandler() { return itemHandler; }
    public boolean select(OilRecipe recipe) {
        if (recipe == null || recipe.kind() != ((OilProcessingBlock)getBlockState().getBlock()).kind()) return false;
        boolean selected = machine.setRecipe(recipe); if (selected) { setChanged(); fluids.onLoad(); } return selected;
    }
    /** Player or blueprint recipe change: return items and recover incompatible residual fluids where possible. */
    @Nullable public Map<String,Integer> changeRecipe(OilRecipe recipe) {
        if(recipe==null || recipe.kind()!=((OilProcessingBlock)getBlockState().getBlock()).kind())return null;
        if(Objects.equals(machine.recipe(),recipe))return Map.of();
        fluids.returnIncompatibleFluids(recipe.fluidInputs().keySet(),recipe.fluidOutputs().keySet());
        Map<String,Integer> returned=machine.changeRecipe(recipe);
        setChanged(); fluids.onLoad(); return returned;
    }
    /** Explicit player action: discard residual liquids so another recipe can be selected. */
    public boolean flushFluids() {
        if (machine.isCrafting()) return false;
        OilMachine.State state=machine.snapshot();
        state.inputFluids().forEach((id,n)->machine.inputFluid(id).extract(n));
        state.outputFluids().forEach((id,n)->machine.outputFluid(id).extract(n));
        setChanged(); return true;
    }
    @Override public FluidBox fluidBox(int port) {
        String id=portFluid(machine.recipe(),port); if(id==null)return unused;
        return port < 2 ? machine.inputFluid(id) : machine.outputFluid(id);
    }
    @Nullable public static String portFluid(@Nullable OilRecipe r,int port) {
        if(r==null || port<0 || port>=5)return null;
        if(r.kind()==OilRecipe.MachineKind.REFINERY && r.id().equals("basic_oil_processing"))
            return switch(port) { case 1 -> "crude_oil"; case 4 -> "petroleum_gas"; default -> null; };
        List<String> ids=new ArrayList<>((port<2?r.fluidInputs():r.fluidOutputs()).keySet());
        int index=port<2?port:port-2;
        return index<ids.size()?ids.get(index):null;
    }
    @Override public void fluidChanged() { setChanged(); }
    @Override public FluidGauge fluidGauge() { return clientGauge; }
    @Override public FluidBox altBox() {
        int ports=((OilProcessingBlock)getBlockState().getBlock()).fluidPorts().size();
        for(int i=0;i<ports;i++) { FluidBox box=fluidBox(i); if(box.amount()>0)return box; }
        return null;
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, OilProcessingBlockEntity be) {
        boolean now = be.machine.powerDemand() > 0 && be.power.satisfaction() > 0;
        if (now) { be.machine.tick(be.power.satisfaction()); be.setChanged(); }
        if (now != be.working) { be.working=now; level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS); }
        dev.minetorio.content.alt.AltSource.tickAlt(be, be.altSync);
    }
    @Override public dev.minetorio.core.alt.AltInfo computeAlt() {
        OilRecipe recipe = machine.recipe();
        String icon = recipe == null ? null : !recipe.itemOutputs().isEmpty() ? recipe.itemOutputs().getFirst().item()
                : recipe.fluidOutputs().isEmpty() ? null : "fluid:" + recipe.fluidOutputs().keySet().iterator().next();
        return dev.minetorio.core.alt.AltRules.assembler(icon, machine.isOutputFull(), dev.minetorio.core.alt.AltRules.electric(power.satisfaction()));
    }
    @Override public dev.minetorio.core.alt.AltInfo altInfo() { return clientAlt; }
    @Override public void onLoad() { super.onLoad(); fluids.onLoad(); power.onLoad(); }
    @Override public void setRemoved() { super.setRemoved(); fluids.onRemoved(); power.onRemoved(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); fluids.onRemoved(); power.onRemoved(); }
    @Override public CompoundTag saveSettings() { CompoundTag t = new CompoundTag(); if (machine.recipe()!=null) t.putString("recipe", machine.recipe().id()); return t; }
    @Override public void loadSettings(CompoundTag t) {
        if(!t.contains("recipe"))return;
        Map<String,Integer> returned=changeRecipe(OilData.recipe(t.getString("recipe")));
        if(returned!=null && level!=null && !level.isClientSide())MachineItems.drop(level,worldPosition,returned);
    }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new OilMenu(id, inv, slots, data, worldPosition, this); }
    private String slotId(int slot) {
        OilRecipe r=machine.recipe(); if(r==null) return null;
        List<Ingredient> list=slot<INPUTS?r.itemInputs():r.itemOutputs(); int index=slot<INPUTS?slot:slot-INPUTS;
        return index>=0 && index<list.size()?list.get(index).item():null;
    }
    private final class Slots extends MachineContainer.View implements MachineItemHandler.Slots {
        public int getContainerSize() { return SLOTS; }
        @Nullable protected String itemIn(int slot) { String id=slotId(slot); return id!=null && countIn(slot)>0?id:null; }
        protected int countIn(int slot) { String id=slotId(slot); return id==null?0:(slot<INPUTS?machine.inputs():machine.outputs()).getOrDefault(id,0); }
        public boolean accepts(int slot,ItemStack stack) { return slot<INPUTS && MachineItems.isPlain(stack) && Objects.equals(slotId(slot),MachineItems.id(stack)); }
        public int room(int slot,ItemStack stack) { return room(slot,stack,false); }
        public int room(int slot,ItemStack stack,boolean automated) { return accepts(slot,stack)?machine.inputRoom(MachineItems.id(stack),automated):0; }
        public int insert(int slot,String id,int n,boolean automated) { if(!Objects.equals(slotId(slot),id)||slot>=INPUTS)return 0; int result=machine.insertInput(id,n,automated); if(result>0)setChanged(); return result; }
        protected int insert(int slot,String id,int n) { return insert(slot,id,n,false); }
        protected int extract(int slot,int n) { String id=slotId(slot); if(id==null)return 0; int v=slot<INPUTS?machine.extractInput(id,n):machine.extractOutput(id,n); if(v>0)setChanged(); return v; }
        public boolean isOutput(int slot) { return slot>=INPUTS && slot<SLOTS; }
        public int extractOutput(int slot,int n,boolean simulate) { return !isOutput(slot)?0:simulate?Math.min(n,countIn(slot)):extract(slot,n); }
        protected boolean usableBy(Player p) { return !isRemoved() && Container.stillValidBlockEntity(OilProcessingBlockEntity.this,p); }
        public void setChanged() { OilProcessingBlockEntity.this.setChanged(); }
    }
    @Override public PickupSource asSource() { return new PickupSource() {
        public Optional<String> peek(java.util.function.Predicate<String> wanted) { return machine.outputs().keySet().stream().filter(wanted).findFirst(); }
        public boolean take(String id) { boolean ok=machine.extractOutput(id,1)==1; if(ok)setChanged(); return ok; }
    }; }
    @Override public DropTarget asTarget() { return new DropTarget() {
        public boolean wants(String id) { return LaneItem.isPlain(id) && machine.inputRoom(id,true)>0; }
        public boolean accept(String id) { if(!wants(id))return false; boolean ok=machine.insertInput(id,1,true)==1; if(ok)setChanged(); return ok; }
    }; }
    @Override protected void dropContents(Level level,BlockPos pos) {
        Map<String,Integer> items=new HashMap<>(machine.inputs()); machine.outputs().forEach((id,n)->items.merge(id,n,Integer::sum));
        if(machine.isCrafting() && machine.recipe()!=null) machine.recipe().itemInputs().forEach(i->items.merge(i.item(),i.count(),Integer::sum));
        MachineItems.drop(level,pos,items);
    }
    private static CompoundTag ints(Map<String,Integer> values) { CompoundTag t=new CompoundTag(); values.forEach(t::putInt); return t; }
    private static CompoundTag doubles(Map<String,Double> values) { CompoundTag t=new CompoundTag(); values.forEach(t::putDouble); return t; }
    private static Map<String,Integer> readInts(CompoundTag t) { Map<String,Integer> m=new HashMap<>(); for(String k:t.getAllKeys())m.put(k,t.getInt(k)); return m; }
    private static Map<String,Double> readDoubles(CompoundTag t) { Map<String,Double> m=new HashMap<>(); for(String k:t.getAllKeys())m.put(k,t.getDouble(k)); return m; }
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider lookup) {
        super.saveAdditional(t,lookup); OilMachine.State s=machine.snapshot(); if(s.recipeId()!=null)t.putString("recipe",s.recipeId());
        t.put("inputs",ints(s.inputs())); t.put("outputs",ints(s.outputs())); t.put("inputFluids",doubles(s.inputFluids())); t.put("outputFluids",doubles(s.outputFluids())); t.putBoolean("crafting",s.crafting()); t.putDouble("progress",s.progress());
    }
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider lookup) {
        super.loadAdditional(t,lookup); if(t.contains("working"))working=t.getBoolean("working"); clientGauge=FluidNode.readGauge(t,clientGauge); clientAlt=dev.minetorio.content.alt.AltSource.read(t,clientAlt);
        if(t.contains("inputs")) machine.load(new OilMachine.State(t.contains("recipe")?t.getString("recipe"):null,readInts(t.getCompound("inputs")),readInts(t.getCompound("outputs")),readDoubles(t.getCompound("inputFluids")),readDoubles(t.getCompound("outputFluids")),t.getBoolean("crafting"),t.getDouble("progress")));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { CompoundTag t=new CompoundTag(); t.putBoolean("working",working); FluidNode.writeGauge(t,FluidGauge.of(altBox())); dev.minetorio.content.alt.AltSource.write(t,this,altSync); return t; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
