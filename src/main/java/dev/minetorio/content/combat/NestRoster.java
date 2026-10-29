package dev.minetorio.content.combat;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Учет потомков переживает выгрузку гнезда. Гибель кусаки освобождает лимит даже в другом чанке. */
public final class NestRoster extends SavedData {
    private final Map<UUID, Set<UUID>> nests = new HashMap<>();
    private NestRoster() {}
    public static NestRoster get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(NestRoster::new, NestRoster::load), "minetorio_nest_roster");
    }
    public Set<UUID> units(UUID nest, Set<UUID> initial) {
        if (!nests.containsKey(nest)) { nests.put(nest, new HashSet<>(initial)); setDirty(); }
        return Set.copyOf(nests.get(nest));
    }
    public void born(UUID nest, UUID child) {
        if (nests.computeIfAbsent(nest, ignored -> new HashSet<>()).add(child)) setDirty();
    }
    public void died(UUID nest, UUID child) {
        Set<UUID> units = nests.get(nest);
        if (units != null && units.remove(child)) setDirty();
    }
    public void destroyed(UUID nest) { if (nests.remove(nest) != null) setDirty(); }
    public static NestRoster load(CompoundTag tag, HolderLookup.Provider registries) {
        NestRoster roster = new NestRoster();
        for (Tag entry : tag.getList("Nests", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag)entry;
            Set<UUID> units = new HashSet<>();
            for (Tag id : record.getList("Children", Tag.TAG_INT_ARRAY)) units.add(NbtUtils.loadUUID(id));
            roster.nests.put(record.getUUID("Nest"), units);
        }
        return roster;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        nests.forEach((id, children) -> {
            CompoundTag record = new CompoundTag(); record.putUUID("Nest", id);
            ListTag units = new ListTag(); children.forEach(child -> units.add(NbtUtils.createUUID(child)));
            record.put("Children", units); list.add(record);
        });
        tag.put("Nests", list); return tag;
    }
}
