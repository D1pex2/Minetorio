package dev.minetorio.client;

import dev.minetorio.Minetorio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Optional author-owned resource-pack music; never ships unlicensed Factorio tracks. */
@EventBusSubscriber(modid=Minetorio.MODID,value=Dist.CLIENT)
public final class FactoryMusic {
    private static final ResourceLocation AMBIENT=ResourceLocation.fromNamespaceAndPath(Minetorio.MODID,"music.nauvis");
    private static SimpleSoundInstance current;
    private static int waitTicks=200;
    private FactoryMusic() {}
    @SubscribeEvent public static void filter(PlaySoundEvent e) {
        var sound=e.getOriginalSound();
        if (sound.getSource()==SoundSource.MUSIC && sound.getLocation().getNamespace().equals("minecraft")) e.setSound(null);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null) {
            if(current!=null) mc.getSoundManager().stop(current);
            current=null; waitTicks=200; return;
        }
        if(mc.isPaused() || (current!=null && mc.getSoundManager().isActive(current))) return;
        if(--waitTicks>0) return;
        waitTicks=1200;
        var event=mc.getSoundManager().getSoundEvent(AMBIENT);
        if(event==null || event.getSound(net.minecraft.util.RandomSource.create())==net.minecraft.client.sounds.SoundManager.EMPTY_SOUND) return;
        current=SimpleSoundInstance.forMusic(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(AMBIENT));
        mc.getSoundManager().play(current);
    }
}
