package dev.minetorio.content.oilmap;

import dev.minetorio.Minetorio;
import dev.minetorio.content.map.WorldMap;
import dev.minetorio.core.map.ChunkKeys;
import dev.minetorio.core.map.RequestBudget;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded map queries read the saved ledger without loading resource chunks. */
public final class OilMapPayloads {
    private OilMapPayloads() {}
    private static final Map<UUID, RequestBudget> BUDGETS = new ConcurrentHashMap<>();
    private static final Map<Long, Info> CLIENT = new ConcurrentHashMap<>();
    public static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToServer(Query.TYPE, Query.CODEC, Query::handle)
                .playToClient(Info.TYPE, Info.CODEC, Info::handle));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BUDGETS.remove(e.getEntity().getUUID()));
    }
    public static Info cached(int x, int z) { return CLIENT.get(ChunkKeys.of(x, z)); }
    public static void clearClient() { CLIENT.clear(); }
    public record Query(int x, int z) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "oil_map_query"));
        public static final StreamCodec<ByteBuf, Query> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Query::x, ByteBufCodecs.VAR_INT, Query::z, Query::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(Query q, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!BUDGETS.computeIfAbsent(player.getUUID(), u -> new RequestBudget(8, 2)).tryTake(player.server.getTickCount())) return;
            ServerLevel level = player.server.overworld();
            if (!level.getWorldBorder().isWithinBounds(q.x, q.z)
                    || !WorldMap.get(player.server).isExplored(ChunkKeys.of(q.x >> 4, q.z >> 4))) return;
            PacketDistributor.sendToPlayer(player, lookup(level, q.x, q.z));
        }
    }
    public record Info(int x, int z, double yield, double total, int count) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "oil_map_info"));
        public static final StreamCodec<ByteBuf, Info> CODEC = StreamCodec.of((buf, i) -> {
            buf.writeInt(i.x); buf.writeInt(i.z); buf.writeDouble(i.yield); buf.writeDouble(i.total); buf.writeInt(i.count);
        }, buf -> new Info(buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(Info i, IPayloadContext context) {
            if (CLIENT.size() > 512) CLIENT.clear();
            CLIENT.put(ChunkKeys.of(i.x, i.z), i);
        }
    }
    public static Info lookup(ServerLevel level, int x, int z) {
        OilMapData data = OilMapData.get(level);
        OilMapData.Well well = data.at(x, z);
        return well == null ? new Info(x, z, 0, 0, 0) : new Info(x, z, well.yield(), data.total(well.field()), data.count(well.field()));
    }
}
