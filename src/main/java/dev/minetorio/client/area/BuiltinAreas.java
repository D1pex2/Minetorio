package dev.minetorio.client.area;

import dev.minetorio.Minetorio;
import dev.minetorio.client.alt.MachineIndex;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.power.ElectricPoleBlockEntity;
import dev.minetorio.content.power.PowerGridManager;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.area.AreaMath;
import dev.minetorio.core.map.RadarLogic;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The phase 1 area highlights: pole supply area and wire reach (and, while placing a pole, the
 * supply areas of the poles around, faintly, to show coverage gaps), drill mining areas and the
 * radar's continuous-scan area.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class BuiltinAreas {
    /** Supply area tint (Factorio's pale blue). */
    public static final int SUPPLY = 0x4A6CB8FF;
    /** Supply areas of the other poles while placing one. */
    public static final int OTHER_SUPPLY = 0x226CB8FF;
    public static final int WIRE_REACH = 0x80E8F0FF;
    public static final int MINING = 0x4A63E07A;
    public static final int RADAR = 0xC05AA0FF;
    /** Other poles farther than this (horizontally) from the previewed one are left out. */
    public static final int OTHER_POLES_RANGE = 32;

    private BuiltinAreas() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(BuiltinAreas::register);
    }

    static void register() {
        AreaOverlays.register(ModBlocks.SMALL_ELECTRIC_POLE.get(), BuiltinAreas::pole);
        drill(ModBlocks.BURNER_MINING_DRILL.get(), BurnerDrillBlockEntity.AREA);
        drill(ModBlocks.ELECTRIC_MINING_DRILL.get(), ElectricDrillBlockEntity.AREA);
        AreaOverlays.register(ModBlocks.RADAR.get(), BuiltinAreas::radar);
    }

    private static void pole(AreaOverlays.Target t, AreaOverlays.Sink out) {
        BlockPos p = t.pos();
        int r = PowerGridManager.SUPPLY_RADIUS;
        if (t.preview()) {
            for (ElectricPoleBlockEntity other : MachineIndex.poles()) {
                BlockPos q = other.getBlockPos();
                if (other.isRemoved() || q.equals(p)) continue;
                if (Math.abs(q.getX() - p.getX()) > OTHER_POLES_RANGE || Math.abs(q.getZ() - p.getZ()) > OTHER_POLES_RANGE) continue;
                out.fill(AreaMath.poleSupply(q.getX(), q.getZ(), r), OTHER_SUPPLY, q.getY());
            }
        }
        out.fill(AreaMath.poleSupply(p.getX(), p.getZ(), r), SUPPLY, p.getY());
        out.circle(p.getX() + 0.5, p.getZ() + 0.5, PowerGridManager.WIRE_REACH, WIRE_REACH, p.getY());
    }

    private static void drill(Block block, int areaSize) {
        MultiblockShape s = ((MultiblockControllerBlock) block).shape();
        AreaOverlays.register(block, (t, out) -> out.fill(AreaMath.drillArea(t.pos().getX(), t.pos().getZ(),
                t.facing().getStepX(), t.facing().getStepZ(), s.width(), s.depth(), areaSize), MINING, t.pos().getY()));
    }

    private static void radar(AreaOverlays.Target t, AreaOverlays.Sink out) {
        MultiblockShape s = ((MultiblockControllerBlock) ModBlocks.RADAR.get()).shape();
        List<BlockPos> cells = MultiblockControllerBlock.footprint(t.pos(), t.facing(), s);
        double cx = 0, cz = 0;
        for (BlockPos c : cells) {
            cx += c.getX() + 0.5;
            cz += c.getZ() + 0.5;
        }
        int chunkX = ((int) Math.floor(cx / cells.size())) >> 4, chunkZ = ((int) Math.floor(cz / cells.size())) >> 4;
        out.outline(AreaMath.chunkSquare(chunkX, chunkZ, RadarLogic.NEAR_RADIUS), RADAR, t.pos().getY());
    }
}
