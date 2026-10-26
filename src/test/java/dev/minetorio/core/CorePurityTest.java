package dev.minetorio.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CorePurityTest {
    @Test
    void coreHasNoMinecraftImports() throws IOException {
        Path root = Paths.get(System.getProperty("minetorio.projectDir", "."), "src/main/java/dev/minetorio/core");
        assertTrue(Files.isDirectory(root), "core dir missing");
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".java"))::iterator) {
                for (String line : Files.readAllLines(p)) {
                    String t = line.stripLeading();
                    assertFalse(t.startsWith("import net.minecraft") || t.startsWith("import net.neoforged"),
                            p + ": " + line);
                }
            }
        }
    }
}
