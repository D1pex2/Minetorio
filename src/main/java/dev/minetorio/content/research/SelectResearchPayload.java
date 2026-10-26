package dev.minetorio.content.research;

import dev.minetorio.Minetorio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: research {@code techId} next (validated by {@link ResearchSavedData#select(ServerPlayer, String)}). */
public record SelectResearchPayload(String techId) implements CustomPacketPayload {
    public static final Type<SelectResearchPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "select_research"));
    public static final StreamCodec<ByteBuf, SelectResearchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(ResearchSyncPayload.MAX_ID_LENGTH), SelectResearchPayload::techId,
            SelectResearchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SelectResearchPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) ResearchSavedData.select(player, payload.techId());
    }
}
