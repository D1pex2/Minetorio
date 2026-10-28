package dev.minetorio.content.orefield;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * The ore amounts of one chunk's field blocks (a chunk attachment, see
 * {@link dev.minetorio.content.registry.ModAttachments#ORE_FIELD}): remaining and initial ore per
 * block position. Plain maps, no block entities: thousands of field blocks cost nothing per tick.
 * Callers mark the chunk unsaved after a change ({@link OreFields}).
 */
public final class OreFieldData implements INBTSerializable<CompoundTag> {
    private static final String KEY_POS = "pos";
    private static final String KEY_REMAINING = "remaining";
    private static final String KEY_INITIAL = "initial";
    private static final String KEY_PATCH = "patch";

    private final Long2LongOpenHashMap remaining = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap initial = new Long2LongOpenHashMap();
    /** Patch key ({@code PatchLedger.key}) per block stamped for a patch; absent for blocks of no patch. */
    private final Long2LongOpenHashMap owner = new Long2LongOpenHashMap();

    /** Key of a block inside its chunk: y in the high bits, local x and z in the low byte. */
    static long key(BlockPos pos) {
        return ((long) pos.getY() << 8) | ((pos.getX() & 15) << 4) | (pos.getZ() & 15);
    }

    public boolean has(BlockPos pos) { return remaining.containsKey(key(pos)); }

    /** Remaining ore, or -1 without an entry. */
    public long remaining(BlockPos pos) { return remaining.getOrDefault(key(pos), -1L); }

    /** Initial ore, or -1 without an entry. */
    public long initial(BlockPos pos) { return initial.getOrDefault(key(pos), -1L); }

    /** Key of the patch the block was stamped for ({@link OrePatchLedger}), or 0 for none. */
    public long owner(BlockPos pos) { return owner.get(key(pos)); }

    /** Sets the block's patch (0: none). */
    public void setOwner(BlockPos pos, long patchKey) {
        if (patchKey == 0) owner.remove(key(pos));
        else owner.put(key(pos), patchKey);
    }

    /** Sets the amounts; the block's patch stays. */
    public void set(BlockPos pos, long initialAmount, long remainingAmount) {
        long k = key(pos);
        initial.put(k, initialAmount);
        remaining.put(k, remainingAmount);
    }

    public boolean remove(BlockPos pos) {
        long k = key(pos);
        if (!remaining.containsKey(k)) return false;
        initial.remove(k);
        remaining.remove(k);
        owner.remove(k);
        return true;
    }

    public int size() { return remaining.size(); }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        long[] keys = remaining.keySet().toLongArray();
        long[] rem = new long[keys.length];
        long[] init = new long[keys.length];
        long[] patch = new long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            rem[i] = remaining.get(keys[i]);
            init[i] = initial.get(keys[i]);
            patch[i] = owner.get(keys[i]);
        }
        CompoundTag tag = new CompoundTag();
        tag.putLongArray(KEY_POS, keys);
        tag.putLongArray(KEY_REMAINING, rem);
        tag.putLongArray(KEY_INITIAL, init);
        tag.putLongArray(KEY_PATCH, patch);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, @UnknownNullability CompoundTag tag) {
        remaining.clear();
        initial.clear();
        owner.clear();
        long[] keys = tag.getLongArray(KEY_POS);
        long[] rem = tag.getLongArray(KEY_REMAINING);
        long[] init = tag.getLongArray(KEY_INITIAL);
        long[] patch = tag.getLongArray(KEY_PATCH); // empty in chunks saved before patches were tracked
        int n = Math.min(keys.length, Math.min(rem.length, init.length));
        for (int i = 0; i < n; i++) {
            remaining.put(keys[i], rem[i]);
            initial.put(keys[i], init[i]);
            if (i < patch.length && patch[i] != 0) owner.put(keys[i], patch[i]);
        }
    }
}
