package dev.minetorio.core.stats;

import java.util.*;

/** Bounded event ledger: ten-second buckets for one hour, plus independent lifetime totals. */
public final class ProductionLedger {
    public static final int BUCKET_TICKS=200, MAX_BUCKETS=360, MAX_KEYS=1024;
    public record Counts(double produced,double consumed) {
        public Counts add(Counts other) {return new Counts(produced+other.produced,consumed+other.consumed);}
    }
    private final Map<String,Counts> totals=new HashMap<>();
    private final NavigableMap<Long,Map<String,Counts>> buckets=new TreeMap<>();
    public void record(long tick,String id,double produced,double consumed) {
        if(id==null || id.isBlank() || id.length()>128 || !Double.isFinite(produced) || !Double.isFinite(consumed) || produced<0 || consumed<0 || produced+consumed<=0)return;
        if(!totals.containsKey(id) && totals.size()>=MAX_KEYS)return;
        Counts delta=new Counts(produced,consumed);totals.merge(id,delta,Counts::add);
        long bucket=Math.floorDiv(Math.max(0,tick),BUCKET_TICKS);
        buckets.computeIfAbsent(bucket,k->new HashMap<>()).merge(id,delta,Counts::add);
        prune(bucket);
    }
    private void prune(long now) {buckets.headMap(now-MAX_BUCKETS+1,false).clear();while(buckets.size()>MAX_BUCKETS)buckets.pollFirstEntry();}
    public Map<String,Counts> snapshot(long tick,int windowTicks) {
        if(windowTicks<=0)return Map.copyOf(totals);
        prune(Math.floorDiv(Math.max(0,tick),BUCKET_TICKS));
        long first=Math.floorDiv(Math.max(0,tick-windowTicks+1),BUCKET_TICKS);
        Map<String,Counts> result=new HashMap<>();buckets.tailMap(first,true).values().forEach(bucket->bucket.forEach((id,c)->result.merge(id,c,Counts::add)));
        return Map.copyOf(result);
    }
    /** Sixty equal-width samples for one resource; time windows remain aligned to saved game ticks. */
    public double[] series(long tick,int windowTicks,String id,boolean produced) {
        double[] values=new double[60];int window=windowTicks<=0?72000:windowTicks;long first=Math.max(0,tick-window+1);
        for(var entry:buckets.entrySet()) {
            long bucketTick=entry.getKey()*BUCKET_TICKS;if(bucketTick<first-BUCKET_TICKS || bucketTick>tick)continue;
            int index=(int)Math.clamp((bucketTick-first)*60L/Math.max(1,window),0,59);
            Counts count=entry.getValue().get(id);if(count!=null)values[index]+=produced?count.produced:count.consumed;
        }
        return values;
    }
    public int bucketCount(){return buckets.size();}
    public Map<String,Counts> totals(){return Map.copyOf(totals);}
    public NavigableMap<Long,Map<String,Counts>> buckets(){var result=new TreeMap<Long,Map<String,Counts>>();buckets.forEach((t,m)->result.put(t,Map.copyOf(m)));return Collections.unmodifiableNavigableMap(result);}
    public void restore(Map<String,Counts> restoredTotals,Map<Long,Map<String,Counts>> restoredBuckets) {
        totals.clear();buckets.clear();restoredTotals.entrySet().stream().limit(MAX_KEYS).forEach(e->{Counts c=e.getValue();if(valid(c))totals.put(e.getKey(),c);});
        restoredBuckets.entrySet().stream().sorted(Map.Entry.<Long,Map<String,Counts>>comparingByKey().reversed()).limit(MAX_BUCKETS).forEach(e->{var m=new HashMap<String,Counts>();e.getValue().forEach((id,c)->{if(totals.containsKey(id)&&valid(c))m.put(id,c);});buckets.put(e.getKey(),m);});
    }
    private static boolean valid(Counts c){return c!=null && Double.isFinite(c.produced) && Double.isFinite(c.consumed) && c.produced>=0 && c.consumed>=0;}
}
