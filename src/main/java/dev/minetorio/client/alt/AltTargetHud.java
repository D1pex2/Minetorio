package dev.minetorio.client.alt;

import dev.minetorio.Minetorio;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.alt.AltSource;
import dev.minetorio.content.machine.MachineItems;
import dev.minetorio.content.multiblock.MultiblockPartBlock;
import dev.minetorio.content.research.ClientResearch;
import dev.minetorio.content.research.LabBlockEntity;
import dev.minetorio.core.alt.AltInfo;
import dev.minetorio.core.alt.AltRules;
import dev.minetorio.core.alt.AltStatus;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.fluid.FluidData;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Readable detail for the machine under the crosshair, using only its synchronized alt state. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class AltTargetHud {
    private AltTargetHud() {}
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "alt_target"), AltTargetHud::render);
    }
    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!AltMode.isOn() || mc.options.hideGui || mc.screen != null || mc.player == null || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || mc.player.getEyePosition().distanceToSqr(hit.getLocation()) > 64) return;
        BlockPos pos = hit.getBlockPos();
        BlockPos controller = MultiblockPartBlock.controllerPos(mc.level, pos);
        if (controller != null) pos = controller;
        var be = mc.level.getBlockEntity(pos);
        if (!(be instanceof AltSource source) || be.isRemoved()) return;
        AltInfo info = source.altInfo();
        String icon = info.icon();
        if (be instanceof LabBlockEntity && info.status() != AltStatus.NO_RESEARCH)
            icon = AltRules.labIcon(ClientResearch.state().current(), Phase1Data.recipes());
        int w = Math.min(196, g.guiWidth() / 2 - 8);
        if (w < 90 || g.guiHeight() < 145) return;
        int x = 4, y = 52;
        FactoryTheme.panel(g, x, y, w, 46);
        String title = mc.font.plainSubstrByWidth(be.getBlockState().getBlock().getName().getString(), w - 16);
        g.drawString(mc.font, title, x + 8, y + 5, FactoryTheme.TEXT, false);
        Component name;
        if (icon == null) name = Component.translatable(info.status() == AltStatus.NO_RECIPE ? "minetorio.alt.target.no_recipe" : "minetorio.alt.target.no_icon");
        else if (icon.startsWith("fluid:")) {
            String fluid = icon.substring(6);
            g.fill(x + 8, y + 19, x + 24, y + 35, 0xFF000000 | FluidData.color(fluid));
            name = Component.translatable("minetorio.fluid." + fluid);
        } else {
            var stack = MachineItems.stack(icon, 1);
            if (!stack.isEmpty()) g.renderItem(stack, x + 8, y + 19);
            name = stack.isEmpty() ? Component.translatable("minetorio.alt.target.no_icon") : stack.getHoverName();
        }
        g.drawString(mc.font, mc.font.plainSubstrByWidth(name.getString(), w - 38), x + 30, y + 19, FactoryTheme.TEXT, false);
        Component status = Component.translatable("minetorio.alt.status." + info.status().name().toLowerCase(Locale.ROOT));
        int color = info.status().severity() == AltStatus.Severity.ERROR ? 0xFFFF6B5E : info.status() == AltStatus.NONE ? 0xFFB9C4AE : 0xFFFFD267;
        g.drawString(mc.font, mc.font.plainSubstrByWidth(status.getString(), w - 38), x + 30, y + 31, color, false);
    }
}
