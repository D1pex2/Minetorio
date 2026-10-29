package dev.minetorio.content.stats;

import dev.minetorio.Minetorio;
import dev.minetorio.core.stats.ProductionLedger;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded read-only statistics protocol; clients never submit counts or change the ledger. */
public final class StatsPayloads {
    public static final int MAX_ROWS=256;
    public static final int[] WINDOWS={1200,12000,72000,0};
    private static final Map<UUID,Long> REQUEST_TICKS=new HashMap<>();
    public static volatile java.util.function.Consumer<Snapshot> clientHandler=snapshot->{};
    private StatsPayloads(){}
    public static void register(IEventBus bus) {
        bus.addListener((RegisterPayloadHandlersEvent event)->event.registrar("1").playToServer(Request.TYPE,Request.CODEC,Request::handle).playToClient(Snapshot.TYPE,Snapshot.CODEC,Snapshot::handle));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->REQUEST_TICKS.clear());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->REQUEST_TICKS.remove(event.getEntity().getUUID()));
    }
    public record Row(String id,double produced,double consumed) {}
    public record Request(int window,boolean fluids,String selected) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID,"stats_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((buf,p)->{buf.writeVarInt(p.window);buf.writeBoolean(p.fluids);buf.writeUtf(p.selected,128);},buf->new Request(buf.readVarInt(),buf.readBoolean(),buf.readUtf(128)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
        static void handle(Request request,IPayloadContext context) {if(context.player() instanceof ServerPlayer player)send(player,request);}
    }
    public record Snapshot(int window,boolean fluids,long tick,List<Row> rows,String selected,double[] produced,double[] consumed) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID,"stats_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((buf,p)->{
            buf.writeVarInt(p.window);buf.writeBoolean(p.fluids);buf.writeLong(p.tick);buf.writeVarInt(p.rows.size());
            for(Row row:p.rows){buf.writeUtf(row.id,128);buf.writeDouble(row.produced);buf.writeDouble(row.consumed);}
            buf.writeUtf(p.selected,128);for(double n:p.produced)buf.writeDouble(n);for(double n:p.consumed)buf.writeDouble(n);
        },buf->{
            int window=buf.readVarInt();boolean fluids=buf.readBoolean();long tick=buf.readLong();int count=buf.readVarInt();
            if(window<0 || window>=WINDOWS.length || count<0 || count>MAX_ROWS)throw new IllegalArgumentException("Invalid statistics packet");
            List<Row> rows=new ArrayList<>();for(int i=0;i<count;i++){String id=buf.readUtf(128);double produced=buf.readDouble(),consumed=buf.readDouble();if(!Double.isFinite(produced)||!Double.isFinite(consumed)||produced<0||consumed<0)throw new IllegalArgumentException("Invalid statistics count");rows.add(new Row(id,produced,consumed));}
            String selected=buf.readUtf(128);double[] produced=new double[60],consumed=new double[60];for(int i=0;i<60;i++)produced[i]=buf.readDouble();for(int i=0;i<60;i++)consumed[i]=buf.readDouble();
            return new Snapshot(window,fluids,tick,List.copyOf(rows),selected,produced,consumed);
        });
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
        static void handle(Snapshot snapshot,IPayloadContext context){clientHandler.accept(snapshot);}
    }
    public static boolean send(ServerPlayer player,Request request) {
        if(!player.isAlive() || request.window<0 || request.window>=WINDOWS.length || request.selected.length()>128)return false;
        long now=player.server.overworld().getGameTime();Long last=REQUEST_TICKS.get(player.getUUID());if(last!=null && now-last<20)return false;REQUEST_TICKS.put(player.getUUID(),now);
        ProductionLedger ledger=Statistics.get(player.server).ledger();int window=WINDOWS[request.window];
        List<Row> rows=ledger.snapshot(now,window).entrySet().stream().filter(e->e.getKey().startsWith("fluid:")==request.fluids)
            .map(e->new Row(e.getKey(),e.getValue().produced(),e.getValue().consumed()))
            .sorted(Comparator.<Row>comparingDouble(row->row.produced+row.consumed).reversed().thenComparing(Row::id)).limit(MAX_ROWS).toList();
        String selected=rows.stream().anyMatch(row->row.id.equals(request.selected))?request.selected:rows.isEmpty()?"":rows.getFirst().id;
        PacketDistributor.sendToPlayer(player,new Snapshot(request.window,request.fluids,now,rows,selected,ledger.series(now,window,selected,true),ledger.series(now,window,selected,false)));return true;
    }
}
