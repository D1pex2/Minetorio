package dev.minetorio.content.registry;

import dev.minetorio.Minetorio;
import dev.minetorio.content.handcraft.HandcraftAttachment;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data attached to game objects. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Minetorio.MODID);

    /**
     * A player's hand-crafting queue. Not copied on death: the queue is cancelled when the player dies
     * (its items drop with the inventory); copied (as every serialized attachment) when the player
     * is recreated otherwise, e.g. on leaving the End.
     */
    public static final Supplier<AttachmentType<HandcraftAttachment>> HANDCRAFT = TYPES.register("handcraft",
            () -> AttachmentType.builder(() -> new HandcraftAttachment()).serialize(HandcraftAttachment.SERIALIZER).build());

    private ModAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
