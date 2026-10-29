package dev.minetorio.core.robot;

/**
 * Blocks marked for upgrade (Factorio's blue arrow): the cell of the block (a machine's
 * controller), the block id that was marked, the item it is to be replaced with and how many of
 * that item the replacement takes (2 for a paired underground belt, which is swapped as a pair).
 * A blueprint's regular belt replacement can carry a direction; planner upgrades preserve it.
 */
public final class UpgradeMarks extends CellMarks<UpgradeMarks.Order> {
    public record Order(String fromBlock, String toItem, int count, int facing) {
        public Order(String fromBlock, String toItem, int count) { this(fromBlock, toItem, count, -1); }
        public Order {
            if (toItem == null || toItem.isEmpty()) throw new IllegalArgumentException("no target");
            if (count < 1) throw new IllegalArgumentException("count " + count);
            if (fromBlock == null) fromBlock = "";
            if (facing < -1 || facing > 3) throw new IllegalArgumentException("facing " + facing);
        }
    }

    /** Marks {@code cell} (block {@code fromBlock}) to be replaced by {@code count} × {@code toItem}. */
    public boolean mark(Cell cell, String fromBlock, String toItem, int count) {
        return mark(cell, fromBlock, toItem, count, -1);
    }

    /** A blueprint may also request a horizontal facing; -1 preserves the old orientation. */
    public boolean mark(Cell cell, String fromBlock, String toItem, int count, int facing) {
        return put(cell, new Order(fromBlock, toItem, count, facing));
    }

    /** Whether the order on {@code cell} still fits the block now there. */
    public boolean stillValid(Cell cell, String currentBlockId) {
        Order o = get(cell);
        return o != null && (o.fromBlock().isEmpty() || o.fromBlock().equals(currentBlockId));
    }
}
