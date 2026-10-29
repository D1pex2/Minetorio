package dev.minetorio.core.oil;

import dev.minetorio.core.recipe.Ingredient;
import java.util.*;

/** Mixed recipes kept separate from hand crafting. Fluid ids are unnamespaced; item ids are namespaced. */
public record OilRecipe(String id, List<Ingredient> itemInputs, List<Ingredient> itemOutputs,
                        Map<String, Double> fluidInputs, Map<String, Double> fluidOutputs,
                        int ticks, MachineKind kind) {
    public enum MachineKind { REFINERY, CHEMICAL_PLANT, ASSEMBLER }
    public OilRecipe {
        Objects.requireNonNull(id); Objects.requireNonNull(kind);
        itemInputs = List.copyOf(itemInputs); itemOutputs = List.copyOf(itemOutputs);
        fluidInputs = ordered(fluidInputs); fluidOutputs = ordered(fluidOutputs);
        if (ticks <= 0 || itemInputs.stream().anyMatch(i -> i.count() <= 0)
                || itemOutputs.stream().anyMatch(i -> i.count() <= 0)) throw new IllegalArgumentException("Invalid recipe");
    }
    private static Map<String,Double> ordered(Map<String,Double> map) {
        map.forEach((id, amount) -> { if(id == null || !Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Invalid fluid ingredient"); });
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
