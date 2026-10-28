package dev.minetorio.content.robot;

import dev.minetorio.Minetorio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the deconstruction / upgrade marks of one chunk ({@link ConstructionMarks}), kept
 * on the client in {@link #CLIENT} for the overlay renderer. A chunk's list replaces what the client
 * had for it; an empty list clears it.
 */
public final class MarkPayloads {
    private MarkPayloads() {}

    /** Most marks one chunk sends (a chunk full of marked leaves stays under it). */
    private static final int MAX_ENTRIES = 1 << 16;

    /** One mark: {@code upgrade} false = deconstruction; {@code target} the upgrade's new item. */
    public record Entry(BlockPos pos, boolean upgrade, String target) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos, ByteBufCodecs.BOOL, Entry::upgrade,
                ByteBufCodecs.stringUtf8(256), Entry::target, Entry::new);
    }

    public record Chunk(int cx, int cz, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "construction_marks"));
        public static final StreamCodec<ByteBuf, Chunk> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Chunk::cx, ByteBufCodecs.VAR_INT, Chunk::cz,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), Chunk::entries, Chunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Chunk p, IPayloadContext context) {
            CLIENT.set(p.cx(), p.cz(), p.entries());
        }
    }

    /** The client's copy of the marks it was sent, by chunk (render thread reads, network handler writes on the main thread). */
    public static final ClientStore CLIENT = new ClientStore();

    public static final class ClientStore {
        private final Map<Long, Map<BlockPos, Entry>> chunks = new ConcurrentHashMap<>();
        private volatile int version;

        private static long key(int cx, int cz) {
            return (cx & 0xFFFFFFFFL) | ((cz & 0xFFFFFFFFL) << 32);
        }

        public void set(int cx, int cz, List<Entry> entries) {
            if (entries.isEmpty()) {
                chunks.remove(key(cx, cz));
            } else {
                Map<BlockPos, Entry> m = new HashMap<>();
                for (Entry e : entries) m.put(e.pos(), e);
                chunks.put(key(cx, cz), m);
            }
            version++;
        }

        public void drop(int cx, int cz) {
            if (chunks.remove(key(cx, cz)) != null) version++;
        }

        public void clear() {
            chunks.clear();
            version++;
        }

        /** Increases whenever anything changes. */
        public int version() { return version; }

        /** Every mark the client knows (a copy). */
        public List<Entry> all() {
            List<Entry> out = new ArrayList<>();
            for (Map<BlockPos, Entry> m : chunks.values()) out.addAll(m.values());
            return out;
        }

        public Entry at(BlockPos pos) {
            Map<BlockPos, Entry> m = chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
            return m == null ? null : m.get(pos);
        }
    }
}
