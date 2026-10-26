package dev.minetorio.core.handcraft;

import dev.minetorio.core.item.Inventory;
import dev.minetorio.core.recipe.Ingredient;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;
import dev.minetorio.core.recipe.RecipeCategory;

import java.util.*;
import java.util.function.Predicate;

/**
 * Factorio-style handcraft queue (speed 1.0). Raw inputs are removed from the inventory at enqueue time;
 * missing intermediates are crafted automatically as sub-steps ahead of the requested recipe.
 * Only the front entry progresses.
 */
public final class HandcraftQueue {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_TIMES = 1000;

    public record Entry(String recipeId, int times, double progress) {}

    private static final class Slot {
        String recipeId;
        int times;
        final List<String> steps = new ArrayList<>();
        final List<Boolean> top = new ArrayList<>();
        /** Per step: reserve items (earlier sub-crafts' outputs) that the step consumes when it finishes. */
        final List<Map<String, Integer>> use = new ArrayList<>();
        int stepIndex;
        int stepTicks;
        final Map<String, Integer> removed = new LinkedHashMap<>();
        final Map<String, Integer> reserve = new LinkedHashMap<>();
        final Map<String, Integer> pending = new LinkedHashMap<>();
    }

    /** Result of planning against a simulated inventory. */
    private static final class Plan {
        final List<String> steps = new ArrayList<>();
        final List<Boolean> top = new ArrayList<>();
        final List<Map<String, Integer>> use = new ArrayList<>();
        final Map<String, Integer> taken = new LinkedHashMap<>();
        final Map<String, Integer> spare = new LinkedHashMap<>();
    }

    private final RecipeBook book;
    private final List<Slot> slots = new ArrayList<>();

