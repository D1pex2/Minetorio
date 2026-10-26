package dev.minetorio.content.deposit;

import dev.minetorio.core.deposit.Deposit;
import dev.minetorio.core.deposit.DepositLedger;
import dev.minetorio.core.deposit.OreType;
import dev.minetorio.core.deposit.PlacedDeposits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * move the deposits), the {@link DepositLedger} of mined amounts and the {@link PlacedDeposits}
 * (deposits placed at fixed spots by {@code /minetorio demo}).
 */
public final class DepositSavedData extends SavedData {
    public static final String NAME = "minetorio_deposits";
    private static final String KEY_SPAWN_X = "spawn_x";
    private static final String KEY_SPAWN_Z = "spawn_z";
    private static final String KEY_MINED = "mined";
    private static final String KEY_ID = "id";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_PLACED = "placed";
    private static final String KEY_TYPE = "type";
    private static final String KEY_X = "x";
    private static final String KEY_Z = "z";
    private static final String KEY_RADIUS = "radius";

    private final int spawnX;
    private final int spawnZ;
    private final DepositLedger ledger = new DepositLedger();
    private final PlacedDeposits placed = new PlacedDeposits();
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

    public PlacedDeposits placed() { return placed; }

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
        List<Deposit> placed = new ArrayList<>();
        ListTag placedList = tag.getList(KEY_PLACED, Tag.TAG_COMPOUND);
        for (int i = 0; i < placedList.size(); i++) {
            CompoundTag e = placedList.getCompound(i);
            OreType type;
            try {
                type = OreType.valueOf(e.getString(KEY_TYPE));
            } catch (IllegalArgumentException unknown) {
                continue;
            }
            placed.add(new Deposit(e.getLong(KEY_ID), type, e.getInt(KEY_X), e.getInt(KEY_Z), e.getInt(KEY_RADIUS), e.getLong(KEY_AMOUNT)));
        }
        d.placed.load(placed);
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
        ListTag placedList = new ListTag();
        for (Deposit p : placed.all()) {
            CompoundTag e = new CompoundTag();
            e.putLong(KEY_ID, p.id());
            e.putString(KEY_TYPE, p.type().name());
            e.putInt(KEY_X, p.centerX());
            e.putInt(KEY_Z, p.centerZ());
            e.putInt(KEY_RADIUS, p.radius());
            e.putLong(KEY_AMOUNT, p.initialAmount());
            placedList.add(e);
        }
        tag.put(KEY_PLACED, placedList);
        return tag;
    }
}
