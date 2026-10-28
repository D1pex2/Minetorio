package dev.minetorio.client.logistics;

import dev.minetorio.content.logistics.LogisticsPayloads;
import dev.minetorio.content.logistics.NetworkStatsView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The roboport GUI's logistic network panel (Factorio's roboport "logistic network" view), drawn to
 * the right of the roboport GUI: roboports in the network, logistic and construction robots available
 * / total, and the items in the network as icons with counts (most first).
 */
public final class NetworkStatsPanel {
    private static final int W = 176;
    private static final int COLS = 9;
    private static final int ROWS = 5;

    private NetworkStatsPanel() {}

    public static void render(GuiGraphics g, Font font, int x, int y, int containerId, int mouseX, int mouseY) {
        LogisticsPayloads.NetworkStats s = NetworkStatsView.forMenu(containerId);
        int h = 62 + ROWS * 18;
        RequestScreen.panel(g, x, y, W, h);
        g.drawString(font, Component.translatable("minetorio.logistics.network"), x + 8, y + 6, RequestScreen.TEXT, false);
        if (s == null) {
            g.drawString(font, Component.translatable("minetorio.logistics.no_network"), x + 8, y + 20, RequestScreen.TEXT, false);
            return;
        }
        g.drawString(font, Component.translatable("minetorio.logistics.roboports", s.roboports()), x + 8, y + 18, RequestScreen.TEXT, false);
        g.drawString(font, Component.translatable("minetorio.logistics.logistic_robots", s.logisticAvailable(), s.logisticTotal()),
                x + 8, y + 29, RequestScreen.TEXT, false);
        g.drawString(font, Component.translatable("minetorio.logistics.construction_robots", s.constructionAvailable(), s.constructionTotal()),
                x + 8, y + 40, RequestScreen.TEXT, false);
        g.drawString(font, Component.translatable("minetorio.logistics.items", s.items().size()), x + 8, y + 51, RequestScreen.TEXT, false);
        LogisticsPayloads.ItemCount hovered = null;
        for (int i = 0; i < Math.min(s.items().size(), COLS * ROWS); i++) {
            LogisticsPayloads.ItemCount c = s.items().get(i);
            int sx = x + 8 + (i % COLS) * 18, sy = y + 61 + (i / COLS) * 18;
            RequestScreen.slotFrame(g, sx - 1, sy - 1);
            ItemStack stack = RequestScreen.ghost(c.item());
            if (stack.isEmpty()) continue;
            g.renderFakeItem(stack, sx, sy);
            String n = RequestScreen.shortCount(c.count());
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, n, sx + 17 - font.width(n), sy + 9, 0xFFFFFF, true);
            g.pose().popPose();
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) hovered = c;
        }
        if (hovered != null) {
            ItemStack stack = RequestScreen.ghost(hovered.item());
            g.renderComponentTooltip(font, java.util.List.of(stack.getHoverName(), Component.literal(Integer.toString(hovered.count()))),
                    mouseX, mouseY);
        }
    }
}
