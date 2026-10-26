package dev.minetorio.content.deposit;

import dev.minetorio.core.deposit.DepositLedger;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-world deposit state, saved in the Overworld's data storage as {@value #NAME}: the spawn
 * point the deposits were laid out around (captured once, so moving the world spawn later does not
 * move the deposits) and the {@link DepositLedger} of mined amounts.
 */
public final class DepositSavedData extends SavedData {
    public static final String NAME = "minetorio_deposits";
    private static final String KEY_SPAWN_X = "spawn_x";
    private static final String KEY_SPAWN_Z = "spawn_z";
    private static final String KEY_MINED = "mined";
    private static final String KEY_ID = "id";
    private static final String KEY_AMOUNT = "amount";

    private final int spawnX;
    private final int spawnZ;
    private final DepositLedger ledger = new DepositLedger();
    /** Built on first use; not saved. */
    @Nullable
    Deposits deposits;

    private DepositSavedData(int spawnX, int spawnZ) {
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
    }

    public static SavedData.Factory<DepositSavedData> factory(BlockPos spawn) {
        return new SavedData.Factory<>(() -> {
            DepositSavedData d = new DepositSavedData(spawn.getX(), spawn.getZ());
            d.setDirty(); // pin the spawn point
            return d;
        }, DepositSavedData::load);
    }

    public int spawnX() { return spawnX; }

    public int spawnZ() { return spawnZ; }

    public DepositLedger ledger() { return ledger; }

    private static DepositSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DepositSavedData d = new DepositSavedData(tag.getInt(KEY_SPAWN_X), tag.getInt(KEY_SPAWN_Z));
        Map<Long, Long> mined = new HashMap<>();
        ListTag list = tag.getList(KEY_MINED, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            long amount = e.getLong(KEY_AMOUNT);
            if (amount > 0) mined.put(e.getLong(KEY_ID), amount);
        }
        d.ledger.load(mined);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_SPAWN_X, spawnX);
        tag.putInt(KEY_SPAWN_Z, spawnZ);
        ListTag list = new ListTag();
        ledger.minedById().forEach((id, amount) -> {
            CompoundTag e = new CompoundTag();
            e.putLong(KEY_ID, id);
            e.putLong(KEY_AMOUNT, amount);
            list.add(e);
        });
        tag.put(KEY_MINED, list);
        return tag;
    }
}
