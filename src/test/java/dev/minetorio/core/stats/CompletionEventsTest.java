package dev.minetorio.core.stats;
import static org.junit.jupiter.api.Assertions.*;
import dev.minetorio.core.machine.CraftingMachine;
import dev.minetorio.core.data.Phase1Data;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
class CompletionEventsTest {
 @Test void waitingCanceledAndLoadedCraftsDoNotEmitCompletedBatches() {
  var recipe=Phase1Data.recipes().get("iron_gear_wheel").orElseThrow();
  var machine=new CraftingMachine(1,0);var events=new ArrayList<String>();machine.onCraftCompleted(r->events.add(r.id()));machine.setRecipe(recipe);
  for(int i=0;i<100;i++)machine.tick(1);assertTrue(events.isEmpty());
  recipe.inputs().forEach(in->machine.insertInput(in.item(),in.count(),false));machine.tick(1);machine.setRecipe(null);assertTrue(events.isEmpty());
  machine.setRecipe(recipe);recipe.inputs().forEach(in->machine.insertInput(in.item(),in.count(),false));
  for(int i=0;i<recipe.ticks();i++)machine.tick(1);
  assertEquals(java.util.List.of(recipe.id()),events);
  for(int i=0;i<100;i++)machine.tick(1);assertEquals(1,events.size());
 }
}
