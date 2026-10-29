package dev.minetorio.client.handcraft;

import dev.minetorio.Minetorio;
import dev.minetorio.content.handcraft.ClientHandcraft;
import dev.minetorio.core.handcraft.HandcraftQueue;
import dev.minetorio.core.character.HudLayout;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * HUD layer: the hand-crafting queue above the character vitals and shortcut rows, with progress
 * and craft counts. Hidden when
 * the queue is empty or the HUD is hidden (F1).
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class QueueHud {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "handcraft_queue");
    private static final int STEP = 18;

    private QueueHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ID, QueueHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        List<HandcraftQueue.Entry> entries = ClientHandcraft.entries();
        if (entries.isEmpty() || mc.options.hideGui || mc.player == null) return;
        int shown = Math.min(entries.size(), 10);
        int rows=mc.player instanceof dev.minetorio.content.character.CharacterShortcuts s?s.visibleRows():1;
        HudLayout layout = HudLayout.of(g.guiWidth(), g.guiHeight(), rows);
        g.pose().pushPose();
        g.pose().translate(layout.left(), layout.queueTop(), 0);
        g.pose().scale(layout.scale(), layout.scale(), 1);
        int y = 0;
        for (int i = 0; i < shown; i++) {
            HandcraftQueue.Entry e = entries.get(i);
            int x = i * STEP;
            dev.minetorio.client.gui.FactoryTheme.slot(g,x,y);
            ItemStack stack = HandcraftScreen.icon(e.recipeId());
            g.renderItem(stack, x + 1, y + 1);
            boolean more = i == shown - 1 && entries.size() > shown;
            String label = more ? "+" + (entries.size() - shown + 1) : e.times() > 1 ? String.valueOf(e.times()) : null;
            g.renderItemDecorations(mc.font, stack, x + 1, y + 1, label);
            if (i == 0) HandcraftScreen.drawProgress(g, x, y + 16, 18, ClientHandcraft.frontProgress());
        }
        g.pose().popPose();
    }
}
