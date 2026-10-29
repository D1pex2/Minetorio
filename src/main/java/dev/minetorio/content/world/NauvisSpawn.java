package dev.minetorio.content.world;

import com.mojang.logging.LogUtils;
import dev.minetorio.Minetorio;
import dev.minetorio.content.registry.ModWorld;
import dev.minetorio.core.world.CrashSite;
import dev.minetorio.core.world.NauvisTerrain;
import dev.minetorio.core.world.SpawnFinder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

/**
 * The start of a Minetorio world (Factorio freeplay): when the Overworld is created with the
 * Minetorio noise settings, the spawn goes to the flat dry spot {@link SpawnFinder} picks near the
 * origin (the starting ore fields are then stamped around it, see StartingFields), and the crashed
 * ship ({@link CrashSite}) lands next to it with the starter kit in its cargo chest
 * (loot table {@value #LOOT}).
 */
public final class NauvisSpawn {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String LOOT = "minetorio:chests/crash_site";
    public static final ResourceKey<LootTable> LOOT_TABLE =
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "chests/crash_site"));

    private NauvisSpawn() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NauvisSpawn::onCreateSpawn);
    }

    private static void onCreateSpawn(LevelEvent.CreateSpawnPosition e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        NauvisTerrain t = NauvisDensity.terrain(level);
        if (t == null) return;
        int[] s = SpawnFinder.find(t);
        int y = t.surfaceY(s[0], s[1]) + 1;
        BlockPos spawn = new BlockPos(s[0], y, s[1]);
        e.getSettings().setSpawn(spawn, 0.0f);
        e.setCanceled(true);
        placeCrashSite(level, spawn.getX() + CrashSite.ANCHOR_DX, spawn.getZ() + CrashSite.ANCHOR_DZ);
        LOGGER.info("Minetorio spawn at {}, crash site placed", spawn);
    }

    /** Places the crash site with its anchor at column (ax, az); every piece on its own column's ground. Returns the chest position. */
    public static BlockPos placeCrashSite(ServerLevel level, int ax, int az) {
        BlockPos chest = null;
        // Read every original column before placing any hull layers, which alter the heightmap.
        Map<Long, Integer> groundByColumn = new HashMap<>();
        for (CrashSite.Piece p : CrashSite.layout()) {
            int x = ax + p.dx(), z = az + p.dz();
            groundByColumn.computeIfAbsent(BlockPos.asLong(x, 0, z), ignored -> groundY(level, x, z));
        }
        for (CrashSite.Piece p : CrashSite.layout()) {
            int x = ax + p.dx();
            int z = az + p.dz();
            int ground = groundByColumn.get(BlockPos.asLong(x, 0, z));
            BlockPos pos = new BlockPos(x, ground + 1 + p.dy(), z);
            BlockState state = switch (p.kind()) {
                case HULL -> ModWorld.SHIP_WRECK_HULL.get().defaultBlockState();
                case DEBRIS -> ModWorld.SHIP_WRECK_DEBRIS.get().defaultBlockState();
                case CHEST -> ModWorld.CRASH_SITE_CHEST.get().defaultBlockState().setValue(CrashSiteChestBlock.FACING, Direction.WEST);
            };
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (p.kind() == CrashSite.Kind.CHEST) {
                chest = pos;
                if (level.getBlockEntity(pos) instanceof CrashSiteChestBlockEntity be) be.setLootTable(LOOT_TABLE, level.getSeed());
            }
        }
        return chest;
    }

    /** The top ground block of column (x, z), below plants, trees and rocks. */
    private static int groundY(ServerLevel level, int x, int z) {
        // Level#getHeight returns minY for an unloaded column instead of generating its chunk.
        int top = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        while (p.getY() > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(p);
            boolean clutter = s.getBlock() instanceof NaturalObjectBlock || (!s.isAir() && s.canBeReplaced() && s.getFluidState().isEmpty());
            if (clutter) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            else if (!s.isAir()) break;
            p.move(Direction.DOWN);
        }
        return p.getY();
    }
}
