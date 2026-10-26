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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * the tick hook does nothing.
 *
 * <ul>
 *   <li><b>Sources</b>: chests topped up to {@code keep} items of one kind every
 *       {@value #CHEST_INTERVAL} ticks (coal for the fuel lines, plates, science packs, scanners).</li>
 *   <li><b>Sinks</b>: chests emptied every {@value #CHEST_INTERVAL} ticks, so the belts and
 *       inserters ending in them never stop.</li>
 *   <li><b>Belt feeds</b>: every tick an item is put at the start of each lane of the belt if
 *       there is room: an endless, compressed stream.</li>
 *   <li><b>Research</b>: whenever nothing is being researched, the first technology that needs
 *       only automation (red) packs and can be researched is selected.</li>
 * </ul>
 * Everything only acts while its chunk is loaded.
 */
public final class DemoSavedData extends SavedData {
    public static final String NAME = "minetorio_demo";
    public static final int CHEST_INTERVAL = 20;
    private static final String RED = "minetorio:automation_science_pack";

    public record Source(BlockPos pos, String item, int keep) {}

    public record Feed(BlockPos pos, @Nullable String left, @Nullable String right) {}

    private final List<Source> sources = new ArrayList<>();
    private final List<BlockPos> sinks = new ArrayList<>();
    private final List<Feed> feeds = new ArrayList<>();
    private boolean research;

    private DemoSavedData() {}

    private static SavedData.Factory<DemoSavedData> factory() {
        return new SavedData.Factory<>(DemoSavedData::new, DemoSavedData::load);
    }

    /** The demo data, created if missing (only the command and its test call this). */
    public static DemoSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    public List<Source> sources() { return List.copyOf(sources); }

    public List<BlockPos> sinks() { return List.copyOf(sinks); }

    public List<Feed> feeds() { return List.copyOf(feeds); }

    public boolean research() { return research; }

    public void addSource(BlockPos pos, String item, int keep) {
        sources.add(new Source(pos.immutable(), item, keep));
        setDirty();
    }

    public void addSink(BlockPos pos) {
        sinks.add(pos.immutable());
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

    /** Forgets every source, sink and feed inside {@code box} (a showcase being rebuilt or removed). */
    public void removeIn(BoundingBox box) {
        sources.removeIf(s -> box.isInside(s.pos()));
        sinks.removeIf(box::isInside);
        feeds.removeIf(f -> box.isInside(f.pos()));
        setDirty();
    }

    /** Forgets everything, research included. */
    public void clear() {
        sources.clear();
        sinks.clear();
        feeds.clear();
        research = false;
        setDirty();
    }

    // --- ticking ---------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        DemoSavedData data = level.getDataStorage().get(factory(), NAME); // never creates the data
        if (data == null) return;
        for (Feed f : data.feeds) {
            if (level.isLoaded(f.pos()) && level.getBlockEntity(f.pos()) instanceof BeltBlockEntity belt) {
                if (f.left() != null) belt.lane(LaneSide.LEFT).insertAt(f.left(), 0);
                if (f.right() != null) belt.lane(LaneSide.RIGHT).insertAt(f.right(), 0);
            }
        }
        if (server.getTickCount() % CHEST_INTERVAL != 0) return;
        for (Source s : data.sources) {
            if (level.isLoaded(s.pos())) topUp(level, s);
        }
        for (BlockPos p : data.sinks) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof Container c && !c.isEmpty()) c.clearContent();
        }
        if (data.research) selectNextResearch(server);
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
        for (int i = 0; i < sk.size(); i++) NbtUtils.readBlockPos(sk.getCompound(i), "pos").ifPresent(d.sinks::add);
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
        for (BlockPos p : sinks) {
            CompoundTag e = new CompoundTag();
            e.put("pos", NbtUtils.writeBlockPos(p));
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
