package dev.minetorio.content.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.core.blueprint.BlueprintBooks;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A blueprint's or book's name and icons (the {@code minetorio:blueprint_meta} item component),
 * as in Factorio: a name of up to {@link BlueprintBooks#MAX_LABEL} characters and up to
 * {@link BlueprintBooks#MAX_ICONS} item icons (drawn over the slot). Empty means the default name
 * and no icons.
 */
public record BlueprintMeta(String label, List<ResourceLocation> icons) {
    public static final BlueprintMeta EMPTY = new BlueprintMeta("", List.of());

    public BlueprintMeta {
        icons = List.copyOf(icons);
    }

    public boolean isEmpty() { return label.isEmpty() && icons.isEmpty(); }

    public boolean hasLabel() { return !label.isEmpty(); }

    public BlueprintMeta withLabel(String l) { return new BlueprintMeta(BlueprintBooks.cleanLabel(l), icons); }

    public BlueprintMeta withIcons(List<ResourceLocation> i) { return new BlueprintMeta(label, i.size() > BlueprintBooks.MAX_ICONS ? i.subList(0, BlueprintBooks.MAX_ICONS) : i); }

    /** The icon items that exist (unknown ids and air dropped). */
    public List<Item> iconItems() {
        List<Item> out = new ArrayList<>(icons.size());
        for (ResourceLocation id : icons) {
            Item it = BuiltInRegistries.ITEM.get(id);
            if (it != Items.AIR) out.add(it);
        }
        return out;
    }

    private static DataResult<BlueprintMeta> check(BlueprintMeta m) {
        if (!BlueprintBooks.validLabel(m.label())) return DataResult.error(() -> "bad blueprint name");
        if (m.icons().size() > BlueprintBooks.MAX_ICONS) return DataResult.error(() -> "too many blueprint icons");
        return DataResult.success(m);
    }

    public static final Codec<BlueprintMeta> CODEC = RecordCodecBuilder.<BlueprintMeta>create(i -> i.group(
            Codec.sizeLimitedString(BlueprintBooks.MAX_LABEL * 2).optionalFieldOf("label", "").forGetter(BlueprintMeta::label),
            ResourceLocation.CODEC.sizeLimitedListOf(BlueprintBooks.MAX_ICONS).optionalFieldOf("icons", List.of()).forGetter(BlueprintMeta::icons))
            .apply(i, BlueprintMeta::new)).validate(BlueprintMeta::check);

    public static final StreamCodec<ByteBuf, BlueprintMeta> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
