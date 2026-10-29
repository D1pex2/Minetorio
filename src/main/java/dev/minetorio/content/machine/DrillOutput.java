package dev.minetorio.content.machine;

import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.inserter.InserterEndpoints;
import dev.minetorio.core.inserter.DropTarget;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * A drill's one-item output buffer. Each mined item (a stack key) is held here and pushed into the
 * cell in front of the drill exactly like an inserter drops ({@link InserterEndpoints#target}: a
 * machine, a belt lane, or a container); while that refuses, the item waits here. The field block was
 * already debited, so the buffer is saved and dropped when the drill is broken.
 */
public final class DrillOutput {
    private static final String KEY_HELD = "held";

    @Nullable
    private String held;

    @Nullable
    public String held() { return held; }

    public boolean isEmpty() { return held == null; }

    /** Puts {@code item} into the (empty) buffer. */
    public void hold(String item) {
        if (held != null) throw new IllegalStateException("drill output buffer already holds " + held);
        held = item;
    }

    /**
     * Tries to deliver the buffered item to {@code pos}; {@code from} is the side of that block the
     * drill faces. Returns true if the item left the buffer.
     */
    public boolean push(Level level, BlockPos pos, Direction from) {
        if (held == null) return false;
        DropTarget target = InserterEndpoints.target(level, pos, from);
        if (target == null || !target.wants(held) || !target.accept(held)) return false;
        held = null;
        return true;
    }

    /** Takes the actual buffered ore for a GUI transfer; the mining debit already happened. */
    @Nullable
    public String take() { String item=held; held=null; return item; }

    /** Drops the buffered item at {@code pos} and empties the buffer (server side). */
    public void drop(Level level, BlockPos pos) {
        if (held == null) return;
        LaneStacks.drop(level, pos, held);
        held = null;
    }

    public void save(CompoundTag tag) {
        if (held != null) tag.putString(KEY_HELD, held);
    }

    public void load(CompoundTag tag) {
        held = tag.contains(KEY_HELD) ? tag.getString(KEY_HELD) : null;
    }
}
