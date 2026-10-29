package dev.minetorio.content.stats;

import dev.minetorio.core.stats.ProductionLedger;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.oil.OilRecipe;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** World-shared production events persisted in the Overworld, independent of GUI visits. */
public final class Statistics extends SavedData {
    public static final String NAME="minetorio_production";
    private final ProductionLedger ledger=new ProductionLedger();
    public ProductionLedger ledger(){return ledger;}
    public static Statistics get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Statistics::new,Statistics::load),NAME);}
    public static void record(Level level,String id,double produced,double consumed) {
        if(!(level instanceof ServerLevel server))return;
        Statistics data=get(server.getServer());data.ledger.record(server.getServer().overworld().getGameTime(),id,produced,consumed);data.setDirty();
    }
    public static void recipe(Level level,Recipe recipe) {recipe.inputs().forEach(i->record(level,i.item(),0,i.count()));recipe.outputs().forEach(i->record(level,i.item(),i.count(),0));}
    public static void oilRecipe(Level level,OilRecipe recipe) {recipe.itemInputs().forEach(i->record(level,i.item(),0,i.count()));recipe.itemOutputs().forEach(i->record(level,i.item(),i.count(),0));recipe.fluidInputs().forEach((id,n)->record(level,"fluid:"+id,0,n));recipe.fluidOutputs().forEach((id,n)->record(level,"fluid:"+id,n,0));}
    private static ListTag writeCounts(Map<String,ProductionLedger.Counts> counts) {
        ListTag list=new ListTag();counts.forEach((id,c)->{CompoundTag tag=new CompoundTag();tag.putString("id",id);tag.putDouble("produced",c.produced());tag.putDouble("consumed",c.consumed());list.add(tag);});return list;
    }
    private static Map<String,ProductionLedger.Counts> readCounts(ListTag list) {
        Map<String,ProductionLedger.Counts> counts=new HashMap<>();for(int i=0;i<Math.min(list.size(),ProductionLedger.MAX_KEYS);i++){var tag=list.getCompound(i);String id=tag.getString("id");if(!id.isBlank() && id.length()<=128)counts.put(id,new ProductionLedger.Counts(tag.getDouble("produced"),tag.getDouble("consumed")));}return counts;
    }
    public static Statistics load(CompoundTag root,HolderLookup.Provider registries) {
        Statistics data=new Statistics();Map<Long,Map<String,ProductionLedger.Counts>> buckets=new HashMap<>();ListTag list=root.getList("buckets",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(list.size(),ProductionLedger.MAX_BUCKETS);i++){var tag=list.getCompound(i);buckets.put(tag.getLong("tick"),readCounts(tag.getList("counts",Tag.TAG_COMPOUND)));}
        data.ledger.restore(readCounts(root.getList("totals",Tag.TAG_COMPOUND)),buckets);return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries) {
        root.put("totals",writeCounts(ledger.totals()));ListTag list=new ListTag();ledger.buckets().forEach((tick,counts)->{var tag=new CompoundTag();tag.putLong("tick",tick);tag.put("counts",writeCounts(counts));list.add(tag);});root.put("buckets",list);return root;
    }
}
