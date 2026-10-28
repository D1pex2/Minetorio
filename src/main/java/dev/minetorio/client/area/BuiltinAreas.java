package dev.minetorio.client.area;

import dev.minetorio.Minetorio;
import dev.minetorio.client.alt.MachineIndex;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.machine.ElectricDrillBlockEntity;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.power.PowerPole;
import dev.minetorio.content.power.PowerPoleEntity;
import dev.minetorio.core.power.PoleType;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.core.area.AreaMath;
import dev.minetorio.core.map.RadarLogic;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
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
        for (Block pole : List.of(ModBlocks.SMALL_ELECTRIC_POLE.get(), ModBlocks.MEDIUM_ELECTRIC_POLE.get(),
                ModBlocks.BIG_ELECTRIC_POLE.get(), ModBlocks.SUBSTATION.get())) {
            AreaOverlays.register(pole, BuiltinAreas::pole);
        }
        drill(ModBlocks.BURNER_MINING_DRILL.get(), BurnerDrillBlockEntity.AREA);
        drill(ModBlocks.ELECTRIC_MINING_DRILL.get(), ElectricDrillBlockEntity.AREA);
        AreaOverlays.register(ModBlocks.RADAR.get(), BuiltinAreas::radar);
    }

    /** The supply area (per pole type) and wire reach ring of a pole at {@code t}, its footprint taken from its block. */
    private static void pole(AreaOverlays.Target t, AreaOverlays.Sink out) {
        BlockState state = t.level().getBlockState(t.pos());
        Block block = t.preview() || !(state.getBlock() instanceof PowerPole) ? held(t) : state.getBlock();
        PoleType type = PowerPole.typeOf(block);
        if (type == null) return;
        BlockPos min = footprintMin(block, t.pos(), t.facing());
        if (t.preview()) {
            for (PowerPoleEntity other : MachineIndex.poles()) {
                BlockPos q = other.footprintMin();
                if (other.isRemoved() || other.getBlockPos().equals(t.pos())) continue;
                if (Math.abs(q.getX() - min.getX()) > OTHER_POLES_RANGE || Math.abs(q.getZ() - min.getZ()) > OTHER_POLES_RANGE) continue;
                PoleType ot = other.poleType();
                out.fill(AreaMath.poleSupply(q.getX(), q.getZ(), ot.size(), ot.supplyMargin()), OTHER_SUPPLY, q.getY());
            }
        }
        out.fill(AreaMath.poleSupply(min.getX(), min.getZ(), type.size(), type.supplyMargin()), SUPPLY, t.pos().getY());
        double half = type.size() / 2.0;
        out.circle(min.getX() + half, min.getZ() + half, type.wireReach(), WIRE_REACH, t.pos().getY());
    }

    /** The block of the pole item the player holds (previews ask before anything is placed). */
    private static Block held(AreaOverlays.Target t) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            for (var hand : net.minecraft.world.InteractionHand.values()) {
                if (player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof PowerPole) {
                    return bi.getBlock();
                }
            }
        }
        return t.level().getBlockState(t.pos()).getBlock();
    }

    /** Corner of the footprint of a pole of {@code block} at {@code pos} (its controller/lower block) facing {@code facing}. */
    private static BlockPos footprintMin(Block block, BlockPos pos, Direction facing) {
        if (!(block instanceof MultiblockControllerBlock mb)) return pos;
        BlockPos min = pos;
        for (BlockPos c : MultiblockControllerBlock.footprint(pos, facing, mb.shape())) {
            min = new BlockPos(Math.min(min.getX(), c.getX()), Math.min(min.getY(), c.getY()), Math.min(min.getZ(), c.getZ()));
        }
        return min;
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
