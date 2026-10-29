package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.power.SolarPanelBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Solar panel: static, its one model piece at the footprint center ({@link EnergyRender}). */
public class SolarPanelRenderer implements BlockEntityRenderer<SolarPanelBlockEntity> {
    private final ModelBlockRenderer modelRenderer;

    public SolarPanelRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(SolarPanelBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        EnergyRender.assemble(EnergyRender.SOLAR_PANEL, be.facing(), be.shape(), pose, MachineRender.sink(modelRenderer, buffers, be, light, overlay));
    }

    @Override
    public AABB getRenderBoundingBox(SolarPanelBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape()).expandTowards(0, 0.5, 0);
    }
}
