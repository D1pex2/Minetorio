package dev.minetorio.content.handcraft;

import dev.minetorio.content.registry.ModAttachments;
import dev.minetorio.content.research.RecipeLocks;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.handcraft.HandcraftQueue;
import dev.minetorio.core.recipe.Recipe;
import dev.minetorio.core.recipe.RecipeCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of hand crafting. Each server player's queue ({@link HandcraftAttachment}) advances on
 * {@link PlayerTickEvent.Post} against the player's main inventory ({@link PlayerInventoryAdapter}).
 * Requests from the client ({@link HandcraftPayloads}) are validated here. The client's mirror is
 * synced on login, whenever the entries change or a new step starts, and at most once per
 * {@value #RESYNC_TICKS} ticks otherwise while crafting (to correct the client's extrapolated progress).
 * When a player dies the whole queue is cancelled: its items go back into the inventory before the
 * death drop, so they drop with it (or stay, with keepInventory); what does not fit drops at the
 * player's feet.
 */
public final class HandcraftTicker {
    /** Most entries in one player's queue. */
    public static final int MAX_ENTRIES = 64;
    /** Most crafts in one entry. */
    public static final int MAX_TIMES = 1000;
    /** Most enqueue requests a player may make per {@link #RATE_WINDOW_TICKS} ticks. */
    public static final int MAX_REQUESTS_PER_SECOND = 10;
    public static final int RATE_WINDOW_TICKS = 20;
    /** Least ticks between two syncs caused only by progress. */
    public static final int RESYNC_TICKS = 20;

    private HandcraftTicker() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) tick(p);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) onDeath(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            HandcraftAttachment a = p.getExistingDataOrNull(ModAttachments.HANDCRAFT);
            if (a != null) sync(p, a, true);
            else PacketDistributor.sendToPlayer(p, new HandcraftPayloads.QueueSync(List.of(), 0));
        });
    }

    public enum EnqueueResult { OK, SPECTATOR, BAD_COUNT, UNKNOWN_RECIPE, NOT_CRAFTING, LOCKED, QUEUE_FULL, TOO_SOON, MISSING_ITEMS }

    /**
     * Queues {@code times} crafts of {@code recipeId} for the player (from {@link HandcraftPayloads.Enqueue}):
     * refused for spectators, counts outside 1..{@value #MAX_TIMES}, unknown, non-crafting or locked
     * recipes and a full queue; then at most {@value #MAX_REQUESTS_PER_SECOND} requests per second are
     * planned, and the ingredients (plain stacks) are taken at once or not at all.
     */
    public static EnqueueResult enqueue(ServerPlayer player, String recipeId, int times) {
        if (player.isSpectator()) return EnqueueResult.SPECTATOR;
        if (times < 1 || times > MAX_TIMES) return EnqueueResult.BAD_COUNT;
        Optional<Recipe> recipe = Phase1Data.recipes().get(recipeId);
        if (recipe.isEmpty()) return EnqueueResult.UNKNOWN_RECIPE;
        if (recipe.get().category() != RecipeCategory.CRAFTING) return EnqueueResult.NOT_CRAFTING;
        MinecraftServer server = player.server;
        if (!RecipeLocks.isUnlocked(server, recipeId)) return EnqueueResult.LOCKED;
        HandcraftAttachment a = HandcraftAttachment.of(player);
        if (a.queue().entries().size() >= MAX_ENTRIES) return EnqueueResult.QUEUE_FULL;
        int now = server.getTickCount();
        if (now - a.windowStart >= RATE_WINDOW_TICKS) {
            a.windowStart = now;
            a.windowRequests = 0;
        }
        if (a.windowRequests >= MAX_REQUESTS_PER_SECOND) return EnqueueResult.TOO_SOON;
        a.windowRequests++;
        if (!a.queue().enqueue(recipeId, times, new PlayerInventoryAdapter(player), id -> RecipeLocks.isUnlocked(server, id))) {
            return EnqueueResult.MISSING_ITEMS;
        }
        sync(player, a, false);
        return EnqueueResult.OK;
    }

    /** Cancels the entry at {@code index} (from {@link HandcraftPayloads.Cancel}); returns whether there was one. */
    public static boolean cancel(ServerPlayer player, int index) {
        HandcraftAttachment a = player.getExistingDataOrNull(ModAttachments.HANDCRAFT);
        if (a == null || index < 0 || index >= a.queue().entries().size()) return false;
        PlayerInventoryAdapter inv = new PlayerInventoryAdapter(player);
        inv.dropAll(a.queue().cancel(index, inv));
        sync(player, a, false);
        return true;
    }

    /** One tick of the player's queue. */
    public static void tick(ServerPlayer player) {
        HandcraftAttachment a = player.getExistingDataOrNull(ModAttachments.HANDCRAFT);
        if (a == null) return;
        PlayerInventoryAdapter inv = new PlayerInventoryAdapter(player);
        giveRefund(a, inv);
        a.queue().tick(inv);
        sync(player, a, false);
    }

    /** Cancels every entry; the items go back into the inventory, the rest drops at the player's feet. */
    public static void onDeath(ServerPlayer player) {
        HandcraftAttachment a = player.getExistingDataOrNull(ModAttachments.HANDCRAFT);
        if (a == null) return;
        PlayerInventoryAdapter inv = new PlayerInventoryAdapter(player);
        giveRefund(a, inv);
        while (!a.queue().entries().isEmpty()) inv.dropAll(a.queue().cancel(0, inv));
        sync(player, a, false);
    }

    private static void giveRefund(HandcraftAttachment a, PlayerInventoryAdapter inv) {
        if (a.refund().isEmpty()) return;
        Map<String, Integer> items = new LinkedHashMap<>(a.refund());
        a.refund().clear();
        items.forEach(inv::add);
    }

    private static void sync(ServerPlayer player, HandcraftAttachment a, boolean force) {
        HandcraftQueue q = a.queue();
        List<HandcraftQueue.Entry> entries = q.entries();
        List<String> shape = new ArrayList<>(entries.size());
        for (HandcraftQueue.Entry e : entries) shape.add(e.recipeId() + "*" + e.times());
        double progress = entries.isEmpty() ? 0 : entries.get(0).progress();
        int now = player.server.getTickCount();
        boolean stepStarted = progress < a.lastProgress;
        a.lastProgress = progress;
        boolean send = force || stepStarted || !shape.equals(a.syncedShape)
                || progress != a.syncedProgress && now - a.syncedTick >= RESYNC_TICKS;
        if (!send) return;
        a.syncedShape = shape;
        a.syncedProgress = progress;
        a.syncedTick = now;
        PacketDistributor.sendToPlayer(player, new HandcraftPayloads.QueueSync(entries, q.currentStepTicks()));
    }
}
