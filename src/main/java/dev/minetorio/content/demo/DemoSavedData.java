package dev.minetorio.content.demo;

import dev.minetorio.content.belt.BeltBlockEntity;
import dev.minetorio.content.belt.LaneSide;
import dev.minetorio.content.research.ResearchSavedData;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.research.Technology;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The "creative" parts of the demo showcase ({@code /minetorio demo}), saved in the Overworld's
 * data storage as {@value #NAME}. A world gets this data only when the command is run; without it
 * the tick hook does nothing. {@code /minetorio demo off} clears it.
 *
 * <ul>
 *   <li><b>Sources</b>: chests topped up to {@code keep} items of one kind every
 *       {@value #CHEST_INTERVAL} ticks (coal for the fuel lines, plates, science packs, scanners).</li>
 *   <li><b>Sinks</b>: chests trimmed to at most {@code keep} items every {@value #CHEST_INTERVAL}
 *       ticks: 0 for the ends of the showcase belts, a stack for output chests (output stays
 *       visible, lines never block).</li>
 *   <li><b>Belt feeds</b>: every tick an item is put at the start of each lane of the belt if
 *       there is room: an endless, compressed stream.</li>
 *   <li><b>Research autopilot</b>: whenever nothing is being researched, {@code automation} or else
 *       the first researchable technology needing only red packs is selected. It turns itself off
 *       the first time a player selects research.</li>
 * </ul>
 *
 * <p>Only chests the demo placed are touched: they carry a demo custom name ({@link #tag}). An entry
 * whose chunk is loaded but whose block is no longer such a chest (or no longer a belt, for a feed)
 * is dropped for good, so a chest a player puts there later is never refilled or emptied.
 */
public final class DemoSavedData extends SavedData {
    public static final String NAME = "minetorio_demo";
    public static final int CHEST_INTERVAL = 20;
    public static final String SOURCE_NAME = "minetorio.demo.chest.source";
    public static final String SINK_NAME = "minetorio.demo.chest.sink";
    private static final String RED = "minetorio:automation_science_pack";

    public record Source(BlockPos pos, String item, int keep) {}

    public record Sink(BlockPos pos, int keep) {}

    public record Feed(BlockPos pos, @Nullable String left, @Nullable String right) {}

    private final List<Source> sources = new ArrayList<>();
    private final List<Sink> sinks = new ArrayList<>();
    private final List<Feed> feeds = new ArrayList<>();
    private boolean research;

    private DemoSavedData() {}

    private static SavedData.Factory<DemoSavedData> factory() {
        return new SavedData.Factory<>(DemoSavedData::new, DemoSavedData::load);
    }

    /** The demo data, created if missing (only the command and its tests call this). */
    public static DemoSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** The demo data if this world has any; never creates it. */
    @Nullable
    public static DemoSavedData getIfPresent(MinecraftServer server) {
        return server.overworld().getDataStorage().get(factory(), NAME);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    /**
     * Names {@code chest} as a demo chest ({@link #SOURCE_NAME} or {@link #SINK_NAME}); only such
     * chests are refilled or emptied. Tag an empty chest: applying the name also resets its contents.
     */
    public static void tag(ChestBlockEntity chest, String name) {
        ItemStack named = new ItemStack(Items.CHEST);
        named.set(DataComponents.CUSTOM_NAME, Component.translatable(name));
        chest.applyComponentsFromItemStack(named);
        chest.setChanged();
    }

    /** Whether the block entity is a chest named {@code name} by {@link #tag}. */
    static boolean isTagged(@Nullable BlockEntity be, String name) {
        return be instanceof ChestBlockEntity chest && chest.getCustomName() != null
                && chest.getCustomName().getContents() instanceof TranslatableContents t && t.getKey().equals(name);
    }

    public List<Source> sources() { return List.copyOf(sources); }

    public List<Sink> sinks() { return List.copyOf(sinks); }

    public List<Feed> feeds() { return List.copyOf(feeds); }

    public boolean research() { return research; }

    public boolean isEmpty() { return sources.isEmpty() && sinks.isEmpty() && feeds.isEmpty() && !research; }

    public void addSource(BlockPos pos, String item, int keep) {
        sources.add(new Source(pos.immutable(), item, keep));
        setDirty();
    }

    public void addSink(BlockPos pos, int keep) {
        sinks.add(new Sink(pos.immutable(), keep));
        setDirty();
    }

    public void addFeed(BlockPos pos, @Nullable String left, @Nullable String right) {
        feeds.add(new Feed(pos.immutable(), left, right));
        setDirty();
    }

    public void setResearch(boolean on) {
        research = on;
        setDirty();
    }

    /** Forgets every source, sink and feed inside {@code box} (a showcase being rebuilt). */
    public void removeIn(BoundingBox box) {
        sources.removeIf(s -> box.isInside(s.pos()));
        sinks.removeIf(s -> box.isInside(s.pos()));
        feeds.removeIf(f -> box.isInside(f.pos()));
        setDirty();
    }

    /** Forgets everything and turns the research autopilot off. */
    public void clear() {
        sources.clear();
        sinks.clear();
        feeds.clear();
        research = false;
        setDirty();
    }

    /** A player chose a research: the autopilot stops for good (it would fight the player otherwise). */
    public static void playerSelectedResearch(MinecraftServer server) {
        DemoSavedData data = getIfPresent(server);
        if (data != null && data.research) data.setResearch(false);
    }

    // --- ticking ---------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        DemoSavedData data = getIfPresent(server);
        if (data == null || data.isEmpty()) return;
        ServerLevel level = server.overworld();
        boolean dropped = data.feeds.removeIf(f -> {
            if (!level.isLoaded(f.pos())) return false;
            if (!(level.getBlockEntity(f.pos()) instanceof BeltBlockEntity belt)) return true;
            if (f.left() != null) belt.lane(LaneSide.LEFT).insertAt(f.left(), 0);
            if (f.right() != null) belt.lane(LaneSide.RIGHT).insertAt(f.right(), 0);
            return false;
        });
        if (server.getTickCount() % CHEST_INTERVAL == 0) {
            dropped |= data.sources.removeIf(s -> {
                if (!level.isLoaded(s.pos())) return false;
                if (!isTagged(level.getBlockEntity(s.pos()), SOURCE_NAME)) return true;
                topUp(level, s);
                return false;
            });
            dropped |= data.sinks.removeIf(s -> {
                if (!level.isLoaded(s.pos())) return false;
                if (!isTagged(level.getBlockEntity(s.pos()), SINK_NAME)) return true;
                trim((ChestBlockEntity) level.getBlockEntity(s.pos()), s.keep());
                return false;
            });
            if (data.research) selectNextResearch(server);
        }
        if (dropped) data.setDirty();
    }

    private static void topUp(ServerLevel level, Source s) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, s.pos(), null);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(s.item()));
        if (handler == null || item == Items.AIR) return;
        int have = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack st = handler.getStackInSlot(i);
            if (st.is(item) && st.getComponentsPatch().isEmpty()) have += st.getCount();
        }
        int missing = s.keep() - have;
        int max = new ItemStack(item).getMaxStackSize();
        while (missing > 0) {
            int n = Math.min(missing, max);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, new ItemStack(item, n), false);
            if (rest.getCount() == n) return; // full
            missing -= n - rest.getCount();
        }
    }

    /** Removes items from the last slots on until at most {@code keep} are left. */
    static void trim(ChestBlockEntity chest, int keep) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) total += chest.getItem(i).getCount();
        for (int i = chest.getContainerSize() - 1; i >= 0 && total > keep; i--) {
            ItemStack st = chest.getItem(i);
            int n = Math.min(st.getCount(), total - keep);
            if (n > 0) {
                chest.removeItem(i, n);
                total -= n;
            }
        }
    }

    /** Starts {@code automation} if possible, otherwise the first researchable technology needing only red packs. */
    static boolean selectNextResearch(MinecraftServer server) {
        ResearchSavedData r = ResearchSavedData.get(server);
        if (r.state().current().isPresent()) return true;
        if (r.select("automation")) return true;
        for (Technology t : Phase1Data.techTree().all()) {
            if (t.packsPerUnit().keySet().equals(Set.of(RED)) && r.select(t.id())) return true;
        }
        return false;
    }

    // --- persistence -----------------------------------------------------------------------------

    private static DemoSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DemoSavedData d = new DemoSavedData();
        ListTag src = tag.getList("sources", Tag.TAG_COMPOUND);
        for (int i = 0; i < src.size(); i++) {
            CompoundTag e = src.getCompound(i);
            NbtUtils.readBlockPos(e, "pos").ifPresent(p -> d.sources.add(new Source(p, e.getString("item"), e.getInt("keep"))));
        }
        ListTag sk = tag.getList("sinks", Tag.TAG_COMPOUND);
        for (int i = 0; i < sk.size(); i++) {
            CompoundTag e = sk.getCompound(i);
            NbtUtils.readBlockPos(e, "pos").ifPresent(p -> d.sinks.add(new Sink(p, e.getInt("keep"))));
        }
        ListTag fd = tag.getList("feeds", Tag.TAG_COMPOUND);
        for (int i = 0; i < fd.size(); i++) {
            CompoundTag e = fd.getCompound(i);
            String left = e.contains("left", Tag.TAG_STRING) ? e.getString("left") : null;
            String right = e.contains("right", Tag.TAG_STRING) ? e.getString("right") : null;
            NbtUtils.readBlockPos(e, "pos").ifPresent(p -> d.feeds.add(new Feed(p, left, right)));
        }
        d.research = tag.getBoolean("research");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag src = new ListTag();
        for (Source s : sources) {
            CompoundTag e = new CompoundTag();
            e.put("pos", NbtUtils.writeBlockPos(s.pos()));
            e.putString("item", s.item());
            e.putInt("keep", s.keep());
            src.add(e);
        }
        tag.put("sources", src);
        ListTag sk = new ListTag();
        for (Sink s : sinks) {
            CompoundTag e = new CompoundTag();
            e.put("pos", NbtUtils.writeBlockPos(s.pos()));
            e.putInt("keep", s.keep());
            sk.add(e);
        }
        tag.put("sinks", sk);
        ListTag fd = new ListTag();
        for (Feed f : feeds) {
            CompoundTag e = new CompoundTag();
            e.put("pos", NbtUtils.writeBlockPos(f.pos()));
            if (f.left() != null) e.putString("left", f.left());
            if (f.right() != null) e.putString("right", f.right());
            fd.add(e);
        }
        tag.put("feeds", fd);
        tag.putBoolean("research", research);
        return tag;
    }
}
