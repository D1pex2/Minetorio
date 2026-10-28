package dev.minetorio.content.logistics;

import dev.minetorio.core.logistics.LogisticRequest;
import dev.minetorio.core.logistics.RequestTable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** {@link RequestTable} as NBT (saves) and as a network entry list (menu sync). */
public final class RequestNbt {
    private RequestNbt() {}

    /** One set slot. */
    public record Entry(int slot, String item, int min, int max) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::slot,
                ByteBufCodecs.stringUtf8(256), Entry::item,
                ByteBufCodecs.VAR_INT, Entry::min,
                ByteBufCodecs.INT, Entry::max,
                Entry::new);
    }

    public static ListTag save(RequestTable t) {
        ListTag list = new ListTag();
        for (Entry e : entries(t)) {
            CompoundTag c = new CompoundTag();
            c.putInt("slot", e.slot());
            c.putString("item", e.item());
            c.putInt("min", e.min());
            c.putInt("max", e.max());
            list.add(c);
        }
        return list;
    }

    public static void load(RequestTable t, ListTag list) {
        List<Entry> entries = new ArrayList<>();
        for (Tag tag : list) {
            if (tag instanceof CompoundTag c && !c.getString("item").isEmpty()) {
                entries.add(new Entry(c.getInt("slot"), c.getString("item"), c.getInt("min"), c.getInt("max")));
            }
        }
        apply(t, entries);
    }

    public static List<Entry> entries(RequestTable t) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            LogisticRequest r = t.get(i);
            if (r != null) out.add(new Entry(i, r.item(), r.min(), r.max()));
        }
        return out;
    }

    /** Replaces the table's contents with {@code entries} (slots out of range are skipped). */
    public static void apply(RequestTable t, List<Entry> entries) {
        t.clear();
        for (Entry e : entries) {
            if (e.slot() >= 0 && e.slot() < t.size() && !e.item().isEmpty()) t.set(e.slot(), new LogisticRequest(e.item(), e.min(), e.max()));
        }
    }
}
