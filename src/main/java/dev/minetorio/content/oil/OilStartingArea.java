package dev.minetorio.content.oil;

import dev.minetorio.content.oilmap.OilMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Once spawn is known, removes generated wells from the starting area. */
public final class OilStartingArea extends SavedData {
    private static final String NAME="minetorio_oil_starting_area";
    private boolean cleared;
    private OilStartingArea() {}
    private static OilStartingArea load(CompoundTag tag, HolderLookup.Provider lookup) {
        OilStartingArea d=new OilStartingArea(); d.cleared=tag.getBoolean("cleared"); return d;
    }
    public static OilStartingArea get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(OilStartingArea::new,OilStartingArea::load),NAME);
    }
    public static void register() { NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->clearOnce(e.getServer().overworld())); }
    public static void clearOnce(ServerLevel level) {
        OilStartingArea data=get(level); if(data.cleared)return;
        BlockPos spawn=level.getSharedSpawnPos(); int radius=OilFields.EXCLUSION_RADIUS;
        int minX=Math.floorDiv(spawn.getX()-radius,16),maxX=Math.floorDiv(spawn.getX()+radius,16);
        int minZ=Math.floorDiv(spawn.getZ()-radius,16),maxZ=Math.floorDiv(spawn.getZ()+radius,16);
        for(int cx=minX;cx<=maxX;cx++) for(int cz=minZ;cz<=maxZ;cz++) {
            for(OilFields.Well well:OilFields.inChunk(level.getSeed(),cx,cz)) {
                if(Math.hypot(well.x()-spawn.getX(),well.z()-spawn.getZ())>=radius)continue;
                level.getChunk(cx,cz);
                int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,well.x(),well.z())-1;
                for(int y=top;y>=Math.max(level.getMinBuildHeight(),top-16);y--) {
                    BlockPos pos=new BlockPos(well.x(),y,well.z());
                    if(level.getBlockState(pos).is(OilRegistry.OIL_SOURCE.get())) {
                        OilMapData.remove(level,pos); level.setBlock(pos,Blocks.DIRT.defaultBlockState(),Block.UPDATE_ALL); break;
                    }
                }
            }
        }
        data.cleared=true; data.setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) { tag.putBoolean("cleared",cleared); return tag; }
}
