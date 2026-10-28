package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.robot.ConstructionRobotEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Draws a construction robot: its element model (art/gen_robots.py, north = front, ~half a block)
 * turned to its heading and bobbing gently, the carried item hanging below it, and a soft shadow on
 * the ground under it (vanilla entity shadows fade out a block or two above the ground).
 */
public class ConstructionRobotRenderer extends EntityRenderer<ConstructionRobotEntity> {
    public static final ModelResourceLocation MODEL = MachineRender.model("block/construction_robot");
    private static final RenderType SHADOW = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
    private static final int SHADOW_DEPTH = 10;

    private final ItemRenderer items;

    public ConstructionRobotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0;
    }

    @Override
    public void render(ConstructionRobotEntity robot, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(robot, yaw, partialTick, pose, buffers, light);
        float t = robot.tickCount + partialTick;
        float bob = Mth.sin(t * 0.18f + robot.getId()) * 0.06f;
        shadow(robot, partialTick, pose, buffers);

        pose.pushPose();
        pose.translate(0, bob, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, robot.yRotO, robot.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.11f) * 3f)); // slight sway
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, null, model, 1f, 1f, 1f,
                light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();

        ItemStack carried = robot.carried();
        if (!carried.isEmpty()) {
            pose.translate(0, -0.12, 0.02);
            pose.scale(0.55f, 0.55f, 0.55f);
            items.renderStatic(carried, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, robot.level(), robot.getId());
        }
        pose.popPose();
    }

    /** A dark disc on the first solid surface under the robot, fainter the higher it flies. */
    private void shadow(ConstructionRobotEntity robot, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = robot.level();
        Vec3 p = robot.getPosition(partialTick);
        BlockPos.MutableBlockPos m = BlockPos.containing(p).mutable();
        double ground = Double.NaN;
        for (int i = 0; i < SHADOW_DEPTH; i++, m.move(Direction.DOWN)) {
            BlockState s = level.getBlockState(m);
            VoxelShape shape = s.getCollisionShape(level, m);
            if (!shape.isEmpty()) {
                double top = m.getY() + shape.max(Direction.Axis.Y);
                if (top <= p.y + 1e-3) {
                    ground = top;
                    break;
                }
            }
        }
        if (Double.isNaN(ground)) return;
        double h = p.y - ground;
        float alpha = (float) Mth.clamp(0.6 - h * 0.06, 0.15, 0.6);
        float r = 0.32f;
        float y = (float) (ground - p.y + 0.015);
        VertexConsumer vc = buffers.getBuffer(SHADOW);
        Matrix4f mat = pose.last().pose();
        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        vertex(vc, mat, color, -r, y, -r, 0, 0);
        vertex(vc, mat, color, -r, y, r, 0, 1);
        vertex(vc, mat, color, r, y, r, 1, 1);
        vertex(vc, mat, color, r, y, -r, 1, 0);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, int color, float x, float y, float z, float u, float v) {
        org.joml.Vector3f w = mat.transformPosition(x, y, z, new org.joml.Vector3f());
        vc.addVertex(w.x(), w.y(), w.z(), color, u, v, OverlayTexture.NO_OVERLAY, 0xF000F0, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(ConstructionRobotEntity robot) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
