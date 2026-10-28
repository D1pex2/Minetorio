package dev.minetorio.client.blueprint;

import dev.minetorio.content.blueprint.BlueprintImport;
import dev.minetorio.content.blueprint.BlueprintPayloads;
import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.core.blueprint.BlueprintStrings;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of handing the server a blueprint (from the library or a string,
 * {@link BlueprintImport}): the record's deflated bytes in chunks. Also the clipboard: export of a
 * record as a blueprint string and import of one (checked here first for a quick answer; the
 * server checks again).
 */
final class BlueprintTransfer {
    private static int transfer;

    private BlueprintTransfer() {}

    /** Sends deflated record bytes to become an item in inventory slot {@code slot} (-1: any). */
    static void upload(byte[] packed, int slot) {
        int total = Math.max(1, (packed.length + BlueprintImport.CHUNK - 1) / BlueprintImport.CHUNK);
        int id = ++transfer;
        for (int i = 0; i < total; i++) {
            byte[] chunk = Arrays.copyOfRange(packed, i * BlueprintImport.CHUNK, Math.min(packed.length, (i + 1) * BlueprintImport.CHUNK));
            PacketDistributor.sendToServer(new BlueprintPayloads.Upload(id, i, total, slot, chunk));
        }
    }

    /** A copy of {@code r} into slot {@code slot}; an error message, or null when sent. */
    @Nullable
    static Component give(BlueprintRecord r, int slot) {
        byte[] packed = BlueprintStrings.deflate(r.toBytes());
        if (packed.length > BlueprintStrings.MAX_COMPRESSED_BYTES || !r.fitsSize()) {
            return Component.translatable("minetorio.blueprint.import.bad");
        }
        upload(packed, slot);
        return null;
    }

    /** Copies {@code r} to the clipboard as a blueprint string. */
    static Component export(BlueprintRecord r) {
        Minecraft.getInstance().keyboardHandler.setClipboard(r.toBlueprintString());
        return Component.translatable("minetorio.blueprint.string.copied");
    }

    /** The record in the clipboard's blueprint string, or null (with {@code error[0]} set). */
    @Nullable
    static BlueprintRecord fromClipboard(Component[] error) {
        String s = Minecraft.getInstance().keyboardHandler.getClipboard();
        try {
            return BlueprintRecord.fromBlueprintString(s);
        } catch (BlueprintStrings.BadBlueprintString e) {
            error[0] = Component.translatable("minetorio.blueprint.string.bad", e.getMessage());
            return null;
        }
    }

    /** Imports the clipboard's blueprint string as a new item (via the server); a message for the window. */
    static Component importClipboard(int slot) {
        Component[] error = new Component[1];
        BlueprintRecord r = fromClipboard(error);
        if (r == null) return error[0];
        try {
            upload(BlueprintStrings.unwrap(Minecraft.getInstance().keyboardHandler.getClipboard()), slot);
        } catch (BlueprintStrings.BadBlueprintString e) {
            return Component.translatable("minetorio.blueprint.string.bad", e.getMessage());
        }
        return Component.translatable("minetorio.blueprint.string.imported");
    }
}
