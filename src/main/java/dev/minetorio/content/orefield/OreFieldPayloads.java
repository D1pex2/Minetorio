package dev.minetorio.content.orefield;

import dev.minetorio.Minetorio;
import dev.minetorio.content.map.WorldMap;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.RequestBudget;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.OreType;
import dev.minetorio.core.orefield.PatchLedger;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
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
 * and the whole patch it belongs to with that patch's remaining ore ({@link Info}). Amounts live only on the server ({@link OreFieldData}), hence the round trip.
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
     * {@code remaining} ore, or {@code ore} is {@link #NONE} / {@link #NOT_LOADED}. If the block
     * belongs to a patch ({@code patch != 0}, its {@code PatchLedger} key), the patch is the disk of
     * {@code radius} around ({@code centerX}, {@code centerZ}) of the same ore, {@code total} ore left
     * in all of it.
     */
    public record Info(int x, int z, int ore, long remaining, long patch, int centerX, int centerZ, int radius, long total)
            implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "ore_field_info"));
        public static final StreamCodec<ByteBuf, Info> STREAM_CODEC = StreamCodec.of((buf, i) -> {
            VarInt.write(buf, i.x);
            VarInt.write(buf, i.z);
            VarInt.write(buf, i.ore);
            VarLong.write(buf, i.remaining);
            buf.writeLong(i.patch);
            if (i.patch != 0) {
                VarInt.write(buf, i.centerX);
                VarInt.write(buf, i.centerZ);
                VarInt.write(buf, i.radius);
                VarLong.write(buf, i.total);
            }
        }, buf -> {
            int x = VarInt.read(buf), z = VarInt.read(buf), ore = VarInt.read(buf);
            long remaining = VarLong.read(buf), patch = buf.readLong();
            if (patch == 0) return new Info(x, z, ore, remaining);
            return new Info(x, z, ore, remaining, patch, VarInt.read(buf), VarInt.read(buf), VarInt.read(buf), VarLong.read(buf));
        });

        /** An answer without a patch. */
        public Info(int x, int z, int ore, long remaining) {
            this(x, z, ore, remaining, 0, 0, 0, 0, 0);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        /** The ore type, or null for {@link #NONE}, {@link #NOT_LOADED} or an unknown value. */
        @Nullable
        public OreType oreType() {
            return ore >= 0 && ore < OreType.values().length ? OreType.values()[ore] : null;
        }

        /** The patch's disk, or null without a patch (or with an unknown ore). */
        @Nullable
        public OrePatch patchShape() {
            OreType t = oreType();
            return patch == 0 || t == null ? null : new OrePatch(t, centerX, centerZ, radius);
        }

        public static void handle(Info info, IPayloadContext context) {
            ClientOreInfo.accept(info);
        }
    }

    /** The answer about column (x, z) of {@code level}, from loaded chunks only (the patch total needs none: {@link OrePatchLedger}). */
    public static Info lookup(ServerLevel level, int x, int z) {
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return new Info(x, z, NOT_LOADED, 0);
        BlockPos pos = OreFields.surfaceField(level, x, z);
        OreType type = pos == null ? null : OreFields.typeOf(level.getBlockState(pos));
        if (type == null) return new Info(x, z, NONE, 0);
        long remaining = OreFields.remaining(level, pos);
        PatchLedger.Entry e = OrePatchLedger.get(level).patchAt(level, pos, type);
        if (e == null) return new Info(x, z, type.ordinal(), remaining);
        OrePatch p = e.patch();
        return new Info(x, z, type.ordinal(), remaining, e.key(), p.centerX(), p.centerZ(), p.radius(), e.total());
    }
}
