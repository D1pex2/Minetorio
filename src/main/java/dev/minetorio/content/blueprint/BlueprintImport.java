package dev.minetorio.content.blueprint;

import dev.minetorio.core.blueprint.BlueprintStrings;
import dev.minetorio.core.robot.RateBudget;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * A blueprint or book handed to the server by a client: taken from the blueprint library or
 * imported from a blueprint string. The client sends the record's deflated bytes (the body of a
 * blueprint string, {@link BlueprintStrings}) in chunks ({@link BlueprintPayloads.Upload}); the
 * server inflates them with a size limit and decodes them exactly as a blueprint string
 * ({@link BlueprintRecord#fromBytes}: the same limits as the item components), then puts the new
 * item into the requested empty inventory slot, else the empty main hand, else a free slot.
 * A request budget per player bounds how often.
 */
public final class BlueprintImport {
    /** Bytes per upload chunk (well under a serverbound custom payload's 32 KiB). */
    public static final int CHUNK = 30000;
    /** Most chunks of one upload. */
    public static final int MAX_CHUNKS = (BlueprintStrings.MAX_COMPRESSED_BYTES + CHUNK - 1) / CHUNK;
    static final int BURST = 4;
    static final double REFILL_PER_TICK = 0.05;

    private static final Map<UUID, RateBudget> BUDGETS = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private BlueprintImport() {}

    private static final class Pending {
        final int transfer, total, slot;
        int received;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Pending(int transfer, int total, int slot) {
            this.transfer = transfer;
            this.total = total;
            this.slot = slot;
        }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            BUDGETS.remove(e.getEntity().getUUID());
            PENDING.remove(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            BUDGETS.clear();
            PENDING.clear();
        });
    }

    /** Forgets {@code player}'s budget and pending upload (tests share one fake player). */
    public static void reset(Player player) {
        BUDGETS.remove(player.getUUID());
        PENDING.remove(player.getUUID());
    }

    public enum Result { OK, PENDING, NOT_ALLOWED, TOO_SOON, BAD, NO_ROOM }

    /** One upload chunk from {@code player}'s client. */
    public static Result chunk(ServerPlayer player, int transfer, int index, int total, int slot, byte[] data) {
        UUID id = player.getUUID();
        if (index == 0) {
            PENDING.remove(id);
            if (!player.isAlive() || player.isSpectator()) return Result.NOT_ALLOWED;
            if (total < 1 || total > MAX_CHUNKS) return refuse(player, Result.BAD);
            if (!budget(player)) return refuse(player, Result.TOO_SOON);
            PENDING.put(id, new Pending(transfer, total, slot));
        }
        Pending p = PENDING.get(id);
        if (p == null || p.transfer != transfer || p.received != index || p.total != total || p.slot != slot) {
            PENDING.remove(id);
            return Result.BAD;
        }
        if (p.bytes.size() + data.length > BlueprintStrings.MAX_COMPRESSED_BYTES) {
            PENDING.remove(id);
            return refuse(player, Result.BAD);
        }
        p.bytes.writeBytes(data);
        p.received++;
        if (p.received < p.total) return Result.PENDING;
        PENDING.remove(id);
        return accept(player, p.bytes.toByteArray(), p.slot);
    }

    private static boolean budget(ServerPlayer player) {
        return BUDGETS.computeIfAbsent(player.getUUID(), u -> new RateBudget(BURST, REFILL_PER_TICK)).take(player.server.getTickCount(), 1);
    }

    /** A whole blueprint string from {@code player} (what the client's import does, in one piece; budgeted). */
    public static Result importString(ServerPlayer player, String s, int slot) {
        if (!player.isAlive() || player.isSpectator()) return Result.NOT_ALLOWED;
        if (!budget(player)) return refuse(player, Result.TOO_SOON);
        byte[] packed;
        try {
            packed = BlueprintStrings.unwrap(s);
        } catch (BlueprintStrings.BadBlueprintString e) {
            return refuse(player, Result.BAD);
        }
        return accept(player, packed, slot);
    }

    /** Decodes the deflated record {@code packed} and gives it to {@code player} (into {@code slot} if empty). */
    static Result accept(ServerPlayer player, byte[] packed, int slot) {
        if (!player.isAlive() || player.isSpectator()) return Result.NOT_ALLOWED;
        BlueprintRecord r;
        try {
            r = BlueprintRecord.fromBytes(BlueprintStrings.inflate(packed));
        } catch (BlueprintStrings.BadBlueprintString e) {
            return refuse(player, Result.BAD);
        }
        ItemStack stack = BlueprintStacks.stack(r);
        Inventory inv = player.getInventory();
        int to;
        if (slot >= 0 && slot < Inventory.INVENTORY_SIZE && inv.getItem(slot).isEmpty()) to = slot;
        else if (inv.getSelected().isEmpty()) to = inv.selected;
        else to = inv.getFreeSlot();
        if (to < 0 || to >= Inventory.INVENTORY_SIZE) return refuse(player, Result.NO_ROOM);
        inv.setItem(to, stack);
        inv.setChanged();
        player.inventoryMenu.broadcastChanges();
        return Result.OK;
    }

    private static Result refuse(ServerPlayer player, Result r) {
        String key = switch (r) {
            case TOO_SOON -> "minetorio.blueprint.import.too_soon";
            case NO_ROOM -> "minetorio.blueprint.import.no_room";
            default -> "minetorio.blueprint.import.bad";
        };
        player.displayClientMessage(Component.translatable(key), true);
        return r;
    }
}
