package dev.minetorio.content.fluid;

import net.minecraft.util.StringRepresentable;

/** One side of a pipe: nothing there, connected (an arm), or closed by the player (a cap). */
public enum PipeSide implements StringRepresentable {
    NONE("none"), PIPE("pipe"), CAP("cap");

    private final String name;

    PipeSide(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
