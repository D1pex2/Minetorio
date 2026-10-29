package dev.minetorio.content.combat;

import dev.minetorio.Minetorio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CombatPayloads {
    private CombatPayloads() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Intent.TYPE, Intent.CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer player) {
                CombatManager.cameraBlocked(player, p.cameraBlocked());
                if (p.fire()) CombatManager.fire(player);
            }
        }).playToClient(Shot.TYPE, Shot.CODEC, (p, c) -> dev.minetorio.client.combat.CombatClient.shot(p));
    }
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, id));
    }
    /** Ни координат, ни цели, ни урона от клиента не принимается. */
    public record Intent(boolean fire, boolean cameraBlocked) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = CombatPayloads.type("combat_intent");
        public static final StreamCodec<ByteBuf, Intent> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Intent::fire,
                ByteBufCodecs.BOOL, Intent::cameraBlocked, Intent::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Shot(int shooter, double x, double y, double z, double hitX, double hitY, double hitZ, boolean hit, boolean automatic) implements CustomPacketPayload {
        public static final Type<Shot> TYPE = CombatPayloads.type("combat_shot");
        public static final StreamCodec<ByteBuf, Shot> CODEC = new StreamCodec<>() {
            @Override public Shot decode(ByteBuf b) { return new Shot(b.readInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean(), b.readBoolean()); }
            @Override public void encode(ByteBuf b, Shot p) { b.writeInt(p.shooter); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeDouble(p.hitX); b.writeDouble(p.hitY); b.writeDouble(p.hitZ); b.writeBoolean(p.hit); b.writeBoolean(p.automatic); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
