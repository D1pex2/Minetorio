package dev.minetorio.client.robot;

import dev.minetorio.content.robot.RoboportMenu;
import dev.minetorio.core.robot.RobotStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Roboport GUI: the robot slots, robots available/working, the energy buffer and the area size. */
public class RoboportScreen extends AbstractContainerScreen<RoboportMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL = 0xFFC6C6C6;
    private static final int TEXT = 0x404040;

    public RoboportScreen(RoboportMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 168;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // stage 4: the roboport's logistic network, in a panel to the right
        dev.minetorio.client.logistics.NetworkStatsPanel.render(g, font, leftPos + imageWidth + 4, topPos, menu.containerId, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int rows = 3;
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, rows * 18 + 17);
        g.blit(TEXTURE, leftPos, topPos + rows * 18 + 17, 0, 126, imageWidth, 96);
        g.fill(leftPos + 7, topPos + 17, leftPos + 169, topPos + 17 + rows * 18, PANEL); // our own slot row and text below
        for (int i = 0; i < RobotStats.ROBOT_SLOTS; i++) slotFrame(g, leftPos + RoboportMenu.SLOT_X - 1 + i * 18, topPos + RoboportMenu.SLOT_Y - 1);
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int area = 2 * RobotStats.CONSTRUCTION_RADIUS;
        g.drawString(font, Component.translatable("minetorio.roboport.robots", menu.available(), menu.out()), 8, 42, TEXT, false);
        g.drawString(font, Component.translatable("minetorio.roboport.energy", String.format("%.1f", menu.energyMj()),
                RobotStats.ROBOPORT_BUFFER_J / 1_000_000L), 8, 53, TEXT, false);
        g.drawString(font, Component.translatable("minetorio.roboport.area", area, area), 8, 64, TEXT, false);
    }
}
