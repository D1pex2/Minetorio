package dev.minetorio.content.research;

import dev.minetorio.Minetorio;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the world's research state (researched technologies, the current one or null,
 * units done per partially researched technology), applied to {@link ClientResearch}. Sent by
 * {@link ResearchSavedData}.
 */
public record ResearchSyncPayload(Set<String> researched, @Nullable String current, Map<String, Integer> progressById)
        implements CustomPacketPayload {
    /** Most technologies (researched or with progress) accepted from the network. */
    public static final int MAX_TECHS = 256;
    /** Longest technology id accepted from the network. */
    public static final int MAX_ID_LENGTH = 64;
    public static final Type<ResearchSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "research_sync"));
    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(MAX_ID_LENGTH);
    public static final StreamCodec<ByteBuf, ResearchSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, String, Set<String>>collection(HashSet::new, ID, MAX_TECHS), ResearchSyncPayload::researched,
            ByteBufCodecs.optional(ID), p -> Optional.ofNullable(p.current()),
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ID, ByteBufCodecs.VAR_INT, MAX_TECHS),
            ResearchSyncPayload::progressById,
            (researched, current, progress) -> new ResearchSyncPayload(researched, current.orElse(null), progress));

    public ResearchSyncPayload {
        researched = Set.copyOf(researched);
        progressById = Map.copyOf(progressById);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ResearchSyncPayload payload, IPayloadContext context) {
        ClientResearch.apply(payload);
    }
}
