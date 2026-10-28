package dev.minetorio.content.blueprint;

import dev.minetorio.Minetorio;
import io.netty.buffer.ByteBuf;
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

    /** The blueprint shortcut: a new empty blueprint in hand. */
    public record Give() implements CustomPacketPayload {
        public static final Type<Give> TYPE = new Type<>(id("blueprint_give"));
        public static final StreamCodec<ByteBuf, Give> STREAM_CODEC = StreamCodec.unit(new Give());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Give p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) BlueprintCapture.give(player);
        }
    }
}
