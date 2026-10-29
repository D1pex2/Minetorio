package dev.minetorio.content.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minetorio.core.world.NauvisBiome;
import dev.minetorio.core.world.NauvisParams;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The world generation data (written by tools/gen_worldgen.py) agrees with the Java side: the
 * vertical frame with {@link NauvisParams}, the biome climate ranges with {@link NauvisBiome}, and
 * the preset is in the normal preset tag.
 */
class NauvisDataTest {
    private static final Path DATA = Paths.get(System.getProperty("minetorio.projectDir", "."), "src/main/resources/data");

    private static JsonObject json(String rel) throws IOException {
        try (Reader r = Files.newBufferedReader(DATA.resolve(rel), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test void verticalFrameMatches() throws IOException {
        JsonObject dim = json("minetorio/dimension_type/nauvis.json");
        assertEquals(NauvisParams.MIN_Y, dim.get("min_y").getAsInt());
        assertEquals(NauvisParams.HEIGHT, dim.get("height").getAsInt());
        JsonObject ns = json("minetorio/worldgen/noise_settings/nauvis.json");
        assertEquals(NauvisParams.SEA_LEVEL, ns.get("sea_level").getAsInt());
        assertEquals(NauvisParams.MIN_Y, ns.getAsJsonObject("noise").get("min_y").getAsInt());
        assertEquals(NauvisParams.HEIGHT, ns.getAsJsonObject("noise").get("height").getAsInt());
        assertFalse(ns.get("aquifers_enabled").getAsBoolean());
        assertFalse(ns.get("ore_veins_enabled").getAsBoolean());
    }

    private static float[] range(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    @Test void presetBiomesMatchTheCoreTable() throws IOException {
        JsonObject preset = json("minetorio/worldgen/world_preset/minetorio.json");
        JsonObject dims = preset.getAsJsonObject("dimensions");
        assertEquals(1, dims.size(), "Overworld only");
        JsonObject ow = dims.getAsJsonObject("minecraft:overworld");
        assertEquals("minetorio:nauvis", ow.get("type").getAsString());
        Map<String, JsonObject> params = new HashMap<>();
        for (JsonElement b : ow.getAsJsonObject("generator").getAsJsonObject("biome_source").getAsJsonArray("biomes")) {
            params.put(b.getAsJsonObject().get("biome").getAsString(), b.getAsJsonObject().getAsJsonObject("parameters"));
        }
        assertEquals(NauvisBiome.values().length, params.size());
        for (NauvisBiome b : NauvisBiome.values()) {
            JsonObject p = params.get("minetorio:" + b.id());
            assertNotNull(p, b.id());
            assertArrayEquals(b.temperature(), range(p.get("temperature")), 1e-6f, b.id());
            assertArrayEquals(b.humidity(), range(p.get("humidity")), 1e-6f, b.id());
            assertArrayEquals(b.continentalness(), range(p.get("continentalness")), 1e-6f, b.id());
            assertTrue(Files.isRegularFile(DATA.resolve("minetorio/worldgen/biome/" + b.id() + ".json")), b.id());
        }
        assertTrue(json("minecraft/tags/worldgen/world_preset/normal.json").toString().contains("minetorio:minetorio"));
        assertEquals("minetorio:minetorio", MinetorioPreset.ID);
    }

    @Test void waterBiomesSpawnFishAndLandBiomesHaveCliffs() throws IOException {
        for (NauvisBiome b : NauvisBiome.values()) {
            JsonObject biome = json("minetorio/worldgen/biome/" + b.id() + ".json");
            boolean water = b == NauvisBiome.SHALLOW_WATER || b == NauvisBiome.DEEP_WATER;
            String fish = biome.getAsJsonObject("spawners").getAsJsonArray("water_ambient").toString();
            assertEquals(water, fish.contains("minetorio:fish"), b.id());
            assertTrue(biome.getAsJsonArray("features").get(2).toString().contains("minetorio:cliff_rock"), b.id());
            assertTrue(biome.getAsJsonObject("carvers").isEmpty(), "no caves: " + b.id());
        }
        assertTrue(json("minetorio/tags/entity_type/natural_spawns.json").toString().contains("minetorio:fish"));
        assertTrue(json("minecraft/tags/worldgen/biome/is_overworld.json").toString().contains("minetorio:nauvis_grassland"),
                "ore fields are added to #minecraft:is_overworld");
    }
}
