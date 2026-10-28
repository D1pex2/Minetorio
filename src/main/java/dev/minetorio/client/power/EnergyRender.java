package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.core.power.PoleType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;

/**
 * The models of the solar panel, accumulator and multiblock poles (art/gen_energy.py). Each machine is
 * authored around its footprint center in the north orientation and cut into pieces of 32 px: piece
 * {@code k} is drawn 2 k blocks up. The accumulator's lit lamp is authored around model (8, 0, 8)
 * and drawn at {@link #ACC_LAMPS} (machine px, as art/gen_energy.py ACC_LAMP_POS).
 */
public final class EnergyRender {
    public static final List<ModelResourceLocation> SOLAR_PANEL = pieces("solar_panel", 1);
    public static final List<ModelResourceLocation> ACCUMULATOR = pieces("accumulator", 1);
    public static final ModelResourceLocation ACCUMULATOR_LAMP = MachineRender.model("block/accumulator_lamp_on");
    public static final List<ModelResourceLocation> MEDIUM_POLE = pieces("medium_electric_pole", 2);
    public static final List<ModelResourceLocation> BIG_POLE = pieces("big_electric_pole", 3);
    public static final List<ModelResourceLocation> SUBSTATION = pieces("substation", 2);
    /** Lamp centers on the accumulator's front face, machine px. */
    static final double[][] ACC_LAMPS = {{-8, 2, -15}, {-4, 2, -15}, {0, 2, -15}, {4, 2, -15}, {8, 2, -15}};

    private EnergyRender() {}

    private static List<ModelResourceLocation> pieces(String name, int n) {
        List<ModelResourceLocation> out = new ArrayList<>();
        for (int k = 0; k < n; k++) out.add(MachineRender.model("block/" + name + "_p" + k));
        return List.copyOf(out);
    }

    /** Every model the renderers draw outside blockstates (registered as additional models). */
    public static List<ModelResourceLocation> all() {
        List<ModelResourceLocation> out = new ArrayList<>();
        out.addAll(SOLAR_PANEL);
        out.addAll(ACCUMULATOR);
        out.add(ACCUMULATOR_LAMP);
        out.addAll(MEDIUM_POLE);
        out.addAll(BIG_POLE);
        out.addAll(SUBSTATION);
        return out;
    }

    public static List<ModelResourceLocation> poleModels(PoleType type) {
        return switch (type) {
            case BIG -> BIG_POLE;
            case SUBSTATION -> SUBSTATION;
            default -> MEDIUM_POLE;
        };
    }

    /** Lays out a machine's pieces from the controller block's corner. */
    public static void assemble(List<ModelResourceLocation> pieces, Direction facing, MultiblockShape shape, PoseStack pose,
                                MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        for (int k = 0; k < pieces.size(); k++) {
            pose.pushPose();
            pose.translate(-0.5, 2 * k, -0.5);
            sink.part(pieces.get(k), pose);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * The accumulator with {@code lit} lamps glowing (from the left); {@code blink} (0..4, or −1)
     * is a lamp drawn only every other half second (the next lamp filling while charging, the top
     * one emptying while discharging).
     */
    public static void assembleAccumulator(Direction facing, MultiblockShape shape, int lit, int blink, long time,
                                           PoseStack pose, MachineRender.PartSink sink) {
        assemble(ACCUMULATOR, facing, shape, pose, sink);
        pose.pushPose();
        MachineRender.toCenter(pose, facing, shape);
        boolean blinkOn = (time / 10) % 2 == 0;
        for (int i = 0; i < ACC_LAMPS.length; i++) {
            boolean on = i < lit;
            if (i == blink) on = blinkOn;
            if (!on) continue;
            pose.pushPose();
            pose.translate(ACC_LAMPS[i][0] / 16, ACC_LAMPS[i][1] / 16, ACC_LAMPS[i][2] / 16);
            pose.translate(-0.5, 0, -0.5);
            sink.part(ACCUMULATOR_LAMP, pose, false, true);
            pose.popPose();
        }
        pose.popPose();
    }
}
