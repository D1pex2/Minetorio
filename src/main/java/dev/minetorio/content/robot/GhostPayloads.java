package dev.minetorio.content.robot;

import dev.minetorio.Minetorio;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Hand ghost payloads, client to server (registered in {@code ModPayloads}; handlers run on the main thread). */
public final class GhostPayloads {
    private GhostPayloads() {}

    /** A ghost click: the main hand's block item at the block {@code hit} ({@link HandGhosts#place}). */
    public record Place(BlockHitResult hit) implements CustomPacketPayload {
        public static final Type<Place> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ghost_place"));
        public static final StreamCodec<FriendlyByteBuf, Place> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBlockHitResult(p.hit()), buf -> new Place(buf.readBlockHitResult()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Place payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) HandGhosts.place(player, payload.hit());
        }
    }

    /** A ghost item of a recipe's output, from the hand-crafting screen ({@link HandGhosts#fromRecipe}). */
    public record FromRecipe(String recipeId) implements CustomPacketPayload {
        public static final Type<FromRecipe> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ghost_from_recipe"));
        public static final StreamCodec<FriendlyByteBuf, FromRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), FromRecipe::recipeId, FromRecipe::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(FromRecipe payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) HandGhosts.fromRecipe(player, payload.recipeId());
        }
    }

    /** Pick-block on a placed block whose item the player has none of ({@link HandGhosts#pipette}). */
    public record Pipette(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Pipette> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ghost_pipette"));
        public static final StreamCodec<FriendlyByteBuf, Pipette> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Pipette::pos, Pipette::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Pipette payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) HandGhosts.pipette(player, payload.pos());
        }
    }
}
