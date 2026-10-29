package dev.minetorio.client.oilrender;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.multiblock.MultiblockControllerBlock;
import dev.minetorio.content.multiblock.MultiblockControllerBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.oil.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The same assembly is used by live machines, blueprints and construction ghosts. */
public final class OilRender {
    private OilRender() {}
    private static final Map<MultiblockControllerBlockEntity, Animation> ANIMATIONS = new WeakHashMap<>();
    private static final class Animation { double last, phase; boolean initialized; }
    public static final ModelResourceLocation BEAM = MachineRender.model("block/pumpjack_beam");
    public static final ModelResourceLocation FIRE = MachineRender.model("block/steel_furnace_fire");
    public static List<ModelResourceLocation> models() {
        List<ModelResourceLocation> all = new ArrayList<>();
        OilModelParts.PARTS.values().forEach(parts -> parts.forEach(p -> all.add(MachineRender.model("block/" + p.model()))));
        all.add(BEAM); all.add(FIRE);
        return all;
    }
    public static void assemble(BlockState state, double time, boolean working, PoseStack pose, MachineRender.PartSink sink) {
        if (!(state.getBlock() instanceof MultiblockControllerBlock block)) return;
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        List<OilModelParts.Part> parts = OilModelParts.PARTS.get(name);
        if (parts == null) return;
        pose.pushPose();
        MachineRender.toCenter(pose, state.getValue(MultiblockControllerBlock.FACING), block.shape());
        for (OilModelParts.Part part : parts) {
            pose.pushPose();
            positionPart(pose,part);
            sink.part(MachineRender.model("block/" + part.model()), pose);
            pose.popPose();
        }
        if (name.equals("pumpjack")) {
            pose.pushPose();
            pose.translate(0, 26.0 / 16, 0);
            pose.mulPose(Axis.ZP.rotationDegrees((float) (Math.sin(time * Math.PI / 20) * 12)));
            pose.translate(-0.5, 0, -0.5);
            sink.part(BEAM, pose);
            pose.popPose();
        }
        if (name.equals("steel_furnace") && state.getValue(SteelFurnaceBlock.LIT)) {
            pose.pushPose(); pose.translate(-0.5, 0, -0.5);
            sink.part(FIRE, pose, false, true); pose.popPose();
        }
        pose.popPose();
    }
    static void positionPart(PoseStack pose,OilModelParts.Part part) {
        // The part exporter adds eight pixels on all three axes, including height.
        pose.translate(part.x()/16.0-0.5,part.y()/16.0-0.5,part.z()/16.0-0.5);
    }
    public static class Renderer<T extends MultiblockControllerBlockEntity> implements BlockEntityRenderer<T> {
        private final ModelBlockRenderer renderer;
        public Renderer(BlockEntityRendererProvider.Context ctx) { renderer = ctx.getBlockRenderDispatcher().getModelRenderer(); }
        @Override public void render(T be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            boolean working = be instanceof PumpjackBlockEntity jack ? jack.isWorking()
                    : be instanceof OilProcessingBlockEntity process && process.isWorking();
            double time = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partial;
            Animation animation = ANIMATIONS.computeIfAbsent(be, b -> new Animation());
            if (animation.initialized && working) animation.phase += Math.max(0, Math.min(2, time - animation.last));
            animation.last = time; animation.initialized = true;
            assemble(be.getBlockState(), animation.phase, working, pose, MachineRender.sink(renderer, buffers, be, light, overlay));
        }
        @Override public AABB getRenderBoundingBox(T be) {
            MultiblockShape shape = ((MultiblockControllerBlock) be.getBlockState().getBlock()).shape();
            Direction facing = be.getBlockState().getValue(MultiblockControllerBlock.FACING);
            return MachineRender.footprintBox(be.getBlockPos(), facing, shape).expandTowards(0, 1, 0);
        }
    }
}
