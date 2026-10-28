package dev.minetorio.client.robot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.RoboportBlockEntity;
import dev.minetorio.core.robot.ConstructionArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * While the player holds a roboport or a construction robot: the construction area of every loaded
 * roboport as an orange outline at the roboport's floor level (Factorio shows the area while the
 * roboport item is in hand).
 */
public final class ConstructionAreaOverlay {
    private ConstructionAreaOverlay() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ConstructionAreaOverlay::onRender);
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !holdsRobotItem(player)) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (BlockPos p : RoboportBlockEntity.CLIENT_LOADED) {
            if (!(mc.level.getBlockEntity(p) instanceof RoboportBlockEntity be)) continue;
            ConstructionArea a = be.area();
            double y = p.getY() + 0.02;
            LevelRenderer.renderLineBox(pose, lines, a.minX(), y, a.minZ(), a.maxX() + 1, y + 0.05, a.maxZ() + 1,
                    1.0f, 0.6f, 0.1f, 1.0f);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static boolean holdsRobotItem(Player player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var s = player.getItemInHand(hand);
            if (s.is(ModItems.ROBOPORT.get()) || s.is(ModItems.CONSTRUCTION_ROBOT.get())) return true;
        }
        return false;
    }
}
