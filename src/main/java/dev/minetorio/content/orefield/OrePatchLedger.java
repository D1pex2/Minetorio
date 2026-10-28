package dev.minetorio.content.orefield;

import dev.minetorio.content.registry.ModAttachments;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.OreType;
import dev.minetorio.core.orefield.PatchGenerator;
import dev.minetorio.core.orefield.PatchKind;
import dev.minetorio.core.orefield.PatchLedger;
import dev.minetorio.core.orefield.PatchLookup;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server's {@link PatchLedger}: remaining ore per Overworld ore patch, saved in the Overworld's
 * data storage as {@value #NAME} (one entry per patch). Every field block remembers its patch's key
 * ({@link OreFieldData#owner}); stamping adds its amount to that patch ({@link OreFields#stamp},
 * {@link OreFields#placeOwned}), mining subtracts each ore ({@link OreFields#mineOne}) and removing a
 * block subtracts what was left in it ({@link OreFields#forget}). Field blocks without a patch
 * (placed by a command, a test, or before this ledger existed) never touch it.
 *
 * <p>World generation stamps on worker threads: its contributions are queued ({@link #chunkStamped})
 * and applied on the server thread, every tick and before any use of the ledger ({@link #get}).
 *
 * <p>Worlds from before the ledger: their generated patches have no entry and their blocks no
 * owner, so their totals show the formula amount of the whole patch ({@link #patchAt}) and do not
 * drop when mined; patches generated after the update are exact.
 */
public final class OrePatchLedger extends SavedData {
    public static final String NAME = "minetorio_ore_patches";
    private static final String KEY_PATCHES = "patches";

    private record Stamped(ServerLevel level, OrePatch patch, int chunkX, int chunkZ, long amount) {}

    private static final ConcurrentLinkedQueue<Stamped> QUEUE = new ConcurrentLinkedQueue<>();

    private final PatchLedger ledger = new PatchLedger();

    private OrePatchLedger() {}

    private static SavedData.Factory<OrePatchLedger> factory() {
        return new SavedData.Factory<>(OrePatchLedger::new, OrePatchLedger::load);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (!QUEUE.isEmpty()) get(e.getServer().overworld());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> get(e.getServer().overworld()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> QUEUE.clear());
    }

    /** The ledger of {@code level}'s server (kept by the Overworld), with queued world generation applied. Server thread only. */
    public static OrePatchLedger get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        OrePatchLedger d = overworld.getDataStorage().computeIfAbsent(factory(), NAME);
        for (Stamped s; (s = QUEUE.poll()) != null; ) {
            if (s.level != overworld) continue; // another dimension, or a server that is gone
            d.ledger.chunkStamped(s.patch, s.chunkX, s.chunkZ, s.amount);
            d.setDirty();
        }
        return d;
    }

    /**
     * World generation stamped chunk (chunkX, chunkZ) of generated {@code patch} with {@code amount}
     * ore in total (any thread; applied on the server thread).
     */
    public static void chunkStamped(ServerLevel level, OrePatch patch, int chunkX, int chunkZ, long amount) {
        QUEUE.add(new Stamped(level, patch, chunkX, chunkZ, amount));
    }

    /** The plain ledger (read it; change it through the methods here so it is saved). */
    public PatchLedger ledger() { return ledger; }

    /** Records a patch stamped whole (see {@link PatchLedger#register}); returns its key. */
    public long register(PatchKind kind, OrePatch patch, int spawnX, int spawnZ) {
        setDirty();
        return ledger.register(kind, patch, spawnX, spawnZ);
    }

    /** Adds {@code delta} to the patch with this key (no-op for 0 or an unknown key). */
    public void add(long key, long delta) {
        if (key != 0 && delta != 0 && ledger.add(key, delta)) setDirty();
    }

    public void remove(long key) {
        if (ledger.remove(key) != null) setDirty();
    }

    /** Remaining ore of the patch with this key, or -1 if it has no entry. */
    public long total(long key) { return ledger.total(key); }

    /**
     * The patch a {@code type} field block in column (x, z) belongs to by layout ({@link PatchLookup}):
     * recorded patches, then the generated one (gone from the starting area once that is stamped).
     */
    @Nullable
    public PatchLookup.Ref lookup(ServerLevel level, int x, int z, OreType type) {
        if (level.dimension() != Level.OVERWORLD) return null;
        StartingFields starting = StartingFields.get(level.getServer().overworld());
        int[] spawn = starting.stamped() ? new int[] {starting.spawnX(), starting.spawnZ()} : null;
        return PatchLookup.find(x, z, type, ledger.recorded(), new PatchGenerator(level.getSeed(), 0, 0), spawn);
    }

    /**
     * The patch of the {@code type} field block at {@code pos}: the one it was stamped for, else (a
     * block from before the ledger) the one the layout puts there; a patch without an entry is
     * returned with nothing stamped, i.e. its formula total. Null if it belongs to no patch.
     */
    @Nullable
    public PatchLedger.Entry patchAt(ServerLevel level, BlockPos pos, OreType type) {
        OreFieldData data = level.getChunkAt(pos).getExistingDataOrNull(ModAttachments.ORE_FIELD.get());
        long owner = data == null ? 0 : data.owner(pos);
        if (owner != 0) {
            PatchLedger.Entry e = ledger.entry(owner);
            if (e != null) return e;
        }
        PatchLookup.Ref ref = lookup(level, pos.getX(), pos.getZ(), type);
        if (ref == null) return null;
        PatchLedger.Entry e = ledger.entry(ref.key());
        if (e != null) return e;
        if (ref.kind() != PatchKind.GENERATED) return null;
        return new PatchLedger.Entry(PatchKind.GENERATED, ref.patch(), 0, 0, 0, 0);
    }

    private static OrePatchLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        OrePatchLedger d = new OrePatchLedger();
        for (Tag t : tag.getList(KEY_PATCHES, Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            PatchKind kind = PatchKind.byCode(c.getInt("kind"));
            OreType type;
            try {
                type = OreType.valueOf(c.getString("type").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (kind == null) continue;
            OrePatch p = new OrePatch(type, c.getInt("x"), c.getInt("z"), c.getInt("r"));
            d.ledger.put(new PatchLedger.Entry(kind, p, c.getInt("sx"), c.getInt("sz"), c.getLong("left"), c.getLong("done")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PatchLedger.Entry e : ledger.entries()) {
            CompoundTag c = new CompoundTag();
            c.putInt("kind", e.kind().code());
            c.putString("type", e.patch().type().name().toLowerCase(Locale.ROOT));
            c.putInt("x", e.patch().centerX());
            c.putInt("z", e.patch().centerZ());
            c.putInt("r", e.patch().radius());
            c.putInt("sx", e.spawnX());
            c.putInt("sz", e.spawnZ());
            c.putLong("left", e.remaining());
            c.putLong("done", e.doneMask());
            list.add(c);
        }
        tag.put(KEY_PATCHES, list);
        return tag;
    }

    /** Round trip through NBT, as a save and reload would (for tests). */
    public static OrePatchLedger reload(OrePatchLedger d, HolderLookup.Provider registries) {
        return load(d.save(new CompoundTag(), registries), registries);
    }
}
