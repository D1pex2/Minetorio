package dev.minetorio.client.character;

import dev.minetorio.content.registry.BaseMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Cosmetic mining equipment: never writes to the player's real inventory or hand. */
public final class EngineerToolVisuals {
    private static final ItemStack TOOL=new ItemStack(BaseMaterials.ENGINEER_TOOL.get());
    private EngineerToolVisuals() {}
    public static ItemStack mainHand(LivingEntity entity,ItemStack actual) {
        if(!actual.isEmpty() || !(entity instanceof AbstractClientPlayer player)
                || player.isSpectator() || player.isInvisible()) return actual;
        Minecraft mc=Minecraft.getInstance();
        boolean mining=player==mc.player && mc.gameMode!=null && mc.gameMode.isDestroying();
        boolean swinging=player.swinging && player.swingingArm==InteractionHand.MAIN_HAND;
        return mining || swinging ? TOOL : actual;
    }
}
