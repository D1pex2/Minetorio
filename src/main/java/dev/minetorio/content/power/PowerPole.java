package dev.minetorio.content.power;

import dev.minetorio.core.power.PoleType;
import javax.annotation.Nullable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** A block that is an electric pole of some {@link PoleType} (small pole, multiblock poles). */
public interface PowerPole {
    PoleType poleType();

    /** The pole type of {@code block}, or null if it is no pole. */
    @Nullable
    static PoleType typeOf(Block block) {
        return block instanceof PowerPole p ? p.poleType() : null;
    }

    /** The pole type placed by {@code item}, or null if it places no pole. */
    @Nullable
    static PoleType typeOf(Item item) {
        return item instanceof BlockItem bi ? typeOf(bi.getBlock()) : null;
    }
}
