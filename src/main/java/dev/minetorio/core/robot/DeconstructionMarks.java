package dev.minetorio.core.robot;

/**
 * Blocks marked for deconstruction (Factorio's red X): the cell of the block (a machine's
 * controller) and the id of the block that was marked there, so a mark whose block changed in the
 * meantime can be dropped ({@link #stillValid}). An empty id means "whatever stands there".
 *
 * <p>Construction robots take a deconstruction job for every mark ({@link ConstructionQueue}); the
 * server keeps one instance per level and saves it (content: {@code ConstructionMarks}).
 */
public final class DeconstructionMarks extends CellMarks<String> {
    /** Marks {@code cell}, whatever block stands there; false if it was marked already. */
    public boolean mark(Cell cell) {
        return mark(cell, "");
    }

    /** Marks {@code cell} holding block {@code blockId}; false if it was marked already with that id. */
    public boolean mark(Cell cell, String blockId) {
        return put(cell, blockId == null ? "" : blockId);
    }

    /** Block id the mark was made for ("" for any). */
    public String block(Cell cell) {
        String b = get(cell);
        return b == null ? "" : b;
    }

    /** Whether the mark on {@code cell} still fits the block now there ({@code currentBlockId}). */
    public boolean stillValid(Cell cell, String currentBlockId) {
        String b = get(cell);
        return b != null && (b.isEmpty() || b.equals(currentBlockId));
    }
}
