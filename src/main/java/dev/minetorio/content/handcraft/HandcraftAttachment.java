package dev.minetorio.content.handcraft;

import com.mojang.logging.LogUtils;
import dev.minetorio.content.nbt.CoreNbt;
import dev.minetorio.content.registry.ModAttachments;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.handcraft.HandcraftQueue;
import dev.minetorio.core.recipe.RecipeBook;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.slf4j.Logger;

/**
 * A player's hand-crafting queue ({@link ModAttachments#HANDCRAFT}). Saved with the player through
 * {@link HandcraftQueue#save()} / {@link HandcraftQueue#load} and {@link CoreNbt}; an empty queue is
 * not written. A saved entry that uses a recipe this version does not know is not loaded: the items
 * it held (inputs taken and outputs not yet delivered) are refunded to the player on the next tick.
 * Also holds the player's request rate window and what was last synced to the client (not saved).
 */
public final class HandcraftAttachment {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String KEY_REFUND = "refund";

    private HandcraftQueue queue;
    /** Items owed to the player from entries dropped on load. */
    private final Map<String, Integer> refund = new LinkedHashMap<>();

    int windowStart = Integer.MIN_VALUE / 2;
    int windowRequests;
    /** Recipe id and times of each entry at the last sync, null before the first. */
    @Nullable
    List<String> syncedShape;
    double syncedProgress;
    /** Front progress at the previous check: a drop means a new step started. */
    double lastProgress;
    int syncedTick;

    public HandcraftAttachment() { this(new HandcraftQueue(Phase1Data.recipes())); }

    private HandcraftAttachment(HandcraftQueue queue) { this.queue = queue; }

    public static HandcraftAttachment of(Player player) { return player.getData(ModAttachments.HANDCRAFT); }

    public HandcraftQueue queue() { return queue; }

    Map<String, Integer> refund() { return refund; }

    public static final IAttachmentSerializer<CompoundTag, HandcraftAttachment> SERIALIZER = new IAttachmentSerializer<>() {
        @Override
        public HandcraftAttachment read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            return load(tag);
        }

        @Nullable
        @Override
        public CompoundTag write(HandcraftAttachment a, HolderLookup.Provider provider) {
            if (a.queue.entries().isEmpty() && a.refund.isEmpty()) return null;
            CompoundTag tag = CoreNbt.write(a.queue.save());
            if (!a.refund.isEmpty()) tag.put(KEY_REFUND, CoreNbt.writeCounts(a.refund));
            return tag;
        }
    };

    @SuppressWarnings("unchecked")
    static HandcraftAttachment load(CompoundTag tag) {
        RecipeBook book = Phase1Data.recipes();
        Map<String, Integer> refund = new LinkedHashMap<>(CoreNbt.readCounts(tag.getCompound(KEY_REFUND)));
        try {
            Map<String, Object> data = CoreNbt.read(tag);
            data.remove(KEY_REFUND);
            if (data.get("entries") instanceof List<?> list) {
                List<Object> kept = new ArrayList<>();
                for (Object o : list) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    boolean known = ((List<Object>) m.get("steps")).stream().allMatch(id -> book.get((String) id).isPresent());
                    if (known) {
                        kept.add(m);
                    } else {
                        for (String k : List.of("removed", "pending")) {
                            if (m.get(k) instanceof Map<?, ?> counts) {
                                counts.forEach((id, n) -> refund.merge((String) id, ((Number) n).intValue(), Integer::sum));
                            }
                        }
                    }
                }
                data.put("entries", kept);
            }
            HandcraftAttachment a = new HandcraftAttachment(HandcraftQueue.load(book, data));
            a.refund.putAll(refund);
            return a;
        } catch (RuntimeException e) {
            LOG.error("Discarding unreadable hand-crafting queue {}", tag, e);
            HandcraftAttachment a = new HandcraftAttachment();
            a.refund.putAll(refund);
            return a;
        }
    }
}
