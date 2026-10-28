package dev.minetorio.client.logistics;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.content.logistics.LogisticsPayloads;
import dev.minetorio.content.logistics.LogisticsRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client registrations of the logistic network: the logistic robot renderer and models, the chest and
 * personal logistics screens, and the personal logistics key (K by default; Factorio opens personal
 * logistics from the character screen, which Minecraft does not have).
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class LogisticsClient {
    public static final KeyMapping PERSONAL_LOGISTICS = new KeyMapping("key.minetorio.personal_logistics",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, ModKeys.CATEGORY);

    private LogisticsClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(PERSONAL_LOGISTICS);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(), LogisticRobotRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LogisticsRegistry.CHEST_MENU.get(), LogisticChestScreen::new);
        event.register(LogisticsRegistry.PERSONAL_MENU.get(), PersonalLogisticsScreen::new);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(LogisticRobotRenderer.MODEL);
        event.register(LogisticRobotRenderer.ROTOR);
    }

    /**
     * While the player holds a roboport, a logistic robot or a logistic chest: every loaded roboport's
     * logistic area as a green-yellow outline at its floor level (Factorio shows it with those items in hand).
     */
    @SubscribeEvent
    public static void onRenderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !holdsLogisticItem(mc.player)) return;
        var cam = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (net.minecraft.core.BlockPos p : dev.minetorio.content.robot.RoboportBlockEntity.CLIENT_LOADED) {
            if (!(mc.level.getBlockEntity(p) instanceof dev.minetorio.content.robot.RoboportBlockEntity be)) continue;
            var a = dev.minetorio.content.logistics.LogisticsManager.logisticArea(be);
            double y = p.getY() + 0.06;
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, lines, a.minX(), y, a.minZ(), a.maxX() + 1, y + 0.05,
                    a.maxZ() + 1, 0.75f, 0.9f, 0.2f, 1.0f);
        }
        pose.popPose();
        buffers.endBatch(net.minecraft.client.renderer.RenderType.lines());
    }

    private static boolean holdsLogisticItem(net.minecraft.world.entity.player.Player player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var s = player.getItemInHand(hand);
            if (s.is(dev.minetorio.content.registry.ModItems.ROBOPORT.get()) || s.is(LogisticsRegistry.LOGISTIC_ROBOT.get())) return true;
            for (var item : LogisticsRegistry.CHEST_ITEMS.values()) if (s.is(item.get())) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (PERSONAL_LOGISTICS.consumeClick()) {
            if (mc.screen == null && mc.player != null) PacketDistributor.sendToServer(new LogisticsPayloads.OpenPersonal());
        }
    }
}
