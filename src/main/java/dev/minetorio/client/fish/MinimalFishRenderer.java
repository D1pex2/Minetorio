package dev.minetorio.client.fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minetorio.Minetorio;

import dev.minetorio.content.fish.MinimalFishEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws Factorio's fish: a slim silver-blue fish (own model and 32×32 texture
 * {@code textures/entity/fish.png}, made by {@code art/gen_world.py}) whose tail beats as it swims;
 * out of water it lies on its side like vanilla fish.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public class MinimalFishRenderer extends MobRenderer<MinimalFishEntity, MinimalFishRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "fish"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "textures/entity/fish.png");

    public MinimalFishRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.25f);
    }

    @Override
    public ResourceLocation getTextureLocation(MinimalFishEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(MinimalFishEntity entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, pose, bob, yBodyRot, partialTick, scale);
        pose.mulPose(Axis.YP.rotationDegrees(4.0f * Mth.sin(0.6f * bob)));
        if (!entity.isInWater()) {
            pose.translate(0.1f, 0.1f, -0.1f);
            pose.mulPose(Axis.ZP.rotationDegrees(90.0f));
        }
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MinimalFishEntity.FISH.get(), MinimalFishRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, Model::createLayer);
    }

    /** Body 3×4×8 with a tapered head, a tall forked tail fin, a dorsal and a belly fin. */
    public static class Model extends HierarchicalModel<MinimalFishEntity> {
        private final ModelPart root;
        private final ModelPart tail;

        public Model(ModelPart root) {
            this.root = root;
            this.tail = root.getChild("tail");
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition p = mesh.getRoot();
            p.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -2, -4, 3, 4, 8), PartPose.offset(0, 21.5f, 0));
            p.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 12).addBox(-1, -1.5f, -6, 2, 3, 2), PartPose.offset(0, 21.5f, 0));
            p.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(14, 12).addBox(0, -2.5f, 0, 0, 5, 4), PartPose.offset(0, 21.5f, 4));
            p.addOrReplaceChild("dorsal", CubeListBuilder.create().texOffs(22, 0).addBox(0, -2, -2, 0, 2, 4), PartPose.offset(0, 19.5f, 0));
            p.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(22, 6).addBox(0, 0, -1, 0, 1, 3), PartPose.offset(0, 23.5f, 0));
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public ModelPart root() { return root; }

        @Override
        public void setupAnim(MinimalFishEntity fish, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            float amp = fish.isInWater() ? 1.0f : 1.6f;
            tail.yRot = -amp * 0.45f * Mth.sin(0.6f * ageInTicks);
        }
    }
}
