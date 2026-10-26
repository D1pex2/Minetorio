package dev.minetorio.content.belt;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Factorio-style fast-replace: right-clicking a belt, underground belt or splitter with the same
 * kind of block of another tier swaps it in place, keeping facing and lane contents. Shared
 * bookkeeping: what the held stack would place, paying for it, refunding the old pieces.
 */
public final class FastReplace {
    private FastReplace() {}

    /** The block {@code stack} would place, if it is a block of {@code kind} other than {@code current}. */
    @Nullable
    public static <B extends Block> B target(ItemStack stack, Block current, Class<B> kind, Player player) {
        if (player.isSecondaryUseActive()) return null; // sneak: place against the face as usual
        if (!(stack.getItem() instanceof BlockItem item) || !kind.isInstance(item.getBlock()) || item.getBlock() == current) {
            return null;
        }
        return kind.cast(item.getBlock());
    }

    /** Whether the player can pay {@code pieces} items from {@code stack}. */
    public static boolean canPay(Player player, ItemStack stack, int pieces) {
        return player.getAbilities().instabuild || stack.getCount() >= pieces;
    }

    /**
     * Takes {@code pieces} from the hand and gives back {@code pieces} of {@code old} (both skipped
     * in creative), then plays the place sound of {@code placed}.
     */
    public static void finish(Level level, BlockPos pos, BlockState placed, Player player, ItemStack stack, Block old, int pieces) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(pieces);
            ItemStack back = new ItemStack(old.asItem(), pieces);
            if (!player.getInventory().add(back)) player.drop(back, false);
        }
        SoundType sound = placed.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
    }

    public static void refuse(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
