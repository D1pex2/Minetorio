package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.power.MultiblockPoleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Medium/big electric pole and substation: their pieces ({@link EnergyRender}) and their wires ({@link WireRenderer}). */
public class MultiblockPoleRenderer implements BlockEntityRenderer<MultiblockPoleBlockEntity> {
    private final ModelBlockRenderer modelRenderer;

    public MultiblockPoleRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(MultiblockPoleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        EnergyRender.assemble(EnergyRender.poleModels(be.poleType()), be.facing(), be.shape(), pose,
                MachineRender.sink(modelRenderer, buffers, light, overlay));
        WireRenderer.drawWires(be, be.getLevel(), pose, buffers);
    }

    @Override
    public AABB getRenderBoundingBox(MultiblockPoleBlockEntity be) {
        return WireRenderer.wireBox(be, be.getLevel(), be.footprintBox().inflate(0.5, 0, 0.5));
    }
}
