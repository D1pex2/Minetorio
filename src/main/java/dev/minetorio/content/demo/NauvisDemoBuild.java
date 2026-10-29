package dev.minetorio.content.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in offline build on a real Nauvis dedicated server, with a separate run directory. */
public final class NauvisDemoBuild {
    public static final String OUT="minetorio.nauvisDemo.out";
    private static BlockPos feet;
    private static final List<ChunkPos> forced=new ArrayList<>();
    private static int ticks;
    private NauvisDemoBuild() {}
    public static void register() {
        if(System.getProperty(OUT)==null) return;
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->{
            var server=e.getServer();var level=server.overworld();var spawn=level.getSharedSpawnPos();
            int x=spawn.getX(),z=spawn.getZ()+144;
            int y=level.getChunk(x>>4,z>>4).getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)+1;
            feet=new BlockPos(x,y,z);
            var area=DemoBuilder.layout(DemoBuilder.originAround(feet)).area();
            for(int cx=area.minX()>>4;cx<=area.maxX()>>4;cx++)for(int cz=area.minZ()>>4;cz<=area.maxZ()>>4;cz++) {
                level.getChunk(cx,cz);level.setChunkForced(cx,cz,true);forced.add(new ChunkPos(cx,cz));
            }
            try {
                int result=server.getCommands().getDispatcher().execute("minetorio demo "+x+" "+y+" "+z,server.createCommandSourceStack());
                if(result!=1)throw new IllegalStateException("Demo command failed: "+result);
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new IllegalStateException(ex);}
            ticks=0;
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{
            if(feet==null || ++ticks<600) return;
            for(var c:forced)e.getServer().overworld().setChunkForced(c.x,c.z,false);
            feet=feet.immutable();
            e.getServer().halt(false);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{
            if(feet!=null)dev.minetorio.gametest.DemoWorldBuild.pack(e.getServer(),Path.of(System.getProperty(OUT)),feet);
        });
    }
}
