package dev.minetorio.content.mixin;

import dev.minetorio.core.item.ItemStackSizes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The engine's save codecs must accept the same 200-item stacks as the actual Factorio items. */
@Mixin({DataComponents.class, ItemStack.class})
public abstract class FactorioStackCodecMixin {
    @ModifyConstant(method = "*", constant = @Constant(intValue = 99))
    private static int minetorio$factorioStackSaveLimit(int vanillaLimit) {
        return ItemStackSizes.MAX;
    }
}
