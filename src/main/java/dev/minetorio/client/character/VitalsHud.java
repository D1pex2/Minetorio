package dev.minetorio.client.character;

import dev.minetorio.Minetorio;
import dev.minetorio.client.gui.FactoryTheme;
import dev.minetorio.content.character.CharacterMenu;
import dev.minetorio.content.character.CharacterShortcuts;
import dev.minetorio.core.character.HudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Real player vitals above the complete shortcut stack; does not change any player attributes. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class VitalsHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "vitals");
    private static ItemStack last = ItemStack.EMPTY;
    private static int nameTicks;
    private VitalsHud() {}

    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, VitalsHud::render);
    }

    @SubscribeEvent public static void hideVanilla(RenderGuiLayerEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.inventoryMenu instanceof CharacterMenu)) return;
        var layer = event.getName();
        if (layer.equals(VanillaGuiLayers.PLAYER_HEALTH) || layer.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || layer.equals(VanillaGuiLayers.AIR_LEVEL) || layer.equals(VanillaGuiLayers.FOOD_LEVEL)
                || layer.equals(VanillaGuiLayers.EXPERIENCE_BAR) || layer.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
                || layer.equals(VanillaGuiLayers.VEHICLE_HEALTH) || layer.equals(VanillaGuiLayers.JUMP_METER)
                || layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { last = ItemStack.EMPTY; nameTicks = 0; return; }
        if (mc.isPaused()) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!ItemStack.isSameItemSameComponents(last, held)) {
            last = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
            nameTicks = held.isEmpty() ? 0 : 40;
        } else if (nameTicks > 0) nameTicks--;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.gameMode == null
                || !(mc.player instanceof CharacterShortcuts state)) return;
        var player = mc.player;
        boolean survival = mc.gameMode.canHurtPlayer();
        HudLayout layout = HudLayout.of(g.guiWidth(), g.guiHeight(), state.visibleRows());
        if (nameTicks > 0 && !player.getMainHandItem().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(layout.left(), layout.nameTop(), 0);
            g.pose().scale(layout.scale(), layout.scale(), 1);
            String name = mc.font.plainSubstrByWidth(player.getMainHandItem().getHoverName().getString(), 176);
            g.drawCenteredString(mc.font, name, 90, 0, FactoryTheme.TEXT);
            g.pose().popPose();
        }
        if (!survival && !(player.getVehicle() instanceof LivingEntity)) return;
        g.pose().pushPose();
        g.pose().translate(layout.left(), layout.vitalsTop(), 0);
        g.pose().scale(layout.scale(), layout.scale(), 1);

        if (survival) {
            float hp = Math.max(0, player.getHealth());
            float maxHp = Math.max(1, player.getMaxHealth());
            float absorption = Math.max(0, player.getAbsorptionAmount());
            FactoryTheme.bar(g, 0, 11, 180, 9, hp / maxHp, FactoryTheme.GREEN);
            if (absorption > 0) {
                int n = (int)Math.round(178 * Math.min(1f, absorption / maxHp));
                if (n > 0) g.fill(1, 18, 1 + n, 19, FactoryTheme.ACCENT);
            }
            String health = HudLayout.factorioHealth(hp) + " / " + HudLayout.factorioHealth(maxHp);
            if (absorption > 0) health += " + " + HudLayout.factorioHealth(absorption);
            g.drawCenteredString(mc.font, health, 90, 11, FactoryTheme.TEXT);

            int armor = player.getArmorValue();
            if (armor > 0) status(g, mc, 0, "A " + armor);
            int air = player.getAirSupply();
            int maxAir = Math.max(1, player.getMaxAirSupply());
            if (air < maxAir || player.isEyeInFluid(FluidTags.WATER)) status(g, mc, 60, "O2 " + Math.max(0, air) + "/" + maxAir);
        }
        if (player.getVehicle() instanceof LivingEntity mount) {
            status(g, mc, 120, "M " + Math.round(Math.max(0, mount.getHealth())) + "/" + Math.round(mount.getMaxHealth()));
        }
        g.pose().popPose();
    }

    private static void status(GuiGraphics g, Minecraft mc, int x, String text) {
        FactoryTheme.inset(g, x, 0, 58, 9);
        String clipped = mc.font.plainSubstrByWidth(text, 54);
        g.drawString(mc.font, clipped, x + 2, 0, FactoryTheme.TEXT, false);
    }
}
