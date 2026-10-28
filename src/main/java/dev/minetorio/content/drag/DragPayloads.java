package dev.minetorio.content.drag;

import dev.minetorio.Minetorio;
import dev.minetorio.core.drag.DragDir;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Drag placement payloads, client to server (registered in {@code ModPayloads}; handlers run on the main thread). */
public final class DragPayloads {
    private DragPayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path); }

    /**
     * A drag starts at {@code pos} (the cell the click placed) running {@code dir} ({@link DragDir}
     * id); {@code ghost}: it lays ghosts.
     */
    public record Start(BlockPos pos, byte dir, boolean ghost) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(id("drag_start"));
        public static final StreamCodec<ByteBuf, Start> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Start::pos, ByteBufCodecs.BYTE, Start::dir, ByteBufCodecs.BOOL, Start::ghost, Start::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Start payload, IPayloadContext context) {
            DragDir dir = DragDir.byId(payload.dir());
            if (context.player() instanceof ServerPlayer player && dir != null) DragPlacement.start(player, payload.pos(), dir, payload.ghost());
        }
    }

    /**
     * The line's steps number {@code from} onward, one {@link DragDir} id each (at most
     * {@value DragPlacement#MAX_STEPS_PER_PACKET}); {@code finish} ends the drag.
     */
    public record Steps(int from, byte[] steps, boolean finish) implements CustomPacketPayload {
        public static final Type<Steps> TYPE = new Type<>(id("drag_steps"));
        public static final StreamCodec<ByteBuf, Steps> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Steps::from, ByteBufCodecs.byteArray(DragPlacement.MAX_STEPS_PER_PACKET), Steps::steps,
                ByteBufCodecs.BOOL, Steps::finish, Steps::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Steps payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) DragPlacement.steps(player, payload.from(), payload.steps(), payload.finish());
        }
    }
}
