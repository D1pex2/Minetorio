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

    /** Radar's item model, including rotated struts, must stay inside its 16-pixel GUI cell. */
    @Test
    void radarItemFitsGuiCell() throws IOException {
        JsonObject model = read(MODELS.resolve("item/radar.json"));
        JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
        JsonArray rotation = gui.getAsJsonArray("rotation"), scale = gui.getAsJsonArray("scale"), translation = gui.getAsJsonArray("translation");
        double yaw = Math.toRadians(rotation.get(1).getAsDouble()), pitch = Math.toRadians(rotation.get(0).getAsDouble());
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject e = element.getAsJsonObject();
            for (int corner = 0; corner < 8; corner++) {
                double[] v = new double[3];
                for (int axis = 0; axis < 3; axis++)
                    v[axis] = e.getAsJsonArray((corner & (1 << axis)) == 0 ? "from" : "to").get(axis).getAsDouble();
                if (e.has("rotation")) {
                    JsonObject r = e.getAsJsonObject("rotation");
                    JsonArray o = r.getAsJsonArray("origin");
                    int axis = switch (r.get("axis").getAsString()) { case "x" -> 0; case "y" -> 1; default -> 2; };
                    int a = (axis + 1) % 3, b = (axis + 2) % 3;
                    double c = Math.cos(Math.toRadians(r.get("angle").getAsDouble()));
                    double sn = Math.sin(Math.toRadians(r.get("angle").getAsDouble()));
                    double va = v[a] - o.get(a).getAsDouble(), vb = v[b] - o.get(b).getAsDouble();
                    v[a] = o.get(a).getAsDouble() + c * va - sn * vb;
                    v[b] = o.get(b).getAsDouble() + sn * va + c * vb;
                }
                double x = v[0] - 8, y = v[1] - 8, z = v[2] - 8;
                double px = Math.cos(yaw) * x + Math.sin(yaw) * z;
                double pz = -Math.sin(yaw) * x + Math.cos(yaw) * z;
                double py = Math.cos(pitch) * y - Math.sin(pitch) * pz;
                assertTrue(Math.abs(px * scale.get(0).getAsDouble() + translation.get(0).getAsDouble()) <= 8,
                        "radar extends beyond horizontal GUI cell");
                assertTrue(Math.abs(py * scale.get(1).getAsDouble() + translation.get(1).getAsDouble()) <= 8,
                        "radar extends beyond vertical GUI cell");
            }
        }
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
