package dev.minetorio.content.blueprint;

import dev.minetorio.Minetorio;
import dev.minetorio.core.blueprint.BlueprintBooks;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Blueprint payloads, client to server (registered in {@code ModPayloads}; handlers run on the
 * main thread). The blueprint itself never travels in them: the server uses the one in the hand.
 */
public final class BlueprintPayloads {
    private BlueprintPayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path); }

    /** Copy the box between {@code a} and {@code b} into the empty blueprint in hand; {@code autoHeight}: columns only. */
    public record Capture(BlockPos a, BlockPos b, boolean autoHeight) implements CustomPacketPayload {
        public static final Type<Capture> TYPE = new Type<>(id("blueprint_capture"));
        public static final StreamCodec<ByteBuf, Capture> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Capture::a, BlockPos.STREAM_CODEC, Capture::b, ByteBufCodecs.BOOL, Capture::autoHeight, Capture::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Capture p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) BlueprintCapture.capture(player, p.a(), p.b(), p.autoHeight());
        }
    }

    /** Paste the blueprint in hand with the cursor on {@code anchor}, transform {@code bits}; {@code force}: Shift. */
    public record Paste(BlockPos anchor, byte bits, boolean force) implements CustomPacketPayload {
        public static final Type<Paste> TYPE = new Type<>(id("blueprint_paste"));
        public static final StreamCodec<ByteBuf, Paste> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Paste::anchor, ByteBufCodecs.BYTE, Paste::bits, ByteBufCodecs.BOOL, Paste::force, Paste::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Paste p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) BlueprintPaste.paste(player, p.anchor(), p.bits() & 7, p.force());
        }
    }

    /** The blueprint shortcut: a new empty blueprint (or, {@code book}, an empty blueprint book) in hand. */
    public record Give(boolean book) implements CustomPacketPayload {
        public static final Type<Give> TYPE = new Type<>(id("blueprint_give"));
        public static final StreamCodec<ByteBuf, Give> STREAM_CODEC = ByteBufCodecs.BOOL.map(Give::new, Give::book);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Give p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                BlueprintCapture.give(player, p.book() ? BlueprintBookItem.empty() : BlueprintItem.empty());
            }
        }
    }

    private static final StreamCodec<ByteBuf, List<Integer>> PATH =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(BlueprintBooks.MAX_DEPTH));

    /** Ctrl + wheel with a book in the main hand: the active entry {@code delta} (sign only) steps on. */
    public record BookScroll(int delta) implements CustomPacketPayload {
        public static final Type<BookScroll> TYPE = new Type<>(id("blueprint_book_scroll"));
        public static final StreamCodec<ByteBuf, BookScroll> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(BookScroll::new, BookScroll::delta);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(BookScroll p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) BlueprintBookOps.scroll(player, p.delta());
        }
    }

    /** A book window change ({@link BlueprintBookOps.Op} {@code op}) on the book at {@code path} in inventory slot {@code slot}. */
    public record BookEdit(int slot, List<Integer> path, int op, int a, int b) implements CustomPacketPayload {
        public static final Type<BookEdit> TYPE = new Type<>(id("blueprint_book_edit"));
        public static final StreamCodec<ByteBuf, BookEdit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BookEdit::slot, PATH, BookEdit::path, ByteBufCodecs.VAR_INT, BookEdit::op,
                ByteBufCodecs.VAR_INT, BookEdit::a, ByteBufCodecs.VAR_INT, BookEdit::b, BookEdit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(BookEdit p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                BlueprintBookOps.edit(player, p.slot(), p.path(), BlueprintBookOps.Op.byId(p.op()), p.a(), p.b());
            }
        }
    }

    /** New name and icons for the blueprint or book at {@code path} in inventory slot {@code slot}. */
    public record SetMeta(int slot, List<Integer> path, BlueprintMeta meta) implements CustomPacketPayload {
        public static final Type<SetMeta> TYPE = new Type<>(id("blueprint_set_meta"));
        public static final StreamCodec<ByteBuf, SetMeta> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetMeta::slot, PATH, SetMeta::path, BlueprintMeta.STREAM_CODEC, SetMeta::meta, SetMeta::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SetMeta p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) BlueprintBookOps.setMeta(player, p.slot(), p.path(), p.meta());
        }
    }

    /**
     * One chunk ({@code index} of {@code total}) of upload {@code transfer}: a blueprint or book
     * from the library or a string, as deflated bytes, to become an item in slot {@code slot}
     * ({@link BlueprintImport}).
     */
    public record Upload(int transfer, int index, int total, int slot, byte[] data) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = new Type<>(id("blueprint_upload"));
        public static final StreamCodec<ByteBuf, Upload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Upload::transfer, ByteBufCodecs.VAR_INT, Upload::index, ByteBufCodecs.VAR_INT, Upload::total,
                ByteBufCodecs.VAR_INT, Upload::slot, ByteBufCodecs.byteArray(BlueprintImport.CHUNK), Upload::data, Upload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Upload p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) BlueprintImport.chunk(player, p.transfer(), p.index(), p.total(), p.slot(), p.data());
        }
    }
}
