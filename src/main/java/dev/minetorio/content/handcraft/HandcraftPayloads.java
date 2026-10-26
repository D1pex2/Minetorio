package dev.minetorio.content.handcraft;

import dev.minetorio.Minetorio;
import dev.minetorio.core.handcraft.HandcraftQueue;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Hand-crafting network payloads (registered in {@code ModPayloads}; handlers run on the main thread). */
public final class HandcraftPayloads {
    /** Longest recipe id accepted from the network. */
    public static final int MAX_ID_LENGTH = 64;
    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(MAX_ID_LENGTH);

    private HandcraftPayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path); }

    /** Client to server: queue {@code times} crafts of {@code recipeId} (validated by {@link HandcraftTicker#enqueue}). */
    public record Enqueue(String recipeId, int times) implements CustomPacketPayload {
        public static final Type<Enqueue> TYPE = new Type<>(id("handcraft_enqueue"));
        public static final StreamCodec<ByteBuf, Enqueue> STREAM_CODEC = StreamCodec.composite(
                ID, Enqueue::recipeId, ByteBufCodecs.VAR_INT, Enqueue::times, Enqueue::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Enqueue payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) HandcraftTicker.enqueue(player, payload.recipeId(), payload.times());
        }
    }

    /**
     * Client to server: cancel the queue entry at {@code index}, which the client saw as {@code times} ×
     * {@code recipeId} (validated by {@link HandcraftTicker#cancel}; refused if the entry differs).
     */
    public record Cancel(int index, String recipeId, int times) implements CustomPacketPayload {
        public static final Type<Cancel> TYPE = new Type<>(id("handcraft_cancel"));
        public static final StreamCodec<ByteBuf, Cancel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cancel::index, ID, Cancel::recipeId, ByteBufCodecs.VAR_INT, Cancel::times, Cancel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Cancel payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) HandcraftTicker.cancel(player, payload.index(), payload.recipeId(), payload.times());
        }
    }

    /**
     * Server to client: the player's queue (at most {@link HandcraftTicker#MAX_ENTRIES} entries) and the
     * duration in ticks of the step the front entry is crafting, so the client can advance its
     * progress between syncs. Applied to {@link ClientHandcraft}.
     */
    public record QueueSync(List<HandcraftQueue.Entry> entries, int stepTicks) implements CustomPacketPayload {
        public static final Type<QueueSync> TYPE = new Type<>(id("handcraft_queue"));
        private static final StreamCodec<ByteBuf, HandcraftQueue.Entry> ENTRY = StreamCodec.composite(
                ID, HandcraftQueue.Entry::recipeId, ByteBufCodecs.VAR_INT, HandcraftQueue.Entry::times,
                ByteBufCodecs.DOUBLE, HandcraftQueue.Entry::progress, HandcraftQueue.Entry::new);
        public static final StreamCodec<ByteBuf, QueueSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.<ByteBuf, HandcraftQueue.Entry, List<HandcraftQueue.Entry>>collection(ArrayList::new, ENTRY,
                        HandcraftTicker.MAX_ENTRIES), QueueSync::entries,
                ByteBufCodecs.VAR_INT, QueueSync::stepTicks, QueueSync::new);

        public QueueSync {
            entries = List.copyOf(entries);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(QueueSync payload, IPayloadContext context) {
            ClientHandcraft.apply(payload);
        }
    }
}
