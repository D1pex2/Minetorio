package dev.minetorio.content.orefield;

import com.mojang.logging.LogUtils;
import dev.minetorio.core.orefield.FieldStamp;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.PatchGenerator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * The four starting ore fields ({@link PatchGenerator#startingPatches}): stamped around the world
 * spawn the first time the server starts, then remembered in the Overworld's data storage as
 * {@value #NAME} (with the spawn they were laid out around), so they are never stamped twice.
 * Their amounts follow {@link dev.minetorio.core.orefield.OreAmounts} measured from that spawn.
 */
public final class StartingFields extends SavedData {
    public static final String NAME = "minetorio_ore_fields";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY_SPAWN_X = "spawn_x";
    private static final String KEY_SPAWN_Z = "spawn_z";
    private static final String KEY_STAMPED = "starting_stamped";

    private int spawnX;
    private int spawnZ;
    private boolean stamped;

    private StartingFields() {}

    private static SavedData.Factory<StartingFields> factory() {
        return new SavedData.Factory<>(StartingFields::new, StartingFields::load);
    }

    public static StartingFields get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> stampOnce(e.getServer().overworld()));
    }

    public boolean stamped() { return stamped; }

    public int spawnX() { return spawnX; }

    public int spawnZ() { return spawnZ; }

    /** Stamps the starting fields around the Overworld spawn unless this world already has them. */
    public static void stampOnce(ServerLevel overworld) {
        StartingFields data = get(overworld);
        if (data.stamped) return;
        BlockPos spawn = overworld.getSharedSpawnPos();
        List<OrePatch> patches = PatchGenerator.startingPatches(spawn.getX(), spawn.getZ());
        int n = 0;
        for (OrePatch p : patches) {
            int r = p.radius();
            List<FieldStamp.Cell> cells = FieldStamp.plan(List.of(p), p.centerX() - r, p.centerZ() - r, p.centerX() + r, p.centerZ() + r,
                    spawn.getX(), spawn.getZ());
            n += OreFields.stamp(overworld, cells);
        }
        data.spawnX = spawn.getX();
        data.spawnZ = spawn.getZ();
        data.stamped = true;
        data.setDirty();
        LOGGER.info("Stamped the starting ore fields around {} ({} blocks)", spawn, n);
    }

    private static StartingFields load(CompoundTag tag, HolderLookup.Provider registries) {
        StartingFields d = new StartingFields();
        d.spawnX = tag.getInt(KEY_SPAWN_X);
        d.spawnZ = tag.getInt(KEY_SPAWN_Z);
        d.stamped = tag.getBoolean(KEY_STAMPED);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_SPAWN_X, spawnX);
        tag.putInt(KEY_SPAWN_Z, spawnZ);
        tag.putBoolean(KEY_STAMPED, stamped);
        return tag;
    }
}
