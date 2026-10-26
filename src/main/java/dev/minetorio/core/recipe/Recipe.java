package dev.minetorio.core.recipe;

import java.util.List;

public record Recipe(String id, List<Ingredient> inputs, List<Ingredient> outputs, int ticks, RecipeCategory category) {
    public Recipe {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
}
