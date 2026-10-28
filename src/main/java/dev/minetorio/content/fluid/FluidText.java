package dev.minetorio.content.fluid;

import dev.minetorio.core.Units;
import dev.minetorio.core.fluid.FluidNetwork;
import dev.minetorio.core.fluid.FluidStore;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/** Texts about fluids (lang keys {@code minetorio.fluid.*}). */
public final class FluidText {
    private FluidText() {}

    /** The fluid's name ("Water"); "Empty" for null. */
    public static Component name(@Nullable String fluid) {
        return Component.translatable(fluid == null ? "minetorio.fluid.empty" : "minetorio.fluid." + fluid);
    }

    /** Whole units for status lines. */
    public static String units(double amount) {
        return String.valueOf(Math.round(amount));
    }

    /** "Water 1234/2000" of a box or segment. */
    public static Component contents(FluidStore store) {
        return Component.translatable("minetorio.fluid.contents", name(store.fluid()), units(store.amount()), units(store.capacity()));
    }

    /** Hover line of a segment: contents, length and flow per second. */
    public static Component segment(FluidNetwork.Segment s) {
        return Component.translatable("minetorio.fluid.segment", contents(s), s.length(),
                units(s.flow() * Units.TICKS_PER_SECOND), units(s.limitPerTick() * Units.TICKS_PER_SECOND));
    }
}
