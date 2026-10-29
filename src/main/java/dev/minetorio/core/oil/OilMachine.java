package dev.minetorio.core.oil;

import dev.minetorio.core.data.OilData;
import dev.minetorio.core.fluid.*;
import dev.minetorio.core.recipe.Ingredient;
import java.util.*;

/** Atomic mixed crafting. A blocked fraction stops the complete batch, never discards a product.
 * One stable tank per fluid and direction keeps attached port references valid across recipe changes.
 */
public final class OilMachine {
    public static final double TANK_CAPACITY = 1000;
    private static final double EPS = 1e-9;
    private final double speed;
    private final long powerJ;
    private final double outputCapacity;
    private final Map<String,Integer> inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
    private final Map<String,FluidBox> inputFluids = new LinkedHashMap<>(), outputFluids = new LinkedHashMap<>();
    private OilRecipe recipe;
    private boolean crafting;
    private double progress;

    public record State(String recipeId, Map<String,Integer> inputs, Map<String,Integer> outputs,
                        Map<String,Double> inputFluids, Map<String,Double> outputFluids,
                        boolean crafting, double progress) {
        public State { inputs=Map.copyOf(inputs); outputs=Map.copyOf(outputs); inputFluids=Map.copyOf(inputFluids); outputFluids=Map.copyOf(outputFluids); }
    }
    public OilMachine(double speed, long powerJ) {
        this(speed,powerJ,TANK_CAPACITY);
    }
    /** Factorio 1.1 refinery/chemical outputs are 100 units, assembler outputs are 1000. */
    public OilMachine(double speed, long powerJ,double outputCapacity) {
        if(!Double.isFinite(speed) || speed <= 0 || powerJ < 0 || !Double.isFinite(outputCapacity) || outputCapacity<=0) throw new IllegalArgumentException();
        this.speed=speed; this.powerJ=powerJ; this.outputCapacity=outputCapacity;
        for(var f:FluidData.fluids()) { inputFluids.put(f.id(), new FluidBox(TANK_CAPACITY,f.id())); outputFluids.put(f.id(), new FluidBox(outputCapacity,f.id())); }
    }
    public OilRecipe recipe() { return recipe; }
    public FluidBox inputFluid(String id) { return inputFluids.get(id); }
    public FluidBox outputFluid(String id) { return outputFluids.get(id); }
    public Map<String,Integer> inputs() { return Map.copyOf(inputs); }
    public Map<String,Integer> outputs() { return Map.copyOf(outputs); }
    public double progress() { return progress; }
    public boolean isCrafting() { return crafting; }
    public boolean setRecipe(OilRecipe next) {
        if(Objects.equals(recipe,next)) return true;
        if(!canSetRecipe(next) || !inputs.isEmpty() || !outputs.isEmpty()) return false;
        recipe=next; progress=0; return true;
    }
    /** Preflight before returning stored items to the player; fluid and in-progress batches remain protected. */
    public boolean canSetRecipe(OilRecipe next) {
        return !crafting
                && inputFluids.entrySet().stream().noneMatch(e->e.getValue().amount()>EPS && (next==null || !next.fluidInputs().containsKey(e.getKey())))
                && outputFluids.entrySet().stream().noneMatch(e->e.getValue().amount()>EPS && (next==null || !next.fluidOutputs().containsKey(e.getKey())));
    }
    /**
     * Explicit player recipe change, matching Factorio's return_ingredients_on_change (true since
     * 0.18.26). Returns item inputs, unfinished craft item ingredients, and finished item outputs.
     * Consumed fluids are not refunded. Compatible residual fluids stay; incompatible residuals
     * are cleared. The content layer first tries to return those residuals to connected pipes.
     * https://wiki.factorio.com/Version_history/0.18.0#0.18.26
     */
    public Map<String,Integer> changeRecipe(OilRecipe next) {
        if(Objects.equals(recipe,next)) return Map.of();
        Map<String,Integer> returned=new LinkedHashMap<>(inputs);
        outputs.forEach((id,n)->returned.merge(id,n,Integer::sum));
        if(crafting && recipe!=null) recipe.itemInputs().forEach(i->returned.merge(i.item(),i.count(),Integer::sum));
        inputs.clear(); outputs.clear();
        inputFluids.forEach((id,b)->{if(next==null || !next.fluidInputs().containsKey(id))b.extract(b.amount());});
        outputFluids.forEach((id,b)->{if(next==null || !next.fluidOutputs().containsKey(id))b.extract(b.amount());});
        crafting=false; progress=0; recipe=next;
        return returned;
    }
    public int inputRoom(String item, boolean automated) {
        if(recipe==null) return 0;
        int need=recipe.itemInputs().stream().filter(i->i.item().equals(item)).mapToInt(Ingredient::count).sum();
        return need==0?0:Math.max(0,(automated?need*2:Math.max(64,need))-inputs.getOrDefault(item,0));
    }
    public int insertInput(String item,int count,boolean automated) {
        int n=Math.max(0,Math.min(count,inputRoom(item,automated)));
        if(n>0) inputs.merge(item,n,Integer::sum); return n;
    }
    public int extractInput(String item,int max) { return extract(inputs,item,max); }
    public int extractOutput(String item,int max) { return extract(outputs,item,max); }
    private static int extract(Map<String,Integer> map,String item,int max) {
        int have=map.getOrDefault(item,0), n=Math.max(0,Math.min(max,have));
        if(n>0) { if(n==have) map.remove(item); else map.put(item,have-n); } return n;
    }
    private boolean hasInputs() {
        return recipe!=null && recipe.itemInputs().stream().allMatch(i->inputs.getOrDefault(i.item(),0)>=i.count())
                && recipe.fluidInputs().entrySet().stream().allMatch(e->has(inputFluids.get(e.getKey()),e.getKey(),e.getValue()));
    }
    private static boolean has(FluidBox box,String id,double amount) { return box!=null && id.equals(box.fluid()) && box.amount()+EPS>=amount; }
    private boolean outputRoom() {
        return recipe!=null && recipe.itemOutputs().stream().allMatch(i->outputs.getOrDefault(i.item(),0)+i.count()<=64)
                && recipe.fluidOutputs().entrySet().stream().allMatch(e->{var b=outputFluids.get(e.getKey());return b!=null && (b.fluid()==null || b.fluid().equals(e.getKey())) && b.room()+EPS>=e.getValue();});
    }
    public boolean isOutputFull() { return recipe!=null && !outputRoom(); }
    public long powerDemand() { return recipe!=null && outputRoom() && (crafting || hasInputs()) ? powerJ : 0; }
    public void tick(double satisfaction) {
        if(recipe==null || !Double.isFinite(satisfaction) || satisfaction<=0) return;
        if(!crafting) {
            if(!hasInputs() || !outputRoom()) { progress=0; return; }
            recipe.itemInputs().forEach(i->extract(inputs,i.item(),i.count()));
            recipe.fluidInputs().forEach((id,n)->inputFluids.get(id).extract(n));
            crafting=true;
        }
        if(!outputRoom()) return;
        progress+=Math.min(1,satisfaction)*speed/recipe.ticks();
        if(progress>=1-EPS) {
            recipe.itemOutputs().forEach(i->outputs.merge(i.item(),i.count(),Integer::sum));
            recipe.fluidOutputs().forEach((id,n)->outputFluids.get(id).insert(id,n));
            crafting=false; progress=Math.max(0,progress-1);
        }
    }
    public State snapshot() { return new State(recipe==null?null:recipe.id(),inputs,outputs,amounts(inputFluids),amounts(outputFluids),crafting,progress); }
    private static Map<String,Double> amounts(Map<String,FluidBox> boxes) {
        Map<String,Double> result=new LinkedHashMap<>(); boxes.forEach((id,b)->{if(b.amount()>0) result.put(id,b.amount());}); return result;
    }
    public void load(State state) {
        OilRecipe restored=state.recipeId()==null?null:OilData.recipe(state.recipeId());
        if(state.recipeId()!=null && restored==null) throw new IllegalArgumentException("Unknown recipe: "+state.recipeId());
        validateFluids(state.inputFluids(),TANK_CAPACITY); validateFluids(state.outputFluids(),outputCapacity);
        if(state.inputs().values().stream().anyMatch(n->n<0) || state.outputs().values().stream().anyMatch(n->n<0)
                || !Double.isFinite(state.progress()) || state.progress()<0 || state.progress()>1 || state.crafting() && restored==null) throw new IllegalArgumentException("Invalid machine state");
        recipe=restored; inputs.clear(); inputs.putAll(state.inputs()); outputs.clear(); outputs.putAll(state.outputs());
        restoreFluids(inputFluids,state.inputFluids()); restoreFluids(outputFluids,state.outputFluids());
        crafting=state.crafting(); progress=state.progress();
    }
    private static void validateFluids(Map<String,Double> amounts,double capacity) {
        amounts.forEach((id,n)->{if(!FluidData.isFluid(id) || !Double.isFinite(n) || n<0 || n>capacity) throw new IllegalArgumentException("Invalid fluid state");});
    }
    private static void restoreFluids(Map<String,FluidBox> boxes,Map<String,Double> amounts) {
        boxes.forEach((id,b)->{b.extract(b.amount()); b.insert(id,amounts.getOrDefault(id,0d));});
    }
}
