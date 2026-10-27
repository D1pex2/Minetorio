package dev.minetorio.content.map;

import com.mojang.logging.LogUtils;
import dev.minetorio.Minetorio;
import dev.minetorio.core.map.MapChunkBatch;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
}
