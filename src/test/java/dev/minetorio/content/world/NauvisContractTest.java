package dev.minetorio.content.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Distribution-level contract: a new server can load a thin, cave-free Nauvis map. */
class NauvisContractTest {
    private static final Path DATA = Path.of(System.getProperty("minetorio.projectDir", "."), "src/main/resources/data");
    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(DATA.resolve(path))).getAsJsonObject();
    }

    @Test void presetIsThinOverworldWithNoUndergroundSystems() throws Exception {
        JsonObject preset = json("minetorio/worldgen/world_preset/minetorio.json");
        assertEquals(1, preset.getAsJsonObject("dimensions").size());
        JsonObject dim = json("minetorio/dimension_type/nauvis.json");
        assertEquals(-16, dim.get("min_y").getAsInt());
        assertEquals(144, dim.get("height").getAsInt());
        JsonObject noise = json("minetorio/worldgen/noise_settings/nauvis.json");
        assertFalse(noise.get("aquifers_enabled").getAsBoolean());
        assertFalse(noise.get("ore_veins_enabled").getAsBoolean());
        assertTrue(json("minetorio/worldgen/biome/nauvis_grassland.json").getAsJsonObject("carvers").isEmpty());
    }

    @Test void rockResourcesAreModItemsAndStartingCargoContainsFreeplayEquipment() throws Exception {
        String rock = json("minetorio/loot_table/blocks/huge_rock.json").toString();
        assertTrue(rock.contains("minetorio:stone"));
        assertTrue(rock.contains("minetorio:coal"));
        String cargo = json("minetorio/loot_table/chests/crash_site.json").toString();
        assertTrue(cargo.contains("minetorio:burner_mining_drill"));
        assertTrue(cargo.contains("minetorio:stone_furnace"));
    }
}
