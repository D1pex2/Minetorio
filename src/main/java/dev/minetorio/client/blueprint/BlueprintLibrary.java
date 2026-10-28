package dev.minetorio.client.blueprint;

import com.mojang.logging.LogUtils;
import dev.minetorio.content.blueprint.BlueprintLibraryFile;
import dev.minetorio.content.blueprint.BlueprintRecord;
import dev.minetorio.core.blueprint.BlueprintBooks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * The personal blueprint library (Factorio's): blueprints and books kept on this computer, shared
 * by every world and server, in {@code <game dir>/minetorio/blueprint-library.dat}
 * ({@link BlueprintLibraryFile}). Loaded on first use, saved after every change. A file that
 * cannot be read (or is from a newer version) is kept aside as {@code .unreadable} and not
 * overwritten blindly.
 */
public final class BlueprintLibrary {
    private static final Logger LOG = LogUtils.getLogger();
    @Nullable
    private static List<BlueprintRecord> entries;
    @Nullable
    private static String problem;
    /** The file is from a newer version: nothing is saved over it. */
    private static boolean readOnly;

    private BlueprintLibrary() {}

    public static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("minetorio").resolve("blueprint-library.dat");
    }

    /** The entries (loaded on first call). */
    public static List<BlueprintRecord> entries() {
        if (entries == null) load();
        return entries;
    }

    /** A message about the last load or save, or null. */
    @Nullable
    public static String problem() {
        return problem;
    }

    private static void load() {
        Path f = file();
        try {
            BlueprintLibraryFile.Loaded l = BlueprintLibraryFile.load(f);
            entries = new ArrayList<>(l.entries());
            if (l.dropped() > 0) {
                problem = l.dropped() + " library entries could not be read and were dropped";
                LOG.warn("Blueprint library {}: {}", f, problem);
                backup(f, ".bak");
            }
        } catch (BlueprintLibraryFile.BadLibrary e) {
            LOG.error("Blueprint library {} unreadable: {}", f, e.getMessage());
            problem = e.getMessage();
            readOnly = e.newer;
            if (!e.newer) backup(f, ".unreadable");
            entries = new ArrayList<>();
        }
    }

    private static void backup(Path f, String suffix) {
        try {
            Files.copy(f, f.resolveSibling(f.getFileName() + suffix), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("Could not back up {}", f, e);
        }
    }

    public static boolean readOnly() {
        entries();
        return readOnly;
    }

    private static void save() {
        if (readOnly) return;
        try {
            BlueprintLibraryFile.save(file(), entries());
        } catch (IOException e) {
            problem = "could not save: " + e.getMessage();
            LOG.error("Could not save the blueprint library", e);
        }
    }

    /** Stores a copy of {@code r} at {@code at} (clamped); false when the library is full. */
    public static boolean add(int at, BlueprintRecord r) {
        List<BlueprintRecord> l = entries();
        if (readOnly || l.size() >= BlueprintBooks.MAX_LIBRARY_ENTRIES) return false;
        l.add(Math.max(0, Math.min(l.size(), at)), r);
        save();
        return true;
    }

    public static void remove(int i) {
        List<BlueprintRecord> l = entries();
        if (i < 0 || i >= l.size()) return;
        l.remove(i);
        save();
    }

    public static void move(int from, int to) {
        List<BlueprintRecord> l = entries();
        if (from < 0 || from >= l.size()) return;
        List<BlueprintRecord> m = BlueprintBooks.moved(l, from, Math.min(to, l.size() - 1));
        l.clear();
        l.addAll(m);
        save();
    }

    public static void replace(int i, BlueprintRecord r) {
        List<BlueprintRecord> l = entries();
        if (i < 0 || i >= l.size()) return;
        l.set(i, r);
        save();
    }
}
