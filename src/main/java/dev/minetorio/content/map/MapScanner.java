package dev.minetorio.content.map;

import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.power.ElectricPoleBlock;
import dev.minetorio.core.map.MapPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Computes a chunk's map colors ({@link MapPalette}): per column, the map color of the top block
 * that has one (walking down from the {@code WORLD_SURFACE} heightmap past blocks without a map
 * color, like glass), so every block — including ore-field blocks added later, which only need
 * their own {@link MapColor} — shows up in its own color. Land is shaded by height against the
 * column to the north (higher: bright, lower: dark), water by depth. Our machines (multiblock
 * controllers and parts) and electric poles get flat marker colors; belts, inserters and pipes
 * keep their block map colors (yellow/red/blue belts).
 *
 * <p>Cost: one heightmap lookup and a few block-state reads per column; called only for chunks
 * that are explored for the first time or due a (throttled) re-scan, never per tick.
 */
public final class MapScanner {
    /** Deepest water counted for shading. */
    private static final int MAX_WATER_DEPTH = 12;

    private MapScanner() {}

    public static byte[] scan(ServerLevel level, LevelChunk chunk) {
        byte[] out = new byte[MapPalette.COLUMNS];
        int[] heights = new int[MapPalette.COLUMNS];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        LevelChunk north = level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z - 1);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lx + lz * 16;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                int min = level.getMinBuildHeight();
                MapColor color = MapColor.NONE;
                byte marker = 0;
                for (; y >= min; y--) {
                    BlockState state = chunk.getBlockState(pos.set(x0 + lx, y, z0 + lz));
                    marker = marker(state);
                    if (marker != 0) break;
                    color = state.getMapColor(level, pos);
                    if (color != MapColor.NONE) break;
                }
                heights[i] = y;
                if (marker != 0) {
                    out[i] = marker;
                } else if (color == MapColor.NONE) {
                    out[i] = 0;
                } else if (color == MapColor.WATER) {
                    int depth = 1;
                    while (depth < MAX_WATER_DEPTH && y - depth >= min
                            && chunk.getBlockState(pos.set(x0 + lx, y - depth, z0 + lz)).getMapColor(level, pos) == MapColor.WATER) {
                        depth++;
                    }
                    double d = depth * 0.1 + ((lx + lz) & 1) * 0.2;
                    MapColor.Brightness b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    out[i] = color.getPackedId(b);
                    heights[i] = Integer.MIN_VALUE; // water does not shade the land south of it
                } else {
                    out[i] = color.getPackedId(MapColor.Brightness.NORMAL);
                }
            }
        }
        // height shading of land against the column to the north
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lx + lz * 16;
                if (heights[i] == Integer.MIN_VALUE || out[i] == 0 || MapPalette.isMarker(out[i])) continue;
                int prev;
                if (lz > 0) prev = heights[i - 16];
                else prev = north == null ? heights[i] : north.getHeight(Heightmap.Types.WORLD_SURFACE, lx, 15);
                if (prev == Integer.MIN_VALUE) prev = heights[i];
                int id = (out[i] & 0xFF) >> 2;
                MapColor.Brightness b = heights[i] > prev ? MapColor.Brightness.HIGH
                        : heights[i] < prev ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                out[i] = MapColor.byId(id).getPackedId(b);
            }
        }
        return out;
    }

    /** Marker for our structures, or 0 for blocks drawn in their own map color. */
    static byte marker(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof MultiblockControllerBlock || b instanceof MultiblockPartBlock) return MapPalette.MARKER_MACHINE;
        if (b instanceof ElectricPoleBlock) return MapPalette.MARKER_POLE;
        return 0;
    }
}
