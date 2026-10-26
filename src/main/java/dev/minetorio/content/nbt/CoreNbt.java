package dev.minetorio.content.nbt;

import dev.minetorio.core.belt.BeltLane;
import dev.minetorio.core.belt.LaneItem;
import dev.minetorio.core.fluid.FluidBox;
import dev.minetorio.core.machine.Burner;
import dev.minetorio.core.machine.CraftingMachine;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeBook;
import dev.minetorio.core.research.LabLogic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Bridge between pure-core state and NBT. Generic maps may hold String, Integer, Long, Double,
 * List (elements of one kind) and Map&lt;String, ?&gt; values, nested arbitrarily; {@link #read} returns
 * the same Java types (mutable LinkedHashMap / ArrayList).
 */
public final class CoreNbt {
    private CoreNbt() {}

    public static CompoundTag write(Map<String, ?> map) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, ?> e : map.entrySet()) tag.put(e.getKey(), toTag(e.getValue()));
        return tag;
    }

    public static Map<String, Object> read(CompoundTag tag) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) out.put(key, fromTag(tag.get(key)));
        return out;
    }

    private static Tag toTag(Object v) {
        if (v instanceof String s) return StringTag.valueOf(s);
        if (v instanceof Integer i) return IntTag.valueOf(i);
        if (v instanceof Long l) return LongTag.valueOf(l);
        if (v instanceof Double d) return DoubleTag.valueOf(d);
        if (v instanceof Map<?, ?> m) {
            CompoundTag tag = new CompoundTag();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!(e.getKey() instanceof String key)) throw new IllegalArgumentException("non-string key: " + e.getKey());
                tag.put(key, toTag(e.getValue()));
            }
            return tag;
        }
        if (v instanceof List<?> list) {
            ListTag tag = new ListTag();
            for (Object o : list) {
                if (!tag.addTag(tag.size(), toTag(o))) throw new IllegalArgumentException("mixed list element types: " + list);
            }
            return tag;
        }
        throw new IllegalArgumentException("unsupported value: " + v);
    }

    private static Object fromTag(Tag t) {
        return switch (t) {
            case StringTag s -> s.getAsString();
            case IntTag i -> i.getAsInt();
            case LongTag l -> l.getAsLong();
            case DoubleTag d -> d.getAsDouble();
            case CompoundTag c -> read(c);
            case ListTag l -> {
                List<Object> out = new ArrayList<>(l.size());
                for (Tag e : l) out.add(fromTag(e));
                yield out;
            }
            default -> throw new IllegalArgumentException("unsupported tag: " + t);
        };
    }

    public static CompoundTag writeCounts(Map<String, Integer> counts) {
        CompoundTag tag = new CompoundTag();
        counts.forEach(tag::putInt);
        return tag;
    }

    public static Map<String, Integer> readCounts(CompoundTag tag) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) out.put(key, tag.getInt(key));
        return out;
    }

    public static CompoundTag writeLane(BeltLane lane) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("length", lane.length());
        ListTag items = new ListTag();
        for (LaneItem it : lane.items()) {
            CompoundTag e = new CompoundTag();
            e.putString("item", it.item());
            e.putDouble("pos", it.pos());
            items.add(e);
        }
        tag.put("items", items);
        return tag;
    }

    public static BeltLane readLane(CompoundTag tag) {
        BeltLane lane = new BeltLane(tag.getDouble("length"));
        for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            lane.insertAt(e.getString("item"), e.getDouble("pos"));
        }
        return lane;
    }

    public static CompoundTag writeFluid(FluidBox box) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("capacity", box.capacity());
        if (box.fluid() != null) {
            tag.putString("fluid", box.fluid());
            tag.putDouble("amount", box.amount());
        }
        return tag;
    }

    public static FluidBox readFluid(CompoundTag tag) {
        FluidBox box = new FluidBox(tag.getDouble("capacity"));
        if (tag.contains("fluid", Tag.TAG_STRING)) box.insert(tag.getString("fluid"), tag.getDouble("amount"));
        return box;
    }

    /** Restores {@link #writeFluid} data into an existing box (its own capacity is kept). */
    public static void loadFluidInto(FluidBox box, CompoundTag tag) {
        box.extract(box.amount());
        if (tag.contains("fluid", Tag.TAG_STRING)) box.insert(tag.getString("fluid"), tag.getDouble("amount"));
    }

    public static CompoundTag writeMachine(CraftingMachine machine) {
        CompoundTag tag = new CompoundTag();
        if (machine.recipe() != null) tag.putString("recipe", machine.recipe().id());
        tag.put("inputs", writeCounts(machine.inputs()));
        tag.put("outputs", writeCounts(machine.outputs()));
        tag.put("pending", writeCounts(machine.pendingReturn()));
        tag.putBoolean("crafting", machine.isCrafting());
        if (machine.isCrafting()) {
            Map<String, Integer> consumed = new LinkedHashMap<>();
            for (var in : machine.recipe().inputs()) consumed.merge(in.item(), in.count(), Integer::sum);
            tag.put("crafting_inputs", writeCounts(consumed));
        }
        tag.putDouble("progress", machine.progress());
        return tag;
    }

    /**
     * Restores {@link #writeMachine} data. A recipe id unknown to {@code book} loads as no recipe:
     * stored inputs are kept and the inputs consumed by a craft in progress go to the pending
     * return, so both are handed back by {@code drainInputs} (or dropped when the machine breaks).
     */
    public static void loadMachineInto(CraftingMachine machine, CompoundTag tag, RecipeBook book) {
        Recipe recipe = tag.contains("recipe", Tag.TAG_STRING) ? book.get(tag.getString("recipe")).orElse(null) : null;
        boolean crafting = tag.getBoolean("crafting");
        Map<String, Integer> pending = readCounts(tag.getCompound("pending"));
        if (recipe == null && crafting) readCounts(tag.getCompound("crafting_inputs")).forEach((k, v) -> pending.merge(k, v, Integer::sum));
        machine.load(recipe, readCounts(tag.getCompound("inputs")), readCounts(tag.getCompound("outputs")),
                pending, crafting, tag.getDouble("progress"));
    }

    public static CompoundTag writeBurner(Burner burner) {
        CompoundTag tag = new CompoundTag();
        tag.put("fuel", writeCounts(burner.fuel()));
        tag.putLong("remaining", burner.remainingJ());
        return tag;
    }

    public static void loadBurnerInto(Burner burner, CompoundTag tag) {
        burner.load(readCounts(tag.getCompound("fuel")), tag.getLong("remaining"));
    }

    public static CompoundTag writeLab(LabLogic lab) {
        CompoundTag tag = new CompoundTag();
        tag.put("packs", writeCounts(lab.packs()));
        if (lab.unitTech() != null) {
            tag.putString("unit_tech", lab.unitTech());
            tag.put("unit_packs", writeCounts(lab.unitPacks()));
            tag.putDouble("progress", lab.progress());
        }
        return tag;
    }

    public static void loadLabInto(LabLogic lab, CompoundTag tag) {
        String unit = tag.contains("unit_tech", Tag.TAG_STRING) ? tag.getString("unit_tech") : null;
        lab.load(readCounts(tag.getCompound("packs")), unit, readCounts(tag.getCompound("unit_packs")), tag.getDouble("progress"));
    }
}
