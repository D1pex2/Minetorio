package dev.minetorio.core.research;
import static org.junit.jupiter.api.Assertions.*;
import dev.minetorio.core.data.Phase1Data;
import dev.minetorio.core.Units;
import java.util.Map;
import org.junit.jupiter.api.Test;
class LabInventoryTest {
 @Test void guiExtractionLeavesCommittedUnitPacksIntactAndAbortReturnsThem() {
  String pack="minetorio:automation_science_pack";
  var research=new ResearchState(Phase1Data.techTree(),Phase1Data.startRecipes());research.setCurrent("automation");
  var lab=new LabLogic(1,Units.kw(60));lab.insertPack(pack,8,false);lab.tick(research,1);
  assertEquals(7,lab.extractPack(pack,64));assertTrue(lab.packs().isEmpty());
  assertEquals(Map.of(pack,1),lab.unitPacks());
  assertEquals(0,lab.extractPack(pack,1));assertEquals(0,lab.extractPack(pack,-1));
  research.setCurrent("logistics");lab.tick(research,0);
  assertEquals(1,lab.unitPacks().getOrDefault(pack,0)+lab.packs().getOrDefault(pack,0));
 }
}
