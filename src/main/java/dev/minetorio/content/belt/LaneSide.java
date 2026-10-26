package dev.minetorio.content.belt;

/** A belt lane, relative to the direction the items travel. */
public enum LaneSide {
    LEFT, RIGHT;

    public LaneSide opposite() {
        return this == LEFT ? RIGHT : LEFT;
    }
}
