package dev.minetorio.content.mixin;

import dev.minetorio.content.world.MinetorioPreset;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/** A dedicated server's {@code level-type} defaults to the Minetorio world type (server.properties can still name any type). */
@Mixin(DedicatedServerProperties.class)
public abstract class DedicatedServerPropertiesMixin {
    @ModifyArg(method = "<init>",
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=level-type")),
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/server/dedicated/DedicatedServerProperties;get(Ljava/lang/String;Ljava/util/function/Function;Ljava/lang/Object;)Ljava/lang/Object;"),
            index = 2)
    private Object minetorio$defaultLevelType(Object vanillaDefault) {
        return MinetorioPreset.ID;
    }
}
