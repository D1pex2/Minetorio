package dev.minetorio.core.research;

import java.util.*;

/** Researched set, current technology and per-technology partial progress (kept when switching). */
public final class ResearchState {
    private final TechTree tree;
    private final Set<String> startRecipes;
    private final Set<String> researched = new LinkedHashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private String current;

    public ResearchState(TechTree tree, Set<String> startRecipes) {
        this.tree = tree;
        this.startRecipes = Set.copyOf(startRecipes);
    }

    public boolean isRecipeUnlocked(String recipeId) {
        if (startRecipes.contains(recipeId)) return true;
        for (String id : researched) {
            if (tree.get(id).map(t -> t.unlocksRecipes().contains(recipeId)).orElse(false)) return true;
        }
        return false;
    }

    public boolean isResearched(String techId) { return researched.contains(techId); }

    public boolean canResearch(String techId) {
        Optional<Technology> t = tree.get(techId);
        return t.isPresent() && !researched.contains(techId) && researched.containsAll(t.get().prerequisites());
    }

    public void setCurrent(String techId) {
        if (!canResearch(techId)) throw new IllegalStateException("Cannot research " + techId);
        current = techId;
    }

    public Optional<Technology> current() { return Optional.ofNullable(current).flatMap(tree::get); }

    public int unitsDone() { return current == null ? 0 : progress.getOrDefault(current, 0); }

    /** Units done per partially-researched technology (including the current one). */
    public Map<String, Integer> progressById() {
        Map<String, Integer> m = new HashMap<>();
        progress.forEach((k, v) -> { if (v > 0) m.put(k, v); });
        return m;
    }

    /** Adds one finished unit to the current technology; no-op without a current one. */
    public void completeUnit() {
        Optional<Technology> t = current();
        if (t.isEmpty()) return;
        int done = progress.merge(current, 1, Integer::sum);
        if (done >= t.get().units()) {
            researched.add(current);
            progress.remove(current);
            current = null;
        }
    }

    public Set<String> researched() { return Collections.unmodifiableSet(researched); }

    public void load(Set<String> researchedIds, String currentId, Map<String, Integer> progressById) {
        researched.clear();
        researched.addAll(researchedIds);
        progress.clear();
        progress.putAll(progressById);
        current = currentId;
    }
}
