package dev.minetorio.core.inserter;

public interface DropTarget {
    boolean wants(String item);

    /** Inserts one item; false if refused (nothing inserted). */
    boolean accept(String item);
}
