package dev.minetorio.content.robot;

import dev.minetorio.Minetorio;
import dev.minetorio.core.robot.UpgradeRules;
import dev.minetorio.core.selection.SelectionBox;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: planner actions ({@link Planners}; registered in {@code ModPayloads}). */
public final class PlannerPayloads {
    private PlannerPayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, path); }

    /** Apply the held planner to the box {@code min..max}; {@code cancel} = Shift (unmark). */
    public record Select(BlockPos min, BlockPos max, boolean cancel) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(id("planner_select"));
        public static final StreamCodec<ByteBuf, Select> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Select::min, BlockPos.STREAM_CODEC, Select::max, ByteBufCodecs.BOOL, Select::cancel, Select::new);

        public static Select of(SelectionBox box, boolean cancel) {
            return new Select(new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()), cancel);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Select p, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;
            SelectionBox box = SelectionBox.of(p.min().getX(), p.min().getY(), p.min().getZ(), p.max().getX(), p.max().getY(), p.max().getZ());
            Planners.select(player, box, p.cancel());
        }
    }

    /** Set the held deconstruction planner's filter. */
    public record SetFilter(int filter) implements CustomPacketPayload {
        public static final Type<SetFilter> TYPE = new Type<>(id("planner_filter"));
        public static final StreamCodec<ByteBuf, SetFilter> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SetFilter::filter, SetFilter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SetFilter p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) Planners.setFilter(player, p.filter());
        }
    }

    /** Set the held upgrade planner's rules. */
    public record SetRules(List<UpgradeRules.Rule> rules) implements CustomPacketPayload {
        public static final Type<SetRules> TYPE = new Type<>(id("planner_rules"));
        public static final StreamCodec<ByteBuf, SetRules> STREAM_CODEC = StreamCodec.composite(
                PlannerItems.RULE_STREAM_CODEC.apply(ByteBufCodecs.list(UpgradeRules.MAX_RULES)), SetRules::rules, SetRules::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SetRules p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) Planners.setRules(player, p.rules());
        }
    }

    /** The shortcut key: a planner of kind {@code kind} ({@link Planners.Kind} ordinal) into the hand. */
    public record Give(int kind) implements CustomPacketPayload {
        public static final Type<Give> TYPE = new Type<>(id("planner_give"));
        public static final StreamCodec<ByteBuf, Give> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Give::kind, Give::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(Give p, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Planners.Kind[] kinds = Planners.Kind.values();
            if (p.kind() >= 0 && p.kind() < kinds.length) Planners.give(player, kinds[p.kind()]);
        }
    }
}
