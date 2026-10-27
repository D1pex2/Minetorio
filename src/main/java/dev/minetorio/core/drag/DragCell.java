package dev.minetorio.core.drag;

/** One cell of a drag line: its column and the way a belt placed there faces. */
public record DragCell(int x, int z, DragDir facing) {}
