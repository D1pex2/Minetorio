package dev.minetorio.client.robot;

import dev.minetorio.content.robot.RoboportMenu;
import dev.minetorio.core.robot.RobotStats;
import net.minecraft.client.gui.GuiGraphics;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.client.gui.FactoryContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Roboport GUI: the robot slots, robots available/working, the energy buffer and the area size. */
public class RoboportScreen extends FactoryContainerScreen<RoboportMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL = 0xFFC6C6C6;
    private static final int TEXT = FactoryTheme.TEXT;

    public RoboportScreen(RoboportMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 406; imageHeight = 190;
        inventoryLabelX=210;inventoryLabelY=6;
    }

    @Override
    protected void renderFactory(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderFactory(g, mouseX, mouseY, partialTick);
        // stage 4: the roboport's logistic network, in a panel to the right
        dev.minetorio.client.logistics.NetworkStatsPanel.render(g, font, leftPos + imageWidth + 4, topPos, menu.containerId, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        FactoryTheme.panel(g,leftPos,topPos,196,imageHeight);FactoryTheme.panel(g,leftPos+202,topPos,204,imageHeight);
        FactoryTheme.slots(g,leftPos,topPos,menu.slots);
        FactoryTheme.bar(g,leftPos+8,topPos+80,176,9,menu.energyMj()/(RobotStats.ROBOPORT_BUFFER_J/1_000_000.0),FactoryTheme.ACCENT);
        for (int i = 0; i < RobotStats.ROBOT_SLOTS; i++) slotFrame(g, leftPos + RoboportMenu.SLOT_X - 1 + i * 18, topPos + RoboportMenu.SLOT_Y - 1);
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        FactoryTheme.slot(g,x,y);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        FactoryTheme.title(g,font,title,10,6);
        FactoryTheme.label(g,font,playerInventoryTitle,inventoryLabelX,inventoryLabelY);
        int area = 2 * RobotStats.CONSTRUCTION_RADIUS;
        g.drawString(font, Component.translatable("minetorio.roboport.robots", menu.available(), menu.out()), 8, 42, TEXT, false);
        g.drawString(font, Component.translatable("minetorio.roboport.energy", String.format("%.1f", menu.energyMj()),
                RobotStats.ROBOPORT_BUFFER_J / 1_000_000L), 8, 53, TEXT, false);
        g.drawString(font, Component.translatable("minetorio.roboport.area", area, area), 8, 64, TEXT, false);
    }
}
