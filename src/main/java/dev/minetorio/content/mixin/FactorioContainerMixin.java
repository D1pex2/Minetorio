package dev.minetorio.content.mixin;

import dev.minetorio.core.item.ItemStackSizes;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A container still respects each item's own limit, including the 200-count circuit/science stacks. */
@Mixin(Container.class)
public interface FactorioContainerMixin {
    @ModifyConstant(method = "getMaxStackSize()I", constant = @Constant(intValue = 99))
    private int minetorio$factorioContainerLimit(int vanillaLimit) {
        return ItemStackSizes.MAX;
    }
}
