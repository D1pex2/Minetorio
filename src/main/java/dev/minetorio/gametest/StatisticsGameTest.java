package dev.minetorio.gametest;
import dev.minetorio.content.stats.Statistics;
import dev.minetorio.content.stats.StatsPayloads;
import dev.minetorio.core.stats.ProductionLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@GameTestHolder("minetorio") @PrefixGameTestTemplate(false)
public class StatisticsGameTest {
 @GameTest(template="empty16",timeoutTicks=20)
 public static void statisticsPersistAndRejectInvalidRequests(GameTestHelper h) {
  String id="minetorio:statistics_persistence_test";
  Statistics.record(h.getLevel(),id,7,3);
  var original=Statistics.get(h.getLevel().getServer());
  var saved=original.save(new CompoundTag(),h.getLevel().registryAccess());
  var restored=Statistics.load(saved,h.getLevel().registryAccess());
  h.assertTrue(original.ledger().snapshot(0,0).equals(restored.ledger().snapshot(0,0)),"lifetime production/consumption survives save/load");
  h.assertTrue(original.ledger().buckets().equals(restored.ledger().buckets()),"bounded history survives save/load");
  var player=MachineGameTest.player(h,new BlockPos(2,1,2));
  h.assertFalse(StatsPayloads.send(player,new StatsPayloads.Request(-1,false,"")),"negative time window accepted");
  h.assertFalse(StatsPayloads.send(player,new StatsPayloads.Request(4,false,"")),"unknown time window accepted");
  h.succeed();
 }
}
