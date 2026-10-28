package dev.minetorio.content.map;

import com.mojang.logging.LogUtils;
import dev.minetorio.Minetorio;
import dev.minetorio.core.map.MapChunkBatch;
import dev.minetorio.core.map.WatchCoverage;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/** Server-to-client map payloads (registered in {@code ModPayloads}). */
public final class MapPayloads {
    private static final Logger LOG = LogUtils.getLogger();

    private MapPayloads() {}

    /**
     * Map colors of up to {@value MapChunkBatch#MAX_CHUNKS} explored chunks, deflated
     * ({@link MapChunkBatch}); at most {@value MapChunkBatch#MAX_BYTES} bytes on the wire.
     */
    public record Chunks(byte[] data) implements CustomPacketPayload {
        public static final Type<Chunks> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "map_chunks"));
        public static final StreamCodec<ByteBuf, Chunks> STREAM_CODEC =
                ByteBufCodecs.byteArray(MapChunkBatch.MAX_BYTES).map(Chunks::new, Chunks::data);

        public static Chunks of(List<MapChunkBatch.Entry> entries) {
            return new Chunks(MapChunkBatch.encode(entries));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Chunks payload, IPayloadContext context) {
            try {
                ClientMapData.accept(MapChunkBatch.decode(payload.data()));
            } catch (IOException e) {
                LOG.warn("Dropped a malformed map payload", e);
            }
        }
    }

    /** A player on the map: name, block position and look direction (degrees, Minecraft yaw). */
    public record Marker(String name, int x, int z, float yaw) {
        static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(Players.MAX_NAME), Marker::name,
                ByteBufCodecs.VAR_INT, Marker::x,
                ByteBufCodecs.VAR_INT, Marker::z,
                ByteBufCodecs.FLOAT, Marker::yaw,
                Marker::new);
    }

    /** Every player in the Overworld (sent twice a second, so far-away players show on the map too). */
    public record Players(List<Marker> players) implements CustomPacketPayload {
        public static final int MAX_PLAYERS = 64;
        static final int MAX_NAME = 32;
        public static final Type<Players> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "map_players"));
        public static final StreamCodec<ByteBuf, Players> STREAM_CODEC =
                ByteBufCodecs.<ByteBuf, Marker, List<Marker>>collection(ArrayList::new, Marker.STREAM_CODEC, MAX_PLAYERS)
                        .map(Players::new, Players::players);

        public Players {
            players = List.copyOf(players);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Players payload, IPayloadContext context) {
            ClientMapData.setPlayers(payload.players());
        }
    }

    /**
     * The chunks watched by working radars ({@link WatchedChunks}): with {@code reset}, the client
     * first forgets its copy (the first piece of a full set on login); then it adds {@code added}
     * and removes {@code removed}. At most {@value #MAX_KEYS} chunks in all per payload.
     */
    public record Watched(boolean reset, long[] added, long[] removed) implements CustomPacketPayload {
        public static final int MAX_KEYS = 4096;
        public static final Type<Watched> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "map_watched"));
        public static final StreamCodec<ByteBuf, Watched> STREAM_CODEC = StreamCodec.of(Watched::write, Watched::read);

        public Watched {
            if (added.length + removed.length > MAX_KEYS) throw new IllegalArgumentException("too many chunks: " + (added.length + removed.length));
        }

        /** The whole set: a reset piece first, then as many pieces as needed. */
        public static List<Watched> full(Collection<Long> chunks) {
            List<Watched> out = new ArrayList<>();
            for (long[] piece : WatchCoverage.split(chunks, MAX_KEYS)) out.add(new Watched(out.isEmpty(), piece, new long[0]));
            return out;
        }

        /** A diff, in as many pieces as needed (none if empty). */
        public static List<Watched> diff(Collection<Long> added, Collection<Long> removed) {
            List<Watched> out = new ArrayList<>();
            if (!added.isEmpty()) for (long[] piece : WatchCoverage.split(added, MAX_KEYS)) out.add(new Watched(false, piece, new long[0]));
            if (!removed.isEmpty()) for (long[] piece : WatchCoverage.split(removed, MAX_KEYS)) out.add(new Watched(false, new long[0], piece));
            return out;
        }

        private static void write(ByteBuf buf, Watched w) {
            buf.writeBoolean(w.reset);
            writeKeys(buf, w.added);
            writeKeys(buf, w.removed);
        }

        private static void writeKeys(ByteBuf buf, long[] keys) {
            VarInt.write(buf, keys.length);
            for (long k : keys) buf.writeLong(k);
        }

        private static Watched read(ByteBuf buf) {
            boolean reset = buf.readBoolean();
            long[] added = readKeys(buf, MAX_KEYS);
            long[] removed = readKeys(buf, MAX_KEYS - added.length);
            return new Watched(reset, added, removed);
        }

        private static long[] readKeys(ByteBuf buf, int max) {
            int n = VarInt.read(buf);
            if (n < 0 || n > max) throw new io.netty.handler.codec.DecoderException("watched chunks: " + n + " > " + max);
            long[] keys = new long[n];
            for (int i = 0; i < n; i++) keys[i] = buf.readLong();
            return keys;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Watched payload, IPayloadContext context) {
            ClientWatchedChunks.accept(payload.reset(), payload.added(), payload.removed());
        }
    }
}
