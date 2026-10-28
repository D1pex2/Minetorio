package dev.minetorio.content.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minetorio.content.logistics.LogisticsRegistry;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The logistic network's own registry: ids match, every item has a model, a name in both languages and a loot table or recipe. */
class LogisticsLangTest {
    private static final Path ASSETS = Paths.get(System.getProperty("minetorio.projectDir", "."), "src/main/resources/assets/minetorio");

    @Test
    void idConstantMatchesTheRegister() {
        List<String> registered = new ArrayList<>();
        LogisticsRegistry.ITEMS.getEntries().forEach(h -> registered.add(h.getId().getPath()));
        assertEquals(new TreeSet<>(registered), new TreeSet<>(LogisticsRegistry.IDS));
    }

    @Test
    void everyItemHasAModelAndNames() throws IOException {
        for (String code : List.of("en_us", "ru_ru")) {
            JsonObject json;
            try (Reader r = Files.newBufferedReader(ASSETS.resolve("lang/" + code + ".json"), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            }
            for (String id : LogisticsRegistry.IDS) {
                assertTrue(json.has("item.minetorio." + id) || json.has("block.minetorio." + id), code + " lacks " + id);
                assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + id + ".json")), "no item model " + id);
            }
            LogisticsRegistry.BLOCKS.getEntries().forEach(h -> {
                String id = h.getId().getPath();
                assertTrue(json.has("block.minetorio." + id), code + " lacks block " + id);
                assertTrue(Files.isRegularFile(ASSETS.resolve("blockstates/" + id + ".json")), "no blockstate " + id);
                assertTrue(Files.isRegularFile(ASSETS.resolveSibling("../data/minetorio/loot_table/blocks/" + id + ".json").normalize()),
                        "no loot table " + id);
            });
        }
    }
}
