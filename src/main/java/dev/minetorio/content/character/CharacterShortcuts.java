package dev.minetorio.content.character;

/** Persistent shortcut state; entries are registry item IDs, never item stacks. */
public interface CharacterShortcuts {
    int ROWS = 10;
    int COLUMNS = 10;
    int shortcut(int index);
    void shortcut(int index, int itemId);
    int selectedRow();
    void selectedRow(int row);
    int selectedColumn();
    void selectedColumn(int column);
    boolean emptyHand();
    void emptyHand(boolean empty);
    int visibleRows();
    void visibleRows(int rows);

    /** Visible row at a vertical offset from the active row. */
    default int displayedRow(int offset) {
        return Math.floorMod(selectedRow() - offset, visibleRows());
    }
}
