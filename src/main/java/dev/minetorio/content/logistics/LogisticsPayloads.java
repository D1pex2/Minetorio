package dev.minetorio.content.logistics;

import dev.minetorio.Minetorio;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Logistic network payloads (handlers run on the main thread). */
public final class LogisticsPayloads {
    private static final String VERSION = "1";
    /** Most request entries / network item lines accepted from the network. */
    private static final int MAX_ENTRIES = 64;

    private LogisticsPayloads() {}

    static void register(IEventBus modBus) {
        modBus.addListener(LogisticsPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(OpenPersonal.TYPE, OpenPersonal.STREAM_CODEC, OpenPersonal::handle)
                .playToServer(EditRequest.TYPE, EditRequest.STREAM_CODEC, EditRequest::handle)
                .playToClient(RequestSync.TYPE, RequestSync.STREAM_CODEC, RequestSync::handle)
                .playToClient(NetworkStats.TYPE, NetworkStats.STREAM_CODEC, NetworkStats::handle);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path); }

    /** Client to server: open the personal logistics screen. */
    public record OpenPersonal() implements CustomPacketPayload {
        public static final Type<OpenPersonal> TYPE = new Type<>(id("open_personal_logistics"));
        public static final StreamCodec<ByteBuf, OpenPersonal> STREAM_CODEC = StreamCodec.unit(new OpenPersonal());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        static void handle(OpenPersonal p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer sp && sp.isAlive()) {
                sp.openMenu(new SimpleMenuProvider((id, inventory, pl) -> new PersonalLogisticsMenu(id, inventory),
                        Component.translatable("minetorio.personal_logistics.title")));
            }
        }
    }

    /** Client to server: counts for request slot {@code slot} of the open request menu ({@code max} -1 = none). */
    public record EditRequest(int containerId, int slot, int min, int max) implements CustomPacketPayload {
        public static final Type<EditRequest> TYPE = new Type<>(id("edit_request"));
        public static final StreamCodec<ByteBuf, EditRequest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EditRequest::containerId,
                ByteBufCodecs.VAR_INT, EditRequest::slot,
                ByteBufCodecs.VAR_INT, EditRequest::min,
                ByteBufCodecs.INT, EditRequest::max,
                EditRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        static void handle(EditRequest p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer sp && sp.containerMenu instanceof RequestMenu menu
                    && menu.containerId == p.containerId && menu.stillValid(sp)) {
                menu.editCounts(p.slot, p.min, p.max);
            }
        }
    }

    /** Server to client: the request table (and toggle) of the open request menu. */
    public record RequestSync(int containerId, List<RequestNbt.Entry> entries, boolean toggle) implements CustomPacketPayload {
        public static final Type<RequestSync> TYPE = new Type<>(id("request_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RequestSync::containerId,
                RequestNbt.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), RequestSync::entries,
                ByteBufCodecs.BOOL, RequestSync::toggle,
                RequestSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        static void handle(RequestSync p, IPayloadContext context) {
            if (context.player().containerMenu instanceof RequestMenu menu && menu.containerId == p.containerId) {
                menu.applySync(p.entries, p.toggle);
            }
        }
    }

    /** One item line of a network's contents. */
    public record ItemCount(String item, int count) {
        public static final StreamCodec<ByteBuf, ItemCount> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), ItemCount::item,
                ByteBufCodecs.VAR_INT, ItemCount::count,
                ItemCount::new);
    }

    /**
     * Server to client: the logistic network of the roboport whose GUI is open ({@code containerId}):
     * robots available and in total by kind, roboports, and the items in the network (most first).
     */
    public record NetworkStats(int containerId, int roboports, int logisticAvailable, int logisticTotal,
                               int constructionAvailable, int constructionTotal, List<ItemCount> items)
            implements CustomPacketPayload {
        public static final Type<NetworkStats> TYPE = new Type<>(id("network_stats"));
        public static final StreamCodec<ByteBuf, NetworkStats> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public NetworkStats decode(ByteBuf buf) {
                int id = ByteBufCodecs.VAR_INT.decode(buf);
                int ports = ByteBufCodecs.VAR_INT.decode(buf);
                int la = ByteBufCodecs.VAR_INT.decode(buf), lt = ByteBufCodecs.VAR_INT.decode(buf);
                int ca = ByteBufCodecs.VAR_INT.decode(buf), ct = ByteBufCodecs.VAR_INT.decode(buf);
                int n = Math.min(MAX_ENTRIES, ByteBufCodecs.VAR_INT.decode(buf));
                List<ItemCount> items = new ArrayList<>(n);
                for (int i = 0; i < n; i++) items.add(ItemCount.STREAM_CODEC.decode(buf));
                return new NetworkStats(id, ports, la, lt, ca, ct, items);
            }

            @Override
            public void encode(ByteBuf buf, NetworkStats s) {
                ByteBufCodecs.VAR_INT.encode(buf, s.containerId);
                ByteBufCodecs.VAR_INT.encode(buf, s.roboports);
                ByteBufCodecs.VAR_INT.encode(buf, s.logisticAvailable);
                ByteBufCodecs.VAR_INT.encode(buf, s.logisticTotal);
                ByteBufCodecs.VAR_INT.encode(buf, s.constructionAvailable);
                ByteBufCodecs.VAR_INT.encode(buf, s.constructionTotal);
                int n = Math.min(MAX_ENTRIES, s.items.size());
                ByteBufCodecs.VAR_INT.encode(buf, n);
                for (int i = 0; i < n; i++) ItemCount.STREAM_CODEC.encode(buf, s.items.get(i));
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        static void handle(NetworkStats p, IPayloadContext context) {
            NetworkStatsView.set(p);
        }
    }
}
