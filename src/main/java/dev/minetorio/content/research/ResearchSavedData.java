package dev.minetorio.content.research;

import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.research.LabLogic;
import dev.minetorio.core.research.ResearchState;
import dev.minetorio.core.research.Technology;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The research of a world: one {@link ResearchState} shared by every player, saved in the
 * Overworld's data storage as {@value #NAME}. Every change goes through this class, which marks it
 * dirty and keeps the clients' mirrors ({@link ClientResearch}) up to date with
 * {@link ResearchSyncPayload}: at once when a technology is chosen or completed, at most once per
 * {@value #PROGRESS_SYNC_TICKS} ticks for unit progress, and to each player on login.
 */
public final class ResearchSavedData extends SavedData {
    public static final String NAME = "minetorio_research";
    /** Least ticks between two syncs caused only by unit progress. */
    public static final int PROGRESS_SYNC_TICKS = 20;
    /** Least ticks between two research switches by the same player. */
    public static final int SWITCH_COOLDOWN_TICKS = 10;
    private static final String KEY_RESEARCHED = "researched";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_PROGRESS = "progress";

    private final ResearchState state = newState();
    /** Server the data belongs to (set by {@link #get}); not saved. */
    @Nullable
    private MinecraftServer server;
    private boolean progressSyncPending;
    private int lastSyncTick = Integer.MIN_VALUE / 2;
    /** Server tick of each player's last research switch; not saved. */
    private final Map<UUID, Integer> lastSwitchTick = new HashMap<>();

    private ResearchSavedData() {}

    public static ResearchState newState() {
        return new ResearchState(Phase1Data.techTree(), Phase1Data.startRecipes());
    }

    private static SavedData.Factory<ResearchSavedData> factory() {
        return new SavedData.Factory<>(ResearchSavedData::new, ResearchSavedData::load);
    }

    public static ResearchSavedData get(MinecraftServer server) {
        ResearchSavedData data = server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
        data.server = server;
        return data;
    }

    /** Syncs to players on login and flushes throttled progress syncs. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, get(player.server).syncPayload());
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> get(e.getServer()).flushProgressSync());
    }

    /** Translation key of a technology's name. */
    public static String nameKey(String techId) {
        return "technology.minetorio." + techId;
    }

    /** The research state; read it freely, change it only through this class. */
    public ResearchState state() { return state; }

    /**
     * Ticks a lab against the shared state. A finished unit is saved and synced (throttled); a
     * finished technology is announced in chat to everyone with a sound and synced at once.
     */
    public void tickLab(LabLogic lab, double satisfaction) {
        Optional<Technology> before = state.current();
        int doneBefore = state.unitsDone();
        lab.tick(state, satisfaction);
        if (before.isEmpty()) return;
        if (state.isResearched(before.get().id())) {
            completed(before.get().id());
        } else if (state.unitsDone() != doneBefore) {
            setDirty();
            progressSyncPending = true;
        }
    }

    public enum SelectResult { OK, SPECTATOR, UNKNOWN, UNAVAILABLE, TOO_SOON }

    /**
     * A player's choice of the technology to research (from {@link SelectResearchPayload}):
     * accepted if it is the current one already or {@link ResearchState#canResearch can be
     * researched}; spectators may not choose, and a player may switch the research at most once per
     * {@value #SWITCH_COOLDOWN_TICKS} ticks.
     */
    public static SelectResult select(ServerPlayer player, String techId) {
        if (player.isSpectator()) return SelectResult.SPECTATOR;
        if (Phase1Data.techTree().get(techId).isEmpty()) return SelectResult.UNKNOWN;
        ResearchSavedData data = get(player.server);
        if (data.state.current().map(t -> t.id().equals(techId)).orElse(false)) return SelectResult.OK;
        if (!data.state.canResearch(techId)) return SelectResult.UNAVAILABLE;
        int now = player.server.getTickCount();
        Integer last = data.lastSwitchTick.get(player.getUUID());
        if (last != null && now - last < SWITCH_COOLDOWN_TICKS) return SelectResult.TOO_SOON;
        data.lastSwitchTick.put(player.getUUID(), now);
        data.select(techId);
        return SelectResult.OK;
    }

    /** Makes {@code techId} current if allowed (see {@link #select(ServerPlayer, String)}); returns whether it is current now. */
    public boolean select(String techId) {
        if (state.current().map(t -> t.id().equals(techId)).orElse(false)) return true;
        if (!state.canResearch(techId)) return false;
        state.setCurrent(techId);
        changed();
        return true;
    }

    /** Replaces the whole state (commands, tests) and syncs it. */
    public void restore(Set<String> researched, @Nullable String current, Map<String, Integer> progressById) {
        loadInto(state, researched, current, progressById);
        changed();
    }

    private void completed(String techId) {
        changed();
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("minetorio.research.completed", Component.translatable(nameKey(techId))), false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.4f, 1.0f);
        }
    }

    /** Marks dirty and syncs to every player now. */
    private void changed() {
        setDirty();
        progressSyncPending = false;
        if (server == null) return;
        lastSyncTick = server.getTickCount();
        PacketDistributor.sendToAllPlayers(syncPayload());
    }

    private void flushProgressSync() {
        if (!progressSyncPending || server == null || server.getTickCount() - lastSyncTick < PROGRESS_SYNC_TICKS) return;
        changed();
    }

    public ResearchSyncPayload syncPayload() {
        return new ResearchSyncPayload(Set.copyOf(state.researched()), state.current().map(Technology::id).orElse(null),
                state.progressById());
    }

    /**
     * Loads saved or synced data into {@code target}, dropping a current technology that is unknown
     * or already researched.
     */
    static void loadInto(ResearchState target, Set<String> researched, @Nullable String current, Map<String, Integer> progressById) {
        String cur = current != null && Phase1Data.techTree().get(current).isPresent() && !researched.contains(current) ? current : null;
        target.load(researched, cur, progressById);
    }

    // --- persistence -----------------------------------------------------------------------------------

    private static boolean isKnown(String techId) { return Phase1Data.techTree().get(techId).isPresent(); }

    public static ResearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchSavedData d = new ResearchSavedData();
        Set<String> researched = new LinkedHashSet<>();
        ListTag list = tag.getList(KEY_RESEARCHED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (isKnown(list.getString(i))) researched.add(list.getString(i));
        }
        String current = tag.contains(KEY_CURRENT, Tag.TAG_STRING) ? tag.getString(KEY_CURRENT) : null;
        Map<String, Integer> progress = CoreNbt.readCounts(tag.getCompound(KEY_PROGRESS));
        progress.entrySet().removeIf(e -> e.getValue() <= 0 || !isKnown(e.getKey()) || researched.contains(e.getKey()));
        loadInto(d.state, researched, current, progress);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String id : state.researched()) list.add(StringTag.valueOf(id));
        tag.put(KEY_RESEARCHED, list);
        state.current().ifPresent(t -> tag.putString(KEY_CURRENT, t.id()));
        tag.put(KEY_PROGRESS, CoreNbt.writeCounts(state.progressById()));
        return tag;
    }
}
