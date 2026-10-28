package dev.minetorio.client.logistics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.client.robot.ConstructionRobotRenderer;
import dev.minetorio.content.logistics.LogisticRobotEntity;
import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.robot.RobotFlightPose;
import dev.minetorio.core.robot.RobotStats;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a logistic robot (art/gen_logistics.py: the white-and-red Factorio 1.1 logistic robot hanging
 * under its up-facing fan, north = front) the way {@link ConstructionRobotRenderer} draws a construction
 * robot: turned to its heading, banking into turns, pitching nose-down with its speed, bobbing, the
 * rotor spinning and the cargo hanging below. A personal roboport's robot is a construction robot and
 * uses that model.
 */
public class LogisticRobotRenderer extends EntityRenderer<LogisticRobotEntity> {
    public static final ModelResourceLocation MODEL = MachineRender.model("block/logistic_robot");
    public static final ModelResourceLocation ROTOR = MachineRender.model("block/logistic_robot_rotor");
    private static final float PITCH_PIVOT_Y = 7.5f / 16f;

    private final ItemRenderer items;
    private final Map<LogisticRobotEntity, float[]> pitches = new WeakHashMap<>();

    public LogisticRobotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(LogisticRobotEntity robot, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(robot, yaw, partialTick, pose, buffers, light);
        boolean logistic = robot.kind() == LogisticRobotEntity.Kind.LOGISTIC;
        float t = robot.tickCount + partialTick;
        float bob = Mth.sin(t * 0.18f + robot.getId()) * 0.06f;
        pose.pushPose();
        pose.translate(0, bob, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, robot.yRotO, robot.getYRot())));
        pose.mulPose(Axis.ZP.rotationDegrees(-robot.bank(partialTick)));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.11f) * 3f));
        pose.translate(0, PITCH_PIVOT_Y, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-pitch(robot, partialTick, logistic)));
        pose.translate(0, -PITCH_PIVOT_Y, 0);
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        draw(logistic ? MODEL : ConstructionRobotRenderer.MODEL, pose, vc, light);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(RobotFlightPose.rotorAngle(t, robot.getId())));
        pose.translate(-0.5, 0, -0.5);
        draw(logistic ? ROTOR : ConstructionRobotRenderer.ROTOR, pose, vc, light);
        pose.popPose();
        ItemStack carried = robot.carried();
        if (!carried.isEmpty()) {
            pose.translate(0, -0.12, 0.02);
            pose.scale(0.55f, 0.55f, 0.55f);
            items.renderStatic(carried, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, robot.level(), robot.getId());
        }
        pose.popPose();
    }

    private static void draw(ModelResourceLocation id, PoseStack pose, VertexConsumer vc, int light) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(id);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, null, model, 1f, 1f, 1f,
                light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    private float pitch(LogisticRobotEntity robot, float partialTick, boolean logistic) {
        float[] s = pitches.computeIfAbsent(robot, r -> new float[] {0, 0, r.tickCount});
        if (s[2] != robot.tickCount) {
            double speed = Math.hypot(robot.getX() - robot.xo, robot.getZ() - robot.zo);
            double full = logistic ? LogisticStats.LOGISTIC_SPEED_BLOCKS_PER_TICK : RobotStats.SPEED_BLOCKS_PER_TICK;
            s[0] = s[1];
            s[1] = RobotFlightPose.stepPitch(s[1], RobotFlightPose.targetPitch(speed, full));
            s[2] = robot.tickCount;
        }
        return Mth.lerp(partialTick, s[0], s[1]);
    }

    @Override
    public ResourceLocation getTextureLocation(LogisticRobotEntity robot) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
