package dev.minetorio.content.oilmap;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Oil field totals remain available when the source chunks unload. Updated only by actual sources. */
public final class OilMapData extends SavedData {
    private static final String NAME = "minetorio_oil_fields";
    public record Well(long position, long field, double yield) {}
    private final Map<Long, Well> wells = new LinkedHashMap<>();
    private final Map<Long, Well> columns = new java.util.HashMap<>();
    private record Field(double total, int count) {}
    private final Map<Long, Field> fields = new java.util.HashMap<>();

    private static long column(long position) {
        BlockPos pos = BlockPos.of(position);
        return dev.minetorio.core.map.ChunkKeys.of(pos.getX(), pos.getZ());
    }

    private void adjust(Well well, int sign) {
        fields.compute(well.field(), (key, old) -> {
            int count = (old == null ? 0 : old.count()) + sign;
            return count == 0 ? null : new Field((old == null ? 0 : old.total()) + sign * well.yield(), count);
        });
    }

    private boolean put(Well next) {
        Well old = wells.put(next.position(), next);
        if (next.equals(old)) return false;
        if (old != null) adjust(old, -1);
        columns.put(column(next.position()), next);
        adjust(next, 1);
        return true;
    }

    public static OilMapData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(OilMapData::new, OilMapData::load), NAME);
    }

    public static void update(ServerLevel level, BlockPos pos, long field, double yield) {
        if (!Double.isFinite(yield) || yield < 0) return;
        OilMapData data = get(level);
        Well next = new Well(pos.asLong(), field, yield);
        if (data.put(next)) data.setDirty();
    }

    public Well at(int x, int z) {
        return columns.get(dev.minetorio.core.map.ChunkKeys.of(x, z));
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        OilMapData data = get(level);
        Well old = data.wells.remove(pos.asLong());
        if (old != null) {
            data.columns.remove(column(old.position()));
            data.adjust(old, -1);
            data.setDirty();
        }
    }

    public double total(long field) {
        Field value = fields.get(field);
        return value == null ? 0 : value.total();
    }

    public int count(long field) {
        Field value = fields.get(field);
        return value == null ? 0 : value.count();
    }

    public static OilMapData load(CompoundTag tag, HolderLookup.Provider lookup) {
        OilMapData data = new OilMapData();
        for (Tag entry : tag.getList("wells", Tag.TAG_COMPOUND)) {
            CompoundTag well = (CompoundTag) entry;
            double yield = well.getDouble("yield");
            if (Double.isFinite(yield) && yield >= 0) {
                long pos = well.getLong("position");
                data.put(new Well(pos, well.getLong("field"), yield));
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (Well well : wells.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("position", well.position());
            entry.putLong("field", well.field());
            entry.putDouble("yield", well.yield());
            list.add(entry);
        }
        tag.put("wells", list);
        return tag;
    }
}
