package dev.minetorio.client.robot;

import dev.minetorio.content.robot.PlannerItems;
import dev.minetorio.content.robot.PlannerPayloads;
import dev.minetorio.core.robot.DeconstructionFilter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The deconstruction planner's settings: what it marks (Factorio's "trees and rocks" toggle). */
public class DeconstructionPlannerScreen extends Screen {
    private DeconstructionFilter filter = DeconstructionFilter.MACHINES;
    private Button filterButton;

    public DeconstructionPlannerScreen() {
        super(Component.translatable("item.minetorio.deconstruction_planner"));
    }

    private static Component label(DeconstructionFilter f) {
        return Component.translatable("minetorio.planner.deconstruction.filter",
                Component.translatable("minetorio.planner.filter." + f.name().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void init() {
        var player = Minecraft.getInstance().player;
        if (player != null) filter = PlannerItems.filter(player.getMainHandItem());
        int cx = width / 2, cy = height / 2;
        filterButton = addRenderableWidget(Button.builder(label(filter), b -> {
            filter = filter.next();
            b.setMessage(label(filter));
            PacketDistributor.sendToServer(new PlannerPayloads.SetFilter(filter.ordinal()));
        }).bounds(cx - 110, cy - 10, 220, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx - 50, cy + 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("minetorio.planner.deconstruction.help"), width / 2, height / 2 - 34, 0xFFB0B0B0);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
