package dev.minetorio.content.orefield;

import dev.minetorio.Minetorio;
import dev.minetorio.content.map.WorldMap;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.RequestBudget;
import dev.minetorio.core.orefield.OreType;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The ore field under the cursor of the full-screen map: the client asks about a column
 * ({@link Query}), the server answers with the type and amount of the field block on its surface
 * ({@link Info}). Amounts live only on the server ({@link OreFieldData}), hence the round trip.
 *
 * <p>Bounded for multiplayer: each player has a {@link RequestBudget} ({@value #BURST} at once, then
 * one per {@value #TICKS_PER_QUERY} ticks; excess queries are dropped), only Overworld columns
 * inside the world border and in explored map chunks are answered, and only from chunks that are
 * already loaded (a query never loads or generates a chunk).
 */
public final class OreFieldPayloads {
    static final int BURST = 8;
    static final int TICKS_PER_QUERY = 2;
    /** {@link Info#ore()} of a column without a field block. */
    public static final int NONE = -1;
    /** {@link Info#ore()} of a column in a chunk that is not loaded on the server. */
    public static final int NOT_LOADED = -2;

    private static final Map<UUID, RequestBudget> BUDGETS = new ConcurrentHashMap<>();

    private OreFieldPayloads() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BUDGETS.remove(e.getEntity().getUUID()));
    }

    /** Client to server: what is the ore field at column (x, z)? */
    public record Query(int x, int z) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ore_field_query"));
        public static final StreamCodec<ByteBuf, Query> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Query::x,
                ByteBufCodecs.VAR_INT, Query::z,
                Query::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Query q, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RequestBudget budget = BUDGETS.computeIfAbsent(player.getUUID(), u -> new RequestBudget(BURST, TICKS_PER_QUERY));
            if (!budget.tryTake(player.server.getTickCount())) return;
            ServerLevel overworld = player.server.overworld();
            if (!overworld.getWorldBorder().isWithinBounds(q.x, q.z)) return;
            if (!WorldMap.get(player.server).isExplored(ChunkKeys.of(q.x >> 4, q.z >> 4))) return;
            PacketDistributor.sendToPlayer(player, lookup(overworld, q.x, q.z));
        }
    }

    /**
     * Server to client: column (x, z) holds a field block of {@code OreType.values()[ore]} with
     * {@code remaining} ore, or {@code ore} is {@link #NONE} / {@link #NOT_LOADED}.
     */
    public record Info(int x, int z, int ore, long remaining) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ore_field_info"));
        public static final StreamCodec<ByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Info::x,
                ByteBufCodecs.VAR_INT, Info::z,
                ByteBufCodecs.VAR_INT, Info::ore,
                ByteBufCodecs.VAR_LONG, Info::remaining,
                Info::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        /** The ore type, or null for {@link #NONE}, {@link #NOT_LOADED} or an unknown value. */
        @Nullable
        public OreType oreType() {
            return ore >= 0 && ore < OreType.values().length ? OreType.values()[ore] : null;
        }

        public static void handle(Info info, IPayloadContext context) {
            ClientOreInfo.accept(info);
        }
    }

    /** The answer about column (x, z) of {@code level}, from loaded chunks only. */
    public static Info lookup(ServerLevel level, int x, int z) {
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return new Info(x, z, NOT_LOADED, 0);
        BlockPos pos = OreFields.surfaceField(level, x, z);
        OreType type = pos == null ? null : OreFields.typeOf(level.getBlockState(pos));
        return type == null ? new Info(x, z, NONE, 0) : new Info(x, z, type.ordinal(), OreFields.remaining(level, pos));
    }
}
