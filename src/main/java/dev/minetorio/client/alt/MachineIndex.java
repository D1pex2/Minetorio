package dev.minetorio.client.alt;

import dev.minetorio.Minetorio;
import dev.minetorio.content.alt.AltSource;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.power.PowerPoleEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client index of the Minetorio block entities near the player (alt-mode machines, inserters and
 * poles), rebuilt from the loaded chunks every {@value #REFRESH_TICKS} ticks so the renderers never
 * walk the chunks per frame. Each machine entry carries where its icon goes: above the center of
 * its footprint, on top of the machine.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class MachineIndex {
    /** Horizontal reach of the index, in blocks (alt icons show within 32, pole areas a bit beyond). */
    public static final int RADIUS = 48;
    public static final int REFRESH_TICKS = 10;

    /** A machine with an icon: the block entity and the icon's anchor (world coordinates) and base size. */
    public record Machine(BlockEntity be, double x, double y, double z, double size) {}

    private static List<Machine> machines = List.of();
    private static List<InserterBlockEntity> inserters = List.of();
    private static List<PowerPoleEntity> poles = List.of();
    private static int countdown;

    private MachineIndex() {}

    public static List<Machine> machines() { return machines; }

    public static List<InserterBlockEntity> inserters() { return inserters; }

    public static List<PowerPoleEntity> poles() { return poles; }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            machines = List.of();
            inserters = List.of();
            poles = List.of();
            return;
        }
        if (--countdown > 0) return;
        countdown = REFRESH_TICKS;
        rebuild(mc.level, mc.player.blockPosition());
    }

    private static void rebuild(ClientLevel level, BlockPos center) {
        List<Machine> m = new ArrayList<>();
        List<InserterBlockEntity> ins = new ArrayList<>();
        List<PowerPoleEntity> p = new ArrayList<>();
        int c0x = (center.getX() - RADIUS) >> 4, c1x = (center.getX() + RADIUS) >> 4;
        int c0z = (center.getZ() - RADIUS) >> 4, c1z = (center.getZ() + RADIUS) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved()) continue;
                    if (be instanceof AltSource) {
                        Machine e = anchor(be);
                        if (e != null) m.add(e);
                    } else if (be instanceof InserterBlockEntity i) {
                        ins.add(i);
                    } else if (be instanceof PowerPoleEntity pole) {
                        p.add(pole);
                    }
                }
            }
        }
        machines = m;
        inserters = ins;
        poles = p;
    }

    private static Machine anchor(BlockEntity be) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MultiblockControllerBlock block)) return null;
        List<BlockPos> cells = block.footprint(be.getBlockPos(), state);
        if (cells.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, minY = Integer.MAX_VALUE;
        for (BlockPos c : cells) {
            minX = Math.min(minX, c.getX());
            maxX = Math.max(maxX, c.getX());
            minZ = Math.min(minZ, c.getZ());
            maxZ = Math.max(maxZ, c.getZ());
            minY = Math.min(minY, c.getY());
        }
        int width = Math.max(maxX - minX, maxZ - minZ) + 1;
        double top = minY + block.shape().height();
        // a 3-wide machine gets a ~1.1 block icon, a 2-wide one ~0.8, as Factorio scales them with the entity
        double size = 0.35 + 0.25 * width;
        return new Machine(be, (minX + maxX + 1) / 2.0, top + size / 2 + 0.15, (minZ + maxZ + 1) / 2.0, size);
    }
}
