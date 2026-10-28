package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.drag.DragPayloads;
import dev.minetorio.content.handcraft.HandcraftPayloads;
import dev.minetorio.content.machine.AssemblerBlockEntity;
import dev.minetorio.content.map.MapPayloads;
import dev.minetorio.content.map.RemotePayloads;
import dev.minetorio.content.orefield.OreFieldPayloads;
import dev.minetorio.content.research.ResearchSyncPayload;
import dev.minetorio.content.research.SelectResearchPayload;
import dev.minetorio.content.robot.GhostPayloads;
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
    private static final String VERSION = "3";

    private ModPayloads() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(SelectRecipePayload.TYPE, SelectRecipePayload.STREAM_CODEC, SelectRecipePayload::handle)
                .playToServer(SelectResearchPayload.TYPE, SelectResearchPayload.STREAM_CODEC, SelectResearchPayload::handle)
                .playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC, ResearchSyncPayload::handle)
                .playToServer(HandcraftPayloads.Enqueue.TYPE, HandcraftPayloads.Enqueue.STREAM_CODEC, HandcraftPayloads.Enqueue::handle)
                .playToServer(HandcraftPayloads.Cancel.TYPE, HandcraftPayloads.Cancel.STREAM_CODEC, HandcraftPayloads.Cancel::handle)
                .playToClient(HandcraftPayloads.QueueSync.TYPE, HandcraftPayloads.QueueSync.STREAM_CODEC, HandcraftPayloads.QueueSync::handle)
                .playToClient(MapPayloads.Chunks.TYPE, MapPayloads.Chunks.STREAM_CODEC, MapPayloads.Chunks::handle)
                .playToClient(MapPayloads.Players.TYPE, MapPayloads.Players.STREAM_CODEC, MapPayloads.Players::handle)
                .playToClient(MapPayloads.Watched.TYPE, MapPayloads.Watched.STREAM_CODEC, MapPayloads.Watched::handle)
                .playToServer(OreFieldPayloads.Query.TYPE, OreFieldPayloads.Query.STREAM_CODEC, OreFieldPayloads.Query::handle)
                .playToClient(OreFieldPayloads.Info.TYPE, OreFieldPayloads.Info.STREAM_CODEC, OreFieldPayloads.Info::handle)
                .playToServer(DragPayloads.Start.TYPE, DragPayloads.Start.STREAM_CODEC, DragPayloads.Start::handle)
                .playToServer(DragPayloads.Steps.TYPE, DragPayloads.Steps.STREAM_CODEC, DragPayloads.Steps::handle)
                .playToServer(GhostPayloads.Place.TYPE, GhostPayloads.Place.STREAM_CODEC, GhostPayloads.Place::handle)
                .playToServer(GhostPayloads.FromRecipe.TYPE, GhostPayloads.FromRecipe.STREAM_CODEC, GhostPayloads.FromRecipe::handle)
                .playToServer(GhostPayloads.Pipette.TYPE, GhostPayloads.Pipette.STREAM_CODEC, GhostPayloads.Pipette::handle)
                .playToServer(RemotePayloads.Enter.TYPE, RemotePayloads.Enter.STREAM_CODEC, RemotePayloads.Enter::handle)
                .playToServer(RemotePayloads.Move.TYPE, RemotePayloads.Move.STREAM_CODEC, RemotePayloads.Move::handle)
                .playToServer(RemotePayloads.Exit.TYPE, RemotePayloads.Exit.STREAM_CODEC, RemotePayloads.Exit::handle)
                .playToServer(RemotePayloads.OpenMenu.TYPE, RemotePayloads.OpenMenu.STREAM_CODEC, RemotePayloads.OpenMenu::handle)
                .playToServer(RemotePayloads.RemoveGhost.TYPE, RemotePayloads.RemoveGhost.STREAM_CODEC, RemotePayloads.RemoveGhost::handle)
                .playToClient(RemotePayloads.State.TYPE, RemotePayloads.State.STREAM_CODEC, RemotePayloads.State::handle)
                .playToServer(dev.minetorio.content.robot.PlannerPayloads.Select.TYPE, dev.minetorio.content.robot.PlannerPayloads.Select.STREAM_CODEC,
                        dev.minetorio.content.robot.PlannerPayloads.Select::handle)
                .playToServer(dev.minetorio.content.robot.PlannerPayloads.SetFilter.TYPE, dev.minetorio.content.robot.PlannerPayloads.SetFilter.STREAM_CODEC,
                        dev.minetorio.content.robot.PlannerPayloads.SetFilter::handle)
                .playToServer(dev.minetorio.content.robot.PlannerPayloads.SetRules.TYPE, dev.minetorio.content.robot.PlannerPayloads.SetRules.STREAM_CODEC,
                        dev.minetorio.content.robot.PlannerPayloads.SetRules::handle)
                .playToServer(dev.minetorio.content.robot.PlannerPayloads.Give.TYPE, dev.minetorio.content.robot.PlannerPayloads.Give.STREAM_CODEC,
                        dev.minetorio.content.robot.PlannerPayloads.Give::handle)
                .playToClient(dev.minetorio.content.robot.MarkPayloads.Chunk.TYPE, dev.minetorio.content.robot.MarkPayloads.Chunk.STREAM_CODEC,
                        dev.minetorio.content.robot.MarkPayloads.Chunk::handle);
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
