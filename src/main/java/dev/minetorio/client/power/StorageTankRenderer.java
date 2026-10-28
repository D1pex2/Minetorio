package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.fluid.StorageTankBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Storage tank body ("storage_tank_body", art/gen_fluid.py): authored around the footprint's
 * center block in the north orientation (too big for the controller's own block model).
 */
public class StorageTankRenderer implements BlockEntityRenderer<StorageTankBlockEntity> {
    public static final ModelResourceLocation BODY = MachineRender.model("block/storage_tank_body");

    private final ModelBlockRenderer modelRenderer;

    public StorageTankRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(StorageTankBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        assemble(be.facing(), pose, MachineRender.sink(modelRenderer, buffers, light, overlay));
    }

    /** Lays the tank out from the controller block's corner (also used for ghosts). */
    public static void assemble(Direction facing, PoseStack pose, MachineRender.PartSink sink) {
        pose.pushPose();
        MachineRender.toCenter(pose, facing, MultiblockShape.STORAGE_TANK);
        pose.translate(-0.5, 0, -0.5);
        sink.part(BODY, pose);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(StorageTankBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), MultiblockShape.STORAGE_TANK);
    }
}
