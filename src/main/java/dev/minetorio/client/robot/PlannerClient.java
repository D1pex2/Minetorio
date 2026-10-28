package dev.minetorio.client.robot;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minetorio.Minetorio;
import dev.minetorio.client.ModKeys;
import dev.minetorio.client.selection.AreaSelection;
import dev.minetorio.content.robot.MarkPayloads;
import dev.minetorio.content.robot.PlannerItems;
import dev.minetorio.content.robot.PlannerPayloads;
import dev.minetorio.content.robot.Planners;
import dev.minetorio.core.selection.SelectionBox;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the planners: the shortcut keys (Z: deconstruction planner, U: upgrade planner,
 * like Factorio's shortcut bar: a free planner into the hand), their {@link AreaSelection} tools
 * (red / light blue boxes for marking / unmarking, green / yellow for upgrades), their settings
 * screens and the client copy of the marks (dropped with their chunk).
 */
public final class PlannerClient {
    public static final KeyMapping DECONSTRUCTION = new KeyMapping("key.minetorio.deconstruction_planner",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, ModKeys.CATEGORY);
    public static final KeyMapping UPGRADE = new KeyMapping("key.minetorio.upgrade_planner",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, ModKeys.CATEGORY);

    private PlannerClient() {}

    /** Mod-bus: key registration. */
    @EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(DECONSTRUCTION);
            event.register(UPGRADE);
        }
    }

    /** Called on client setup: the selection tools. */
    public static void register() {
        AreaSelection.register(new AreaSelection.Tool() {
            @Override
            public boolean matches(ItemStack held) { return held.getItem() instanceof PlannerItems.DeconstructionPlanner; }

            @Override
            public void apply(SelectionBox box, boolean alternative) {
                PacketDistributor.sendToServer(PlannerPayloads.Select.of(box, alternative));
            }

            @Override
            public void openSettings() { Minecraft.getInstance().setScreen(new DeconstructionPlannerScreen()); }

            @Override
            public int color(boolean alternative) { return alternative ? 0xFF7FD4FF : 0xFFFF3B30; }
        });
        AreaSelection.register(new AreaSelection.Tool() {
            @Override
            public boolean matches(ItemStack held) { return held.getItem() instanceof PlannerItems.UpgradePlanner; }

            @Override
            public void apply(SelectionBox box, boolean alternative) {
                PacketDistributor.sendToServer(PlannerPayloads.Select.of(box, alternative));
            }

            @Override
            public void openSettings() { Minecraft.getInstance().setScreen(new UpgradePlannerScreen()); }

            @Override
            public int color(boolean alternative) { return alternative ? 0xFFFFD040 : 0xFF40E060; }
        });
    }

    /** Game-bus events. */
    @EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (DECONSTRUCTION.consumeClick()) {
                if (mc.screen == null && mc.player != null) PacketDistributor.sendToServer(new PlannerPayloads.Give(Planners.Kind.DECONSTRUCTION.ordinal()));
            }
            while (UPGRADE.consumeClick()) {
                if (mc.screen == null && mc.player != null) PacketDistributor.sendToServer(new PlannerPayloads.Give(Planners.Kind.UPGRADE.ordinal()));
            }
        }

        @SubscribeEvent
        public static void onChunkUnload(ChunkEvent.Unload event) {
            if (event.getLevel().isClientSide()) MarkPayloads.CLIENT.drop(event.getChunk().getPos().x, event.getChunk().getPos().z);
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MarkPayloads.CLIENT.clear();
        }
    }
}
