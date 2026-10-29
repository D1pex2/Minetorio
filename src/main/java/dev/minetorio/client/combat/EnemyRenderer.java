package dev.minetorio.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.combat.SmallBiterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Авторская геометрия членистоногого и органического гнезда; без моделей ванильных мобов. */
public final class EnemyRenderer<T extends LivingEntity> extends EntityRenderer<T> {
    public static final ModelResourceLocation BITER = MachineRender.model("block/small_biter_body");
    public static final ModelResourceLocation LEG = MachineRender.model("block/small_biter_leg");
    public static final ModelResourceLocation NEST = MachineRender.model("block/biter_spawner");
    private final boolean biter;
    public EnemyRenderer(EntityRendererProvider.Context context, boolean biter) { super(context); this.biter = biter; shadowRadius = biter ? 0.45f : 2.5f; }
    @Override public void render(T entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, entity.yRotO, entity.getYRot())));
        if (entity.deathTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Math.min(90, (entity.deathTime+partial)*5)));
        if (biter) {
            pose.translate(-0.5, 0, -0.5);
            draw(BITER, pose, buffers, light, entity.hurtTime > 0);
            float walk = entity instanceof SmallBiterEntity b ? b.walkAnimation.position(partial) : 0;
            for (int side : new int[]{-1,1}) for (int i = 0; i < 3; i++) {
                pose.pushPose();
                pose.translate(0.5 + side*0.19, 0.18, 0.32 + i*0.18);
                pose.mulPose(Axis.YP.rotationDegrees(side*70 + Mth.sin(walk*3+i*2+side)*22));
                pose.translate(-0.5, 0, -0.5);
                draw(LEG, pose, buffers, light, entity.hurtTime > 0);
                pose.popPose();
            }
        } else {
            float breath = 1 + Mth.sin((entity.tickCount+partial)*0.08f)*0.025f;
            pose.scale(4, 3*breath, 4); pose.translate(-0.5, 0, -0.5);
            draw(NEST, pose, buffers, light, entity.hurtTime > 0);
        }
        pose.popPose();
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    private static void draw(ModelResourceLocation id, PoseStack pose, MultiBufferSource buffers, int light, boolean hurt) {
        Minecraft mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null,
                mc.getModelManager().getModel(id), 1, hurt ? .45f : 1, hurt ? .45f : 1, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }
    @Override public ResourceLocation getTextureLocation(T entity) { return TextureAtlas.LOCATION_BLOCKS; }
}
