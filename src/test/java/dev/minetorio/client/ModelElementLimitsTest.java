package dev.minetorio.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every element of every JSON model under {@code assets/minetorio/models} stays within what
 * Minecraft's model loader accepts: a rotation angle of 0, ±22.5 or ±45 degrees (anything else, e.g.
 * an 11.25° lean, fails to load the model), and {@code from}/{@code to} inside −16..32.
 */
class ModelElementLimitsTest {
    private static final Path MODELS = Paths.get(System.getProperty("minetorio.projectDir", "."),
            "src/main/resources/assets/minetorio/models");
    private static final double[] ANGLES = {0, 22.5, -22.5, 45, -45};

    private static List<Path> models() throws IOException {
        try (Stream<Path> s = Files.walk(MODELS)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject read(Path p) throws IOException {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    static boolean allowedAngle(double a) {
        for (double ok : ANGLES) if (Math.abs(a - ok) < 1e-6) return true;
        return false;
    }

    @Test
    void anglesAreAllowed() {
        assertTrue(allowedAngle(22.5) && allowedAngle(-45) && allowedAngle(0));
        assertFalse(allowedAngle(11.25) || allowedAngle(30) || allowedAngle(90));
    }

    @Test
    void everyElementRotationAndBoundsAreLoadable() throws IOException {
        List<Path> files = models();
        assertFalse(files.isEmpty(), "no models found under " + MODELS.toAbsolutePath());
        List<String> bad = new ArrayList<>();
        for (Path p : files) {
            JsonObject m = read(p);
            if (!m.has("elements")) continue;
            JsonArray els = m.getAsJsonArray("elements");
            for (int i = 0; i < els.size(); i++) {
                JsonObject el = els.get(i).getAsJsonObject();
                String where = MODELS.relativize(p) + " element " + i;
                if (el.has("rotation")) {
                    double angle = el.getAsJsonObject("rotation").get("angle").getAsDouble();
                    if (!allowedAngle(angle)) bad.add(where + ": rotation angle " + angle);
                }
                for (String key : new String[] {"from", "to"}) {
                    for (JsonElement v : el.getAsJsonArray(key)) {
                        double d = v.getAsDouble();
                        if (d < -16 || d > 32) bad.add(where + ": " + key + " " + el.get(key) + " outside -16..32");
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), bad.size() + " unloadable element(s):\n" + String.join("\n", bad));
    }
}
