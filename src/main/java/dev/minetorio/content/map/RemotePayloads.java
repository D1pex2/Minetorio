package dev.minetorio.content.map;

import dev.minetorio.Minetorio;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Remote view payloads ({@link RemoteView}; registered in {@code ModPayloads}; handlers run on the main thread). */
public final class RemotePayloads {
    private RemotePayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path); }

    /** Client to server: open a remote view focused on block column {@code (x, z)} (from the map). */
    public record Enter(double x, double z) implements CustomPacketPayload {
        public static final Type<Enter> TYPE = new Type<>(id("remote_enter"));
        public static final StreamCodec<ByteBuf, Enter> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Enter::x, ByteBufCodecs.DOUBLE, Enter::z, Enter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Enter p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) RemoteView.enter(player, p.x(), p.z());
        }
    }

    /** Client to server: the remote camera's focus moved to {@code (x, z)}. */
    public record Move(double x, double z) implements CustomPacketPayload {
        public static final Type<Move> TYPE = new Type<>(id("remote_move"));
        public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Move::x, ByteBufCodecs.DOUBLE, Move::z, Move::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Move p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) RemoteView.move(player, p.x(), p.z());
        }
    }

    /** Client to server: leave the remote view. */
    public record Exit() implements CustomPacketPayload {
        public static final Type<Exit> TYPE = new Type<>(id("remote_exit"));
        public static final StreamCodec<ByteBuf, Exit> STREAM_CODEC = StreamCodec.unit(new Exit());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Exit p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) RemoteView.exit(player);
        }
    }

    /** Client to server: open the GUI of the machine at {@code pos} from the remote view. */
    public record OpenMenu(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(id("remote_open_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, OpenMenu::pos, OpenMenu::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(OpenMenu p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) RemoteView.openMenu(player, p.pos());
        }
    }

    /** Client to server: remove the ghost at {@code pos} (left-click in the remote view). */
    public record RemoveGhost(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RemoveGhost> TYPE = new Type<>(id("remote_remove_ghost"));
        public static final StreamCodec<ByteBuf, RemoveGhost> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, RemoveGhost::pos, RemoveGhost::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(RemoveGhost p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) RemoteView.removeGhost(player, p.pos());
        }
    }

    /**
     * Server to client: the remote view is ({@code active}) focused on {@code (x, z)} (an answer to
     * Enter, or a correction of a refused move), or it is closed / was refused, {@code reason} saying
     * why ({@link RemoteView.Reason} ordinal).
     */
    public record State(boolean active, double x, double z, byte reason) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("remote_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::active, ByteBufCodecs.DOUBLE, State::x, ByteBufCodecs.DOUBLE, State::z,
                ByteBufCodecs.BYTE, State::reason, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(State p, IPayloadContext context) {
            RemoteView.Reason[] all = RemoteView.Reason.values();
            RemoteView.Reason reason = p.reason() >= 0 && p.reason() < all.length ? all[p.reason()] : RemoteView.Reason.NONE;
            if (RemoteView.clientHandler != null) RemoteView.clientHandler.accept(p.active(), p.x(), p.z(), reason);
        }
    }
}
