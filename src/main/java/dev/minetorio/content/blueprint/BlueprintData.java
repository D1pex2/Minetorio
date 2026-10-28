package dev.minetorio.content.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.core.blueprint.BlueprintPart;
import dev.minetorio.core.blueprint.Blueprints;
import dev.minetorio.core.drag.DragDir;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * A filled blueprint's contents (the {@code minetorio:blueprint} item data component): its parts
 * in blueprint space (normalized to start at 0, 0, 0), each with block id, origin, placement
 * facing, variant and optional settings ({@code CopyableSettings}). Bounded by
 * {@link Blueprints#MAX_PARTS} and the span limits; decoding refuses anything larger.
 *
 * <p>Stored compactly: a palette of block ids, two ints per part (palette index; x, y, z, facing,
 * variant packed) and the settings of the parts that have any, by part index.
 */
public record BlueprintData(List<Entry> entries) {
    /** One part: {@code facing} a {@link DragDir} id or -1, {@code variant} 0..15. */
    public record Entry(String block, int x, int y, int z, int facing, int variant, Optional<CompoundTag> settings) {
        @Nullable
        public DragDir dir() { return DragDir.byId(facing); }
    }

    public BlueprintData {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    // --- core view ------------------------------------------------------------------------------

    /** The parts as core parts (same order), footprints from the blocks' shapes; unknown blocks are one cell. */
    public List<BlueprintPart> parts() {
        List<BlueprintPart> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            int[] f = footprint(block(e.block()));
            out.add(new BlueprintPart(e.block(), e.x(), e.y(), e.z(), e.dir(), f[0], f[1], f[2], e.variant()));
        }
        return out;
    }

    /** {width, depth, height} of what {@code block} occupies: a multiblock's shape, a pole's height, else one cell. */
    public static int[] footprint(Block block) {
        if (block instanceof MultiblockControllerBlock mb) {
            MultiblockShape s = mb.shape();
            return new int[] {s.width(), s.depth(), s.height()};
        }
        if (block instanceof ElectricPoleBlock) return new int[] {1, 1, ElectricPoleBlock.HEIGHT};
        return new int[] {1, 1, 1};
    }

    /** The registered block {@code id} names, AIR if none. */
    public static Block block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(rl);
    }

    /** Whether this blueprint is within the limits: part count, span, height, settings sizes. */
    public boolean valid() {
        if (entries.isEmpty() || !Blueprints.withinLimits(parts())) return false;
        for (Entry e : entries) {
            if (e.settings().isPresent() && e.settings().get().sizeInBytes() > Blueprints.MAX_SETTINGS_BYTES) return false;
        }
        return true;
    }

    /** How many of each block id (for the tooltip), in first-seen order. */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Entry e : entries) out.merge(e.block(), 1, Integer::sum);
        return out;
    }

    // --- codecs ---------------------------------------------------------------------------------

    private record Indexed(int index, CompoundTag tag) {
        static final Codec<Indexed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("i").forGetter(Indexed::index),
                CompoundTag.CODEC.fieldOf("tag").forGetter(Indexed::tag)).apply(i, Indexed::new));
    }

    private record Packed(List<String> palette, int[] data, List<Indexed> settings) {
        static final Codec<Packed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("palette").forGetter(Packed::palette),
                Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("parts").forGetter(Packed::data),
                Indexed.CODEC.listOf().optionalFieldOf("settings", List.of()).forGetter(Packed::settings)).apply(i, Packed::new));
    }

    private static Packed pack(BlueprintData d) {
        List<String> palette = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        int[] data = new int[d.entries.size() * 2];
        List<Indexed> settings = new ArrayList<>();
        for (int i = 0; i < d.entries.size(); i++) {
            Entry e = d.entries.get(i);
            int p = index.computeIfAbsent(e.block(), b -> {
                palette.add(b);
                return palette.size() - 1;
            });
            data[2 * i] = p;
            data[2 * i + 1] = (e.x() & 0xFF) | (e.y() & 0xFF) << 8 | (e.z() & 0xFF) << 16 | ((e.facing() + 1) & 7) << 24 | (e.variant() & 15) << 27;
            int idx = i;
            e.settings().ifPresent(t -> settings.add(new Indexed(idx, t)));
        }
        return new Packed(palette, data, settings);
    }

    private static DataResult<BlueprintData> unpack(Packed p) {
        if (p.data().length % 2 != 0 || p.data().length / 2 > Blueprints.MAX_PARTS) return DataResult.error(() -> "blueprint too large");
        int n = p.data().length / 2;
        Map<Integer, CompoundTag> settings = new HashMap<>();
        for (Indexed s : p.settings()) {
            if (s.index() < 0 || s.index() >= n || s.tag().sizeInBytes() > Blueprints.MAX_SETTINGS_BYTES) {
                return DataResult.error(() -> "bad blueprint settings");
            }
            settings.put(s.index(), s.tag());
        }
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int pi = p.data()[2 * i], v = p.data()[2 * i + 1];
            if (pi < 0 || pi >= p.palette().size()) return DataResult.error(() -> "bad blueprint palette index");
            entries.add(new Entry(p.palette().get(pi), v & 0xFF, (v >> 8) & 0xFF, (v >> 16) & 0xFF, ((v >> 24) & 7) - 1, (v >> 27) & 15,
                    Optional.ofNullable(settings.get(i))));
        }
        return DataResult.success(new BlueprintData(entries));
    }

    public static final Codec<BlueprintData> CODEC = Packed.CODEC.comapFlatMap(BlueprintData::unpack, BlueprintData::pack);
    public static final StreamCodec<ByteBuf, BlueprintData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
