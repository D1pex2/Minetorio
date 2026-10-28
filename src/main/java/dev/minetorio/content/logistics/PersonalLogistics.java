package dev.minetorio.content.logistics;

import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.logistics.RequestTable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * A player's personal logistics (Factorio 1.1 personal logistic slots, without armor): request
 * slots with a minimum (robots bring items up to it) and a maximum (robots take away what is above
 * it: trash), and whether requests are active (Factorio's "personal logistics" toggle). Robots serve
 * the player only while the player stands inside a logistic network ({@link LogisticsManager}).
 */
public final class PersonalLogistics implements INBTSerializable<CompoundTag> {
    private final RequestTable requests = new RequestTable(LogisticStats.PERSONAL_REQUEST_SLOTS);
    private boolean enabled = true;

    public static PersonalLogistics of(Player player) { return player.getData(LogisticsRegistry.PERSONAL); }

    public RequestTable requests() { return requests; }

    public boolean enabled() { return enabled; }

    public void setEnabled(boolean on) { enabled = on; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put("requests", RequestNbt.save(requests));
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        RequestNbt.load(requests, tag.getList("requests", Tag.TAG_COMPOUND));
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
    }
}
