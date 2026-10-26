package dev.minetorio.core.belt;

public interface LaneSink {
    /** @param overflow how far past the lane end the item has travelled; true means the item was taken. */
    boolean offer(String item, double overflow);
}
