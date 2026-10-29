package dev.minetorio.client.combat;

import com.mojang.math.Axis;
import dev.minetorio.Minetorio;
import dev.minetorio.client.camera.BuildCamera;
import dev.minetorio.content.combat.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/** Клиент передает только намерение. Эффект появляется после подтвержденного сервером выстрела. */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class CombatClient {
    private static long nextIntent;
    private static boolean pressed;
    private static boolean blocked;
    private static float kick, oldKick;
    private static int flash, hitTicks;
    private CombatClient() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { nextIntent = 0; pressed = false; blocked = false; kick = oldKick = 0; return; }
        boolean cameraBlocked = BuildCamera.isActive() || !mc.options.getCameraType().isFirstPerson();
        if (cameraBlocked != blocked) {
            blocked = cameraBlocked;
            PacketDistributor.sendToServer(new CombatPayloads.Intent(false, blocked));
        }
        if (mc.isPaused()) return;
        oldKick = kick;
        kick *= 0.55f;
        if (flash > 0) flash--;
        if (hitTicks > 0) hitTicks--;
        boolean down = mc.options.keyAttack.isDown();
        if (!(mc.player.getMainHandItem().getItem() instanceof GunItem weapon) || mc.screen != null || cameraBlocked
                || !mc.player.isAlive() || mc.player.isSpectator()) { pressed = down; return; }
        long now = mc.level.getGameTime();
        if (down && (!pressed || weapon.gun == dev.minetorio.core.combat.CombatRules.Gun.SUBMACHINE_GUN) && now >= nextIntent) {
            // Сервер использует последнее принятое положение и направление игрока.
            PacketDistributor.sendToServer(new CombatPayloads.Intent(true, false));
            nextIntent = now + weapon.gun.cooldownTicks;
        }
        pressed = down;
    }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void attack(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof GunItem)) return;
        if (e.isAttack() || e.isUseItem()) { e.setCanceled(true); e.setSwingHand(false); }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || BuildCamera.isActive() || !mc.options.getCameraType().isFirstPerson()) return;
        float recoil = oldKick + (kick - oldKick) * (float)e.getPartialTick();
        e.setPitch(e.getPitch() - recoil * 1.4f);
        e.setRoll(e.getRoll() + recoil * 0.25f);
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e) {
        var player = e.getPlayer();
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        var modifier = speed.getModifier(CombatManager.SLOWDOWN);
        if (modifier == null) return;
        float correction = dev.minetorio.core.combat.CombatRules.fovCorrection(e.getFovModifier(), speed.getValue(),
                player.getAbilities().getWalkingSpeed(), 1 + modifier.amount(), Minecraft.getInstance().options.fovEffectScale().get());
        e.setNewFovModifier(e.getNewFovModifier() + correction);
    }
    @SubscribeEvent public static void hand(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getItemStack().getItem() instanceof GunItem) || BuildCamera.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        e.setCanceled(true);
        var pose = e.getPoseStack();
        pose.pushPose();
        float recoil = oldKick + (kick - oldKick) * e.getPartialTick();
        pose.translate(0.48, -0.42, -0.65 + recoil * 0.07);
        pose.mulPose(Axis.XP.rotationDegrees(-recoil * 9));
        mc.getItemRenderer().renderStatic(e.getItemStack(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, e.getPackedLight(),
                OverlayTexture.NO_OVERLAY, pose, e.getMultiBufferSource(), mc.level, 0);
        pose.popPose();
    }
    @SubscribeEvent public static void hud(RenderGuiLayerEvent.Post e) {
        if (!e.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || BuildCamera.isActive() || mc.options.hideGui
                || !(mc.player.getMainHandItem().getItem() instanceof GunItem) || !mc.options.getCameraType().isFirstPerson()) return;
        var g = e.getGuiGraphics(); int x = g.guiWidth()/2, y = g.guiHeight()/2;
        int gap = 4 + Math.round(kick * 2);
        int color = hitTicks > 0 ? 0xffffa040 : 0xffe5eadb;
        g.fill(x-gap-5, y, x-gap, y+1, color); g.fill(x+gap, y, x+gap+5, y+1, color);
        g.fill(x, y-gap-5, x+1, y-gap, color); g.fill(x, y+gap, x+1, y+gap+5, color);
        if (flash > 0) { g.fill(x+24, y+20, x+32, y+31, 0xeaffd260); g.fill(x+19, y+24, x+37, y+27, 0xcafff2bb); }
        int rounds = mc.player.getMainHandItem().getOrDefault(CombatRegistry.ROUNDS.get(), 0);
        g.drawString(mc.font, Component.translatable("minetorio.combat.loaded", rounds), x+16, y+40, 0xffddd8bf, true);
    }
    public static void shot(CombatPayloads.Shot p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (p.shooter() == mc.player.getId()) { kick = Math.min(2.5f, kick + 1); flash = 2; if (p.hit()) hitTicks = 5; }
        Vec3 from = new Vec3(p.x(), p.y(), p.z());
        Vec3 end = new Vec3(p.hitX(), p.hitY(), p.hitZ());
        Vec3 line = end.subtract(from);
        Vec3 muzzle = from.add(line.normalize().scale(0.7)).add(0,-0.15,0);
        mc.level.addParticle(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 0,0,0);
        for (double d = 0.9; d < line.length(); d += 0.35) {
            Vec3 at = from.add(line.normalize().scale(d));
            mc.level.addParticle(new DustParticleOptions(new org.joml.Vector3f(1,0.8f,0.35f), 0.35f), at.x,at.y,at.z,0,0,0);
        }
        for (int i = 0; i < 7; i++) mc.level.addParticle(p.hit() ? ParticleTypes.DAMAGE_INDICATOR : ParticleTypes.CRIT,
                end.x,end.y,end.z, (mc.level.random.nextDouble()-.5)*.12, .05, (mc.level.random.nextDouble()-.5)*.12);
        mc.level.playLocalSound(p.x(),p.y(),p.z(), p.automatic() ? CombatRegistry.SUBMACHINE_SHOT.get() : CombatRegistry.PISTOL_SHOT.get(),
                SoundSource.PLAYERS, 0.8f, 0.96f + mc.level.random.nextFloat()*.08f, false);
    }
}