    public HandcraftQueue(RecipeBook book) { this.book = book; }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Slot s : slots) out.add(new Entry(s.recipeId, s.times, progressOf(s)));
        return out;
    }

    private double progressOf(Slot s) {
        if (s.stepIndex >= s.steps.size()) return 0;
        int ticks = book.get(s.steps.get(s.stepIndex)).map(Recipe::ticks).orElse(0);
        return ticks <= 0 ? 0 : Math.min(1.0, (double) s.stepTicks / ticks);
    }

    /** Largest n (capped at 1000) for which n crafts can be planned from the inventory. */
    public int maxCraftable(String recipeId, Inventory inv, Predicate<String> isUnlocked) {
        if (plan(recipeId, 1, inv, isUnlocked) == null) return 0;
        int lo = 1, hi = MAX_TIMES;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (plan(recipeId, mid, inv, isUnlocked) != null) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** Atomic: on failure the inventory is untouched and false is returned. */
    public boolean enqueue(String recipeId, int times, Inventory inv, Predicate<String> isUnlocked) {
        Plan p = plan(recipeId, times, inv, isUnlocked);
        if (p == null) return false;
        Slot s = new Slot();
        s.recipeId = recipeId;
        s.times = times;
        s.steps.addAll(p.steps);
        s.top.addAll(p.top);
        s.use.addAll(p.use);
        for (Map.Entry<String, Integer> e : p.taken.entrySet()) {
            int got = inv.remove(e.getKey(), e.getValue());
            if (got > 0) s.removed.put(e.getKey(), got);
        }
        slots.add(s);
        return true;
    }

    /**
     * Removes the entry and returns exactly the items it took from the inventory. Intermediates and progress are
     * discarded; outputs already delivered to the inventory stay there and their share is not refunded.
     */
    public void cancel(int entryIndex, Inventory inv) {
        if (entryIndex < 0 || entryIndex >= slots.size()) return;
        Slot s = slots.remove(entryIndex);
        for (Map.Entry<String, Integer> e : s.removed.entrySet()) inv.add(e.getKey(), e.getValue());
    }

    public void tick(Inventory inv) {
        if (slots.isEmpty()) return;
        Slot s = slots.get(0);
        flush(s.pending, inv);
        if (s.stepIndex < s.steps.size()) {
            Recipe r = book.get(s.steps.get(s.stepIndex)).orElseThrow();
            s.stepTicks++;
            if (s.stepTicks >= r.ticks()) {
                boolean isTop = s.top.get(s.stepIndex);
                for (Map.Entry<String, Integer> u : s.use.get(s.stepIndex).entrySet()) {
                    int left = s.reserve.getOrDefault(u.getKey(), 0) - u.getValue();
                    if (left > 0) s.reserve.put(u.getKey(), left); else s.reserve.remove(u.getKey());
                }
                Map<String, Integer> target = isTop ? s.pending : s.reserve;
                for (Ingredient o : r.outputs()) target.merge(o.item(), o.count(), Integer::sum);
                s.stepIndex++;
                s.stepTicks = 0;
                if (s.stepIndex >= s.steps.size()) {
                    for (Map.Entry<String, Integer> e : s.reserve.entrySet()) s.pending.merge(e.getKey(), e.getValue(), Integer::sum);
                    s.reserve.clear();
                }
                if (isTop || s.stepIndex >= s.steps.size()) flush(s.pending, inv);
            }
        }
        if (s.stepIndex >= s.steps.size() && s.pending.isEmpty()) slots.remove(0);
    }

    private static void flush(Map<String, Integer> pending, Inventory inv) {
        for (Iterator<Map.Entry<String, Integer>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Integer> e = it.next();
            int added = inv.add(e.getKey(), e.getValue());
            if (added >= e.getValue()) it.remove(); else e.setValue(e.getValue() - added);
        }
    }

    // ---- planning ----

    private Plan plan(String recipeId, int times, Inventory inv, Predicate<String> isUnlocked) {
        if (times <= 0 || times > MAX_TIMES) return null;
        Recipe r = book.get(recipeId).orElse(null);
        if (r == null || r.category() != RecipeCategory.CRAFTING || !isUnlocked.test(recipeId)) return null;
        Plan p = new Plan();
        for (int i = 0; i < times; i++) if (!planCraft(r, true, inv, isUnlocked, p, 0)) return null;
        return p;
    }

    private boolean planCraft(Recipe r, boolean isTop, Inventory inv, Predicate<String> isUnlocked, Plan p, int depth) {
        if (depth > MAX_DEPTH) return false;
        Map<String, Integer> use = new LinkedHashMap<>();
        for (Ingredient in : r.inputs()) {
            int need = in.count();
            int fromInv = Math.min(need, inv.count(in.item()) - p.taken.getOrDefault(in.item(), 0));
            if (fromInv > 0) {
                p.taken.merge(in.item(), fromInv, Integer::sum);
                need -= fromInv;
            }
            int fromSpare = Math.min(need, p.spare.getOrDefault(in.item(), 0));
            if (fromSpare > 0) {
                p.spare.merge(in.item(), -fromSpare, Integer::sum);
                use.merge(in.item(), fromSpare, Integer::sum);
                need -= fromSpare;
            }
            if (need == 0) continue;
            Recipe sub = firstCrafting(in.item(), isUnlocked);
            if (sub == null) return false;
            int perCraft = sub.outputs().stream().filter(o -> o.item().equals(in.item())).mapToInt(Ingredient::count).sum();
            int crafts = (need + perCraft - 1) / perCraft;
            for (int c = 0; c < crafts; c++) {
                if (!planCraft(sub, false, inv, isUnlocked, p, depth + 1)) return false;
            }
            // the sub-crafts added their outputs to spare; consume what this input needs
            p.spare.merge(in.item(), -need, Integer::sum);
            use.merge(in.item(), need, Integer::sum);
        }
        p.steps.add(r.id());
        p.top.add(isTop);
        p.use.add(use);
        if (!isTop) for (Ingredient o : r.outputs()) p.spare.merge(o.item(), o.count(), Integer::sum);
        return true;
    }

    private Recipe firstCrafting(String item, Predicate<String> isUnlocked) {
        for (Recipe r : book.producing(item))
            if (r.category() == RecipeCategory.CRAFTING && isUnlocked.test(r.id())) return r;
        return null;
    }

    // ---- persistence ----

    public Map<String, Object> save() {
        List<Object> list = new ArrayList<>();
        for (Slot s : slots) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recipe", s.recipeId);
            m.put("times", s.times);
            m.put("steps", new ArrayList<Object>(s.steps));
            List<Object> top = new ArrayList<>();
            for (boolean b : s.top) top.add(b ? 1 : 0);
            m.put("top", top);
            List<Object> use = new ArrayList<>();
            for (Map<String, Integer> u : s.use) use.add(new LinkedHashMap<String, Object>(u));
            m.put("use", use);
            m.put("stepIndex", s.stepIndex);
            m.put("stepTicks", s.stepTicks);
            m.put("removed", new LinkedHashMap<String, Object>(s.removed));
            m.put("reserve", new LinkedHashMap<String, Object>(s.reserve));
            m.put("pending", new LinkedHashMap<String, Object>(s.pending));
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entries", list);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static HandcraftQueue load(RecipeBook book, Map<String, Object> data) {
        HandcraftQueue q = new HandcraftQueue(book);
        if (!(data.get("entries") instanceof List<?> list)) return q;
        for (Object o : list) {
            Map<String, Object> m = (Map<String, Object>) o;
            Slot s = new Slot();
            s.recipeId = (String) m.get("recipe");
            s.times = ((Number) m.get("times")).intValue();
            for (Object id : (List<Object>) m.get("steps")) s.steps.add((String) id);
            for (Object b : (List<Object>) m.get("top")) s.top.add(((Number) b).intValue() != 0);
            for (Object u : (List<Object>) m.get("use")) {
                Map<String, Integer> um = new LinkedHashMap<>();
                readCounts((Map<String, Object>) u, um);
                s.use.add(um);
            }
            s.stepIndex = ((Number) m.get("stepIndex")).intValue();
            s.stepTicks = ((Number) m.get("stepTicks")).intValue();
            readCounts((Map<String, Object>) m.get("removed"), s.removed);
            readCounts((Map<String, Object>) m.get("reserve"), s.reserve);
            readCounts((Map<String, Object>) m.get("pending"), s.pending);
            q.slots.add(s);
        }
        return q;
    }

    private static void readCounts(Map<String, Object> src, Map<String, Integer> dst) {
        if (src == null) return;
        for (Map.Entry<String, Object> e : src.entrySet()) dst.put(e.getKey(), ((Number) e.getValue()).intValue());
    }
}
