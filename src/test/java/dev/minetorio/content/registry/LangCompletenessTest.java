package dev.minetorio.content.registry;

import static org.junit.jupiter.api.Assertions.*;

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
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** en_us and ru_ru define the same keys, and every registered item and block has a name. */
class LangCompletenessTest {
    private static final Path LANG = Paths.get(System.getProperty("minetorio.projectDir", "."),
            "src/main/resources/assets/minetorio/lang");

    private static JsonObject lang(String code) throws IOException {
        try (Reader r = Files.newBufferedReader(LANG.resolve(code + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    @Test
    void englishAndRussianHaveTheSameKeys() throws IOException {
        Set<String> en = new TreeSet<>(lang("en_us").keySet());
        Set<String> ru = new TreeSet<>(lang("ru_ru").keySet());
        Set<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(ru);
        Set<String> onlyRu = new TreeSet<>(ru);
        onlyRu.removeAll(en);
        assertTrue(onlyEn.isEmpty() && onlyRu.isEmpty(), "only in en_us: " + onlyEn + ", only in ru_ru: " + onlyRu);
    }

    @Test
    void idConstantMatchesTheRegister() {
        List<String> registered = new ArrayList<>();
        ModItems.ITEMS.getEntries().forEach(h -> registered.add(h.getId().getPath()));
        assertEquals(new TreeSet<>(registered), new TreeSet<>(ModItems.IDS));
        assertEquals(registered.size(), ModItems.IDS.size(), "duplicate ids in ModItems.IDS");
    }

    @Test
    void everyItemHasAnItemModel() {
        Path models = LANG.resolveSibling("models").resolve("item");
        List<String> missing = new ArrayList<>();
        for (String id : ModItems.IDS) {
            if (!Files.isRegularFile(models.resolve(id + ".json"))) missing.add(id);
        }
        assertTrue(missing.isEmpty(), "no models/item/<id>.json for " + missing);
    }

    @Test
    void everyItemAndBlockHasANameInBothLanguages() throws IOException {
        for (String code : List.of("en_us", "ru_ru")) {
            JsonObject json = lang(code);
            List<String> missing = new ArrayList<>();
            for (String id : ModItems.IDS) {
                if (!json.has("item.minetorio." + id) && !json.has("block.minetorio." + id)) missing.add(id);
            }
            ModBlocks.BLOCKS.getEntries().forEach(h -> {
                String id = h.getId().getPath();
                if (!json.has("block.minetorio." + id)) missing.add("block " + id);
            });
            assertTrue(missing.isEmpty(), code + " lacks names for " + missing);
            for (String key : json.keySet()) {
                assertFalse(json.get(key).getAsString().isBlank(), code + ": empty value for " + key);
            }
        }
    }
}
