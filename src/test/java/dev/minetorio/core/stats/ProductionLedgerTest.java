package dev.minetorio.core.stats;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ProductionLedgerTest {
 @Test void countsActualEventsAcrossWindowsAndPrunesOldBuckets() {
  var ledger=new ProductionLedger();
  ledger.record(0,"minetorio:iron_plate",5,2);
  ledger.record(1400,"minetorio:iron_plate",7,3);
  assertEquals(new ProductionLedger.Counts(7,3),ledger.snapshot(1400,1200).get("minetorio:iron_plate"));
  assertEquals(new ProductionLedger.Counts(12,5),ledger.snapshot(1400,0).get("minetorio:iron_plate"));
  for(int tick=0;tick<100000;tick+=200)ledger.record(tick,"minetorio:iron_plate",1,0);
  assertTrue(ledger.bucketCount()<=ProductionLedger.MAX_BUCKETS);
  assertTrue(ledger.snapshot(100000,0).get("minetorio:iron_plate").produced()>360);
 }
 @Test void rejectsNonFiniteNegativeAndInvalidEvents() {
  var ledger=new ProductionLedger();
  ledger.record(0,"bad",Double.NaN,0);ledger.record(0,"bad",-1,0);ledger.record(0,"",1,0);
  assertTrue(ledger.snapshot(0,0).isEmpty());
 }
}
