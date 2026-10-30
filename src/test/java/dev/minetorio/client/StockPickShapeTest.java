package dev.minetorio.client;

import dev.minetorio.content.rail.StockPickShape;
import dev.minetorio.core.data.RailData.StockKind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockPickShapeTest {
    private static Vec3 transform(Vec3 origin,float yaw,double x,double y,double z) {
        double a=Math.toRadians(180-yaw);return origin.add(x*Math.cos(a)+z*Math.sin(a),y,-x*Math.sin(a)+z*Math.cos(a));
    }
    @Test void modelBodyPickingIncludesEndsAndExcludesRotatedEnvelopeCorners() {
        var origin=new Vec3(120,70,-40);
        for(var kind:StockKind.values())for(float yaw:new float[]{0,45,90,135,180,225,270,315}) {
            var shape=StockPickShape.at(kind,origin,yaw);var inside=transform(origin,yaw,0,.35,2);
            assertTrue(shape.contains(inside),kind+" "+yaw);assertEquals(0,shape.distanceToSqr(inside),1e-20);assertSame(shape,shape.inflate(0));
            var from=transform(origin,yaw,-4,.35,2);var to=transform(origin,yaw,4,.35,2);assertTrue(shape.clip(from,to).isPresent());
            var a=transform(origin,yaw,1.7,.35,-1);var b=transform(origin,yaw,1.7,.35,1);assertFalse(shape.contains(a));assertTrue(shape.clip(a,b).isEmpty());assertTrue(shape.distanceToSqr(a)>.25);
        }
    }
    @Test void disconnectedSignalResourcesHaveSeparateBlinkStatesWithoutLogicalRedAlias() throws Exception {
        for(String name:new String[]{"rail_signal","rail_chain_signal"}) {
            try(var reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/assets/minetorio/blockstates/"+name+".json")),java.nio.charset.StandardCharsets.UTF_8)) {
                var variants=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("variants");assertEquals(6,variants.size());
                for(int n=0;n<6;n++)assertEquals("minetorio:block/"+name+"_"+n,variants.getAsJsonObject("aspect="+n).get("model").getAsString());
            }
            for(int n:new int[]{4,5})try(var reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/assets/minetorio/models/block/"+name+"_"+n+".json")),java.nio.charset.StandardCharsets.UTF_8)) {
                var model=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();assertFalse(model.getAsJsonArray("elements").isEmpty());assertTrue(model.toString().contains(n==4?"gray_concrete":"white_concrete"));
            }
        }
    }
}
