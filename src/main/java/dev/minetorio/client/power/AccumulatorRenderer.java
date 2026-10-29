package dev.minetorio.client.power;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.minetorio.client.machine.MachineRender;
import dev.minetorio.content.power.AccumulatorBlockEntity;
import dev.minetorio.core.power.Accumulator;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * Accumulator: its body and the charge-level lamps along the front, lit from the left as many as
 * the synced {@link AccumulatorBlockEntity#lamps()}; while charging the next lamp blinks, while
 * discharging the top lit one does.
 */
public class AccumulatorRenderer implements BlockEntityRenderer<AccumulatorBlockEntity> {
    private final ModelBlockRenderer modelRenderer;

    public AccumulatorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.modelRenderer = ctx.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(AccumulatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int lit = be.lamps();
        int blink = switch (be.flow()) {
            case CHARGING -> lit < AccumulatorBlockEntity.LAMPS ? lit : -1;
            case DISCHARGING -> lit - 1;
            default -> -1;
        };
        if (be.flow() == Accumulator.Flow.DISCHARGING && blink >= 0) lit = blink; // the blinking one is drawn by blink
        long time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        EnergyRender.assembleAccumulator(be.facing(), be.shape(), lit, blink, time, pose,
                MachineRender.sink(modelRenderer, buffers, be, light, overlay));
    }

    @Override
    public AABB getRenderBoundingBox(AccumulatorBlockEntity be) {
        return MachineRender.footprintBox(be.getBlockPos(), be.facing(), be.shape());
    }
}
