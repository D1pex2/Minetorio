package dev.minetorio.client.belt;

import dev.minetorio.Minetorio;
import dev.minetorio.content.belt.BeltNode;
import dev.minetorio.content.belt.BeltTicker;
import dev.minetorio.content.belt.UndergroundBeltBlock;
import dev.minetorio.content.belt.UndergroundBeltBlockEntity;
import dev.minetorio.core.belt.BeltTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Factorio-style placement guide: while the player holds an underground belt, tier-coloured dust
 * marks every free cell in front of a nearby unpaired entrance of that tier where an exit would
 * connect (1 .. gap+1 blocks ahead, same facing). Nothing is shown when there is no such entrance.
 */
@EventBusSubscriber(modid = Minetorio.MODID, value = Dist.CLIENT)
public final class UndergroundPlacementHints {
    private static final int INTERVAL = 4;
    private static final double RANGE = 24;

    private UndergroundPlacementHints() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused() || level.getGameTime() % INTERVAL != 0) return;
        UndergroundBeltBlock held = heldUnderground(player);
        if (held == null) return;
        DustParticleOptions dust = new DustParticleOptions(color(held.tier()), 1.0f);
        for (BeltNode node : BeltTicker.of(level).nodes()) {
            if (!(node instanceof UndergroundBeltBlockEntity be) || be.isRemoved() || be.isPaired()) continue;
            if (!be.getBlockState().is(held) || be.type() != UndergroundBeltBlock.Type.INPUT) continue;
            BlockPos origin = be.getBlockPos();
            if (origin.distToCenterSqr(player.position()) > RANGE * RANGE) continue;
            Direction f = be.facing();
            for (int i = 1; i <= held.maxDistance(); i++) {
                BlockPos cell = origin.relative(f, i);
                if (!level.getBlockState(cell).canBeReplaced()) continue;
                level.addParticle(dust, cell.getX() + 0.5, cell.getY() + 0.25, cell.getZ() + 0.5, 0, 0, 0);
            }
        }
    }

    private static UndergroundBeltBlock heldUnderground(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof UndergroundBeltBlock ub) return ub;
        }
        return null;
    }

    private static Vector3f color(BeltTier tier) {
        return switch (tier) {
            case YELLOW -> new Vector3f(0.95f, 0.75f, 0.15f);
            case RED -> new Vector3f(0.9f, 0.2f, 0.15f);
            case BLUE -> new Vector3f(0.25f, 0.5f, 0.95f);
        };
    }
}
