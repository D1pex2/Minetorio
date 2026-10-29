package dev.minetorio.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatAssetsTest {
    @Test void militaryHasTheResearchTranslationKeyAndEveryCombatItemHasAssets() throws Exception {
        Path assets=Path.of(System.getProperty("minetorio.projectDir","."),"src/main/resources/assets/minetorio");
        for(String lang:List.of("ru_ru","en_us")) {
            var json=JsonParser.parseString(Files.readString(assets.resolve("lang/"+lang+".json"))).getAsJsonObject();
            assertTrue(json.has("technology.minetorio.military"),"нет названия исследования " + lang);
            for(String id:List.of("pistol","submachine_gun","firearm_magazine")) {
                assertTrue(json.has("item.minetorio."+id),"нет имени " + id);
                assertTrue(Files.isRegularFile(assets.resolve("models/item/"+id+".json")),"нет модели " + id);
            }
        }
    }
}
