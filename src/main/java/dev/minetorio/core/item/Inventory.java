package dev.minetorio.core.item;

/** Minimal item container abstraction; item ids are strings like "minetorio:iron_plate". */
public interface Inventory {
    int count(String item);

    /** Removes up to n items; returns how many were actually removed. */
    int remove(String item, int n);

    /** Adds up to n items; returns how many were actually added. */
    int add(String item, int n);
}
