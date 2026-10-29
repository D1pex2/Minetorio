package dev.minetorio.content.blueprint;

import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.nbt.CopyableSettings;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.content.registry.ModDataComponents;
import dev.minetorio.content.robot.GhostBlock;
import dev.minetorio.content.robot.GhostBlockEntity;
import dev.minetorio.core.blueprint.BlueprintBooks;
import dev.minetorio.core.blueprint.BlueprintPart;
import dev.minetorio.core.blueprint.Blueprints;
import dev.minetorio.core.drag.DragDir;
import dev.minetorio.core.robot.RateBudget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Copying an area into an empty blueprint (server side). The client sends the box's two corners
 * ({@link BlueprintPayloads.Capture}); with {@code autoHeight} (the top-down rectangle) only the
 * columns count and the box runs from the lowest to the highest copyable block in them. Every
 * copyable block ({@link BlueprintRules#capturable}) touching the box is copied once by its
 * origin (a multiblock by its controller, a pole by its base, a ghost as what it stands for):
 * block, position relative to the copy, placement facing, an underground's entrance/exit and the
 * settings ({@link CopyableSettings}: recipe, filters). Wires between copied poles come back by
 * themselves (poles connect to every pole in wire reach). Contents are never copied.
 *
 * <p>Checks: an empty blueprint in the main hand, both corners within ghost reach
 * ({@link RemoteView#canReachGhost}), the box within {@link Blueprints#MAX_SPAN} ×
 * {@link Blueprints#MAX_HEIGHT}, the result within the limits, and a request budget.
 */
public final class BlueprintCapture {
    static final int BURST = 4;
    static final double REFILL_PER_TICK = 0.1;
    private static final Map<UUID, RateBudget> BUDGETS = new HashMap<>();

    private BlueprintCapture() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BUDGETS.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> BUDGETS.clear());
        // an empty blueprint is free: dropped, it vanishes
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getEntity() instanceof ItemEntity item
                    && (BlueprintItem.isEmptyBlueprint(item.getItem()) || BlueprintBookItem.isEmptyBook(item.getItem()))) {
                e.setCanceled(true);
            }
        });
    }

    static void resetBudget(Player player) {
        BUDGETS.remove(player.getUUID());
    }

    public enum Result { OK, NOT_ALLOWED, NO_BLUEPRINT, TOO_FAR, TOO_BIG, NOTHING, TOO_SOON }

    /** Copies the box between {@code a} and {@code b} (inclusive) into the empty blueprint in {@code player}'s main hand. */
    public static Result capture(ServerPlayer player, BlockPos a, BlockPos b, boolean autoHeight) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || !player.isAlive()) return Result.NOT_ALLOWED;
        ItemStack hand = player.getMainHandItem();
        if (!BlueprintItem.isEmptyBlueprint(hand)) return Result.NO_BLUEPRINT;
        if (!RemoteView.canReachGhost(player, a) || !RemoteView.canReachGhost(player, b)) return Result.TOO_FAR;
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        if (x1 - x0 >= Blueprints.MAX_SPAN || z1 - z0 >= Blueprints.MAX_SPAN || (!autoHeight && y1 - y0 >= Blueprints.MAX_HEIGHT)) {
            player.displayClientMessage(Component.translatable("minetorio.blueprint.too_big", Blueprints.MAX_SPAN, Blueprints.MAX_SPAN, Blueprints.MAX_HEIGHT), true);
            return Result.TOO_BIG;
        }
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return Result.TOO_FAR;
            }
        }
        if (!BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(BURST, REFILL_PER_TICK)).take(player.server.getTickCount(), 1)) {
            return Result.TOO_SOON;
        }
        if (autoHeight) {
            int[] ys = heightRange(level, x0, x1, z0, z1);
            if (ys == null) return nothing(player);
            if (ys[1] - ys[0] >= Blueprints.MAX_HEIGHT) {
                player.displayClientMessage(Component.translatable("minetorio.blueprint.too_big", Blueprints.MAX_SPAN, Blueprints.MAX_SPAN, Blueprints.MAX_HEIGHT), true);
                return Result.TOO_BIG;
            }
            y0 = ys[0];
            y1 = ys[1];
        }
        BlueprintData data = scan(level, x0, y0, z0, x1, y1, z1);
        if (data == null) return nothing(player);
        if (!data.valid()) {
            player.displayClientMessage(Component.translatable("minetorio.blueprint.too_big", Blueprints.MAX_SPAN, Blueprints.MAX_SPAN, Blueprints.MAX_HEIGHT), true);
            return Result.TOO_BIG;
        }
        hand.set(ModDataComponents.BLUEPRINT.get(), data);
        BlueprintMeta meta = BlueprintStacks.meta(hand);
        if (meta.icons().isEmpty()) BlueprintStacks.setMeta(hand, meta.withIcons(defaultIcons(data)));
        player.displayClientMessage(Component.translatable("minetorio.blueprint.captured", data.entries().size()), true);
        return Result.OK;
    }

    /** Factorio's default icons: the items of the most frequent parts (up to four). */
    public static List<ResourceLocation> defaultIcons(BlueprintData data) {
        Map<String, Integer> items = new LinkedHashMap<>();
        data.counts().forEach((block, n) -> {
            Item item = BlueprintData.block(block).asItem();
            if (item != Items.AIR) items.merge(BuiltInRegistries.ITEM.getKey(item).toString(), n, Integer::sum);
        });
        List<ResourceLocation> out = new ArrayList<>();
        for (String id : BlueprintBooks.defaultIcons(items)) out.add(ResourceLocation.parse(id));
        return out;
    }

    private static Result nothing(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("minetorio.blueprint.nothing"), true);
        return Result.NOTHING;
    }

    /** {lowest, highest} y of a copyable block in the columns, or null; empty chunk sections are skipped. */
    @Nullable
    static int[] heightRange(ServerLevel level, int x0, int x1, int z0, int z1) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    if (sections[si].hasOnlyAir()) continue;
                    int baseY = level.getSectionYFromSectionIndex(si) << 4;
                    for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
                        for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
                            for (int dy = 0; dy < 16; dy++) {
                                BlockState s = sections[si].getBlockState(x & 15, dy, z & 15);
                                if (copyable(s)) {
                                    lo = Math.min(lo, baseY + dy);
                                    hi = Math.max(hi, baseY + dy);
                                }
                            }
                        }
                    }
                }
            }
        }
        return lo == Integer.MAX_VALUE ? null : new int[] {lo, hi};
    }

    private static boolean copyable(BlockState s) {
        Block b = s.getBlock();
        return b instanceof GhostBlock || b instanceof MultiblockPartBlock || BlueprintRules.capturable(b);
    }

    /** The blueprint of the box (inclusive), or null when nothing in it is copyable. */
    @Nullable
    public static BlueprintData scan(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        Set<BlockPos> origins = new LinkedHashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            BlockPos o = origin(level, p);
            if (o != null) origins.add(o);
        }
        List<BlueprintData.Entry> entries = new ArrayList<>();
        List<BlueprintPart> parts = new ArrayList<>();
        for (BlockPos o : origins) {
            BlueprintData.Entry e = entry(level, o);
            if (e == null) continue;
            int[] f = BlueprintData.footprint(BlueprintData.block(e.block()));
            entries.add(e);
            parts.add(new BlueprintPart(e.block(), e.x(), e.y(), e.z(), e.dir(), f[0], f[1], f[2], e.variant()));
        }
        if (entries.isEmpty()) return null;
        // normalize: world coordinates to blueprint space
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlueprintPart p : parts) {
            minX = Math.min(minX, p.minX());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.minZ());
        }
        List<BlueprintData.Entry> out = new ArrayList<>(entries.size());
        for (BlueprintData.Entry e : entries) {
            int x = e.x() - minX, y = e.y() - minY, z = e.z() - minZ; // the box limits keep these far below 256
            out.add(new BlueprintData.Entry(e.block(), x, y, z, e.facing(), e.variant(), e.settings()));
        }
        return new BlueprintData(out);
    }

    /** The origin of the copyable thing at {@code pos}: a multiblock's controller, a pole's base, the cell itself; null if none. */
    @Nullable
    private static BlockPos origin(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        Block b = s.getBlock();
        if (b instanceof MultiblockPartBlock) {
            BlockPos c = MultiblockPartBlock.controllerPos(level, pos);
            return c == null ? null : c.immutable();
        }
        if (b instanceof ElectricPoleBlock) return pos.below(s.getValue(ElectricPoleBlock.PART)).immutable();
        if (b instanceof GhostBlock) return GhostBlock.at(level, pos) != null && GhostBlock.at(level, pos).isValid() ? pos.immutable() : null;
        return BlueprintRules.capturable(b) ? pos.immutable() : null;
    }

    /** The entry (world coordinates) for the thing whose origin is {@code o}, or null. */
    @Nullable
    private static BlueprintData.Entry entry(BlockGetter level, BlockPos o) {
        BlockState s = level.getBlockState(o);
        Direction facing = null;
        CompoundTag settings = null;
        if (s.getBlock() instanceof GhostBlock) {
            GhostBlockEntity g = GhostBlock.at(level, o);
            if (g == null || !g.isValid()) return null;
            settings = g.settings();
            BlockState t = g.target();
            facing = t.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? t.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
            s = t;
        } else if (level.getBlockEntity(o) instanceof CopyableSettings copy) {
            settings = copy.saveSettings();
        }
        if (!BlueprintRules.capturable(s.getBlock())) return null;
        if (facing == null && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) facing = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        int variant = s.hasProperty(UndergroundBeltBlock.TYPE) && s.getValue(UndergroundBeltBlock.TYPE) == UndergroundBeltBlock.Type.OUTPUT ? 1 : 0;
        if (facing == null && s.hasProperty(BlockStateProperties.FACING)) {
            // six-way blocks (underground pipes): a horizontal facing as usual, up/down as a variant
            Direction f = s.getValue(BlockStateProperties.FACING);
            if (f.getAxis().isHorizontal()) facing = f;
            else variant = BlueprintPaste.verticalVariant(f);
        }
        Optional<CompoundTag> st = settings == null || settings.isEmpty() || settings.sizeInBytes() > Blueprints.MAX_SETTINGS_BYTES
                ? Optional.empty() : Optional.of(settings);
        return new BlueprintData.Entry(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), o.getX(), o.getY(), o.getZ(),
                facing == null ? -1 : DragDir.valueOf(facing.name()).id(), variant, st);
    }

    // --- the free empty blueprint ---------------------------------------------------------------

    public enum GiveResult { OK, NOT_ALLOWED, NO_ROOM, TOO_SOON }

    /**
     * The blueprint shortcut: an empty blueprint (or book) into the main hand (Factorio's shortcut bar). An
     * empty hand takes it; else a free hotbar slot is selected; else the held item moves to a free
     * inventory slot. Refused when the inventory is full.
     */
    public static GiveResult give(ServerPlayer player) {
        return give(player, BlueprintItem.empty());
    }

    /** {@link #give(ServerPlayer)} with {@code fresh} (an empty blueprint or book) instead. */
    public static GiveResult give(ServerPlayer player, ItemStack fresh) {
        if (player.isSpectator() || !player.isAlive()) return GiveResult.NOT_ALLOWED;
        if (!BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(BURST, REFILL_PER_TICK)).take(player.server.getTickCount(), 1)) {
            return GiveResult.TOO_SOON;
        }
        Inventory inv = player.getInventory();
        if (!inv.items.get(inv.selected).isEmpty()) {
            int free = -1;
            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                if (inv.items.get(i).isEmpty()) {
                    free = i;
                    break;
                }
            }
            if (free >= 0) {
                inv.selected = free;
                player.connection.send(new ClientboundSetCarriedItemPacket(free));
            } else {
                int slot = inv.getFreeSlot();
                if (slot < 0) return GiveResult.NO_ROOM;
                inv.setItem(slot, inv.items.get(inv.selected));
            }
        }
        inv.setItem(inv.selected, fresh);
        ((dev.minetorio.content.character.CharacterShortcuts) player).emptyHand(false);
        inv.setChanged();
        player.inventoryMenu.broadcastChanges();
        return GiveResult.OK;
    }
}
