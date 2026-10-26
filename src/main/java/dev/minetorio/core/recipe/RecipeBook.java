package dev.minetorio.core.recipe;

import java.util.*;

public final class RecipeBook {
    private final Map<String, Recipe> byId = new LinkedHashMap<>();

    public RecipeBook(Collection<Recipe> recipes) {
        for (Recipe r : recipes)
            if (byId.putIfAbsent(r.id(), r) != null)
                throw new IllegalArgumentException("duplicate recipe id: " + r.id());
    }

    public Optional<Recipe> get(String id) { return Optional.ofNullable(byId.get(id)); }

    public List<Recipe> producing(String item) {
        return byId.values().stream()
                .filter(r -> r.outputs().stream().anyMatch(o -> o.item().equals(item))).toList();
    }

    /** First smelting recipe whose single input is the given item. */
    public Optional<Recipe> smeltingFor(String inputItem) {
        return byId.values().stream()
                .filter(r -> r.category() == RecipeCategory.SMELTING
                        && r.inputs().size() == 1 && r.inputs().get(0).item().equals(inputItem))
                .findFirst();
    }

    public Collection<Recipe> all() { return Collections.unmodifiableCollection(byId.values()); }
}
