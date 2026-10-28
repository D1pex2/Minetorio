package dev.minetorio.content.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.core.blueprint.BlueprintBooks;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A blueprint book's contents (the {@code minetorio:blueprint_book} item component): its entries
 * (blueprints and books, {@link BlueprintRecord}) in order and the active one ({@code -1} when
 * empty), which the wheel changes and a paste with the book uses.
 */
public record BlueprintBookData(List<BlueprintRecord> entries, int active) {
    public static final BlueprintBookData EMPTY = new BlueprintBookData(List.of(), -1);

    public BlueprintBookData {
        entries = List.copyOf(entries);
        active = BlueprintBooks.clamp(active, entries.size());
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public int size() { return entries.size(); }

    @Nullable
    public BlueprintRecord activeEntry() {
        return active < 0 ? null : entries.get(active);
    }

    public BlueprintBookData withActive(int a) { return new BlueprintBookData(entries, a); }

    /** The wheel: the active entry {@code delta} steps on, wrapping. */
    public BlueprintBookData cycled(int delta) { return withActive(BlueprintBooks.cycle(active, entries.size(), delta)); }

    public BlueprintBookData inserted(int at, BlueprintRecord r) {
        int i = Math.max(0, Math.min(entries.size(), at));
        List<BlueprintRecord> out = new ArrayList<>(entries);
        out.add(i, r);
        return new BlueprintBookData(out, BlueprintBooks.activeAfterInsert(active, i, entries.size()));
    }

    public BlueprintBookData removed(int index) {
        List<BlueprintRecord> out = new ArrayList<>(entries);
        out.remove(index);
        return new BlueprintBookData(out, BlueprintBooks.activeAfterRemove(active, index, entries.size()));
    }

    public BlueprintBookData moved(int from, int to) {
        int t = Math.max(0, Math.min(entries.size() - 1, to));
        return new BlueprintBookData(BlueprintBooks.moved(entries, from, t), BlueprintBooks.activeAfterMove(active, from, t));
    }

    public BlueprintBookData replaced(int index, BlueprintRecord r) {
        List<BlueprintRecord> out = new ArrayList<>(entries);
        out.set(index, r);
        return new BlueprintBookData(out, active);
    }

    static Codec<BlueprintBookData> codec(Codec<BlueprintRecord> record) {
        return RecordCodecBuilder.create(i -> i.group(
                record.sizeLimitedListOf(BlueprintBooks.MAX_ENTRIES).fieldOf("entries").forGetter(BlueprintBookData::entries),
                Codec.INT.optionalFieldOf("active", -1).forGetter(BlueprintBookData::active)).apply(i, BlueprintBookData::new));
    }

    /** The item component's codec: the whole book validated ({@link BlueprintRecord#validate}). */
    public static final Codec<BlueprintBookData> CODEC = codec(BlueprintRecord.CODEC)
            .validate(d -> BlueprintRecord.ofBook(BlueprintMeta.EMPTY, d).validate().map(r -> d));

    public static final StreamCodec<ByteBuf, BlueprintBookData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
