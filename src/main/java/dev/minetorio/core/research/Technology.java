package dev.minetorio.core.research;

import java.util.List;
import java.util.Map;

/** One technology: needs {@code units} research units, each costing {@code packsPerUnit} and {@code ticksPerUnit} lab time. */
public record Technology(String id, List<String> prerequisites, Map<String, Integer> packsPerUnit,
                         int units, int ticksPerUnit, List<String> unlocksRecipes) {
    public Technology {
        prerequisites = List.copyOf(prerequisites);
        packsPerUnit = Map.copyOf(packsPerUnit);
        unlocksRecipes = List.copyOf(unlocksRecipes);
    }
}
