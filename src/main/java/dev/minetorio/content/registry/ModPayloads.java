package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.research.ResearchSyncPayload;
import dev.minetorio.content.research.SelectResearchPayload;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Custom network payloads (handlers run on the main thread). */
public final class ModPayloads {
    private static final String VERSION = "1";

    private ModPayloads() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(SelectRecipePayload.TYPE, SelectRecipePayload.STREAM_CODEC, SelectRecipePayload::handle)
                .playToServer(SelectResearchPayload.TYPE, SelectResearchPayload.STREAM_CODEC, SelectResearchPayload::handle)
                .playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC, ResearchSyncPayload::handle);
    }

    /** Client to server: set the recipe of the assembler at {@code pos} (validated by {@link AssemblerBlockEntity#select}). */
    public record SelectRecipePayload(BlockPos pos, String recipeId) implements CustomPacketPayload {
        /** Longest recipe id accepted from the network. */
        private static final int MAX_ID_LENGTH = 256;
        public static final Type<SelectRecipePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "select_recipe"));
        public static final StreamCodec<ByteBuf, SelectRecipePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SelectRecipePayload::pos,
                ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), SelectRecipePayload::recipeId,
                SelectRecipePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        static void handle(SelectRecipePayload payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) AssemblerBlockEntity.select(player, payload.pos(), payload.recipeId());
        }
    }
}
