package dev.minetorio.content.blueprint;

import dev.minetorio.core.blueprint.BlueprintBooks;
import dev.minetorio.core.blueprint.BlueprintStrings;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * The blueprint library's file ({@code minetorio/blueprint-library.dat} in the game directory,
 * shared by every world and server): gzipped NBT
 * <pre>{format: "minetorio:blueprint_library", version: 1, entries: [record, ...]}</pre>
 * each entry a {@link BlueprintRecord} as its codec writes it. A newer {@code version} or another
 * {@code format} is refused (the file is left alone); an entry that does not decode or breaks a
 * limit is dropped and counted. Saved through a temporary file and a move, so a crash keeps the
 * old file.
 */
public final class BlueprintLibraryFile {
    public static final String FORMAT = "minetorio:blueprint_library";
    public static final int VERSION = 1;
    /** Largest library file accepted, as NBT counts it. */
    static final long MAX_FILE_BYTES = (long) BlueprintBooks.MAX_LIBRARY_ENTRIES * BlueprintBooks.MAX_RECORD_BYTES;

    private BlueprintLibraryFile() {}

    /** What a load found: the entries and how many were dropped. */
    public record Loaded(List<BlueprintRecord> entries, int dropped) {}

    public static final class BadLibrary extends Exception {
        /** The file is from a newer version of the mod: keep it untouched. */
        public final boolean newer;

        public BadLibrary(String message) {
            this(message, false);
        }

        public BadLibrary(String message, boolean newer) {
            super(message);
            this.newer = newer;
        }
    }

    public static CompoundTag write(List<BlueprintRecord> entries) {
        CompoundTag root = new CompoundTag();
        root.putString("format", FORMAT);
        root.putInt("version", VERSION);
        ListTag list = new ListTag();
        for (BlueprintRecord r : entries) list.add(r.toTag());
        root.put("entries", list);
        return root;
    }

    public static Loaded read(CompoundTag root) throws BadLibrary {
        if (!FORMAT.equals(root.getString("format"))) throw new BadLibrary("not a blueprint library");
        int version = root.getInt("version");
        if (version < 1 || version > VERSION) throw new BadLibrary("unknown library version " + version, version > VERSION);
        ListTag list = root.getList("entries", Tag.TAG_COMPOUND);
        List<BlueprintRecord> out = new ArrayList<>(list.size());
        int dropped = 0;
        for (int i = 0; i < list.size(); i++) {
            if (out.size() >= BlueprintBooks.MAX_LIBRARY_ENTRIES) {
                dropped++;
                continue;
            }
            try {
                out.add(BlueprintRecord.fromTag(list.getCompound(i)));
            } catch (BlueprintStrings.BadBlueprintString e) {
                dropped++;
            }
        }
        return new Loaded(out, dropped);
    }

    /** The library in {@code file}; empty when there is none. */
    public static Loaded load(Path file) throws BadLibrary {
        if (!Files.isRegularFile(file)) return new Loaded(List.of(), 0);
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_FILE_BYTES));
        } catch (IOException | RuntimeException e) {
            throw new BadLibrary("unreadable: " + e.getMessage());
        }
        return read(root);
    }

    public static void save(Path file, List<BlueprintRecord> entries) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(write(entries), tmp);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
