package dev.minetorio.content.rail;

import com.google.gson.*;
import dev.minetorio.core.data.RailData.StockKind;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Общая неизменяемая геометрия выбора из собственных моделей, без клиентских классов. */
public final class StockPickShape {
    private StockPickShape() {}
    public static final double LENGTH_SCALE=2;
    public static float rotation(float yaw){return 180-yaw;}
    private static final Map<StockKind,List<AABB>> SHAPES=load();
    private static Map<StockKind,List<AABB>> load() {
        var result=new EnumMap<StockKind,List<AABB>>(StockKind.class);
        for(StockKind kind:StockKind.values()) {
            String name=switch(kind){case LOCOMOTIVE->"locomotive";case CARGO->"cargo";case FLUID->"fluid";};
            try(var stream=Objects.requireNonNull(StockPickShape.class.getResourceAsStream("/assets/minetorio/models/block/rail_"+name+".json"));
                var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var boxes=new ArrayList<AABB>();
                for(var raw:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements")) {
                    var element=raw.getAsJsonObject();
                    if(element.has("rotation"))throw new IllegalStateException("Модель выбора требует прямоугольных элементов");
                    var from=element.getAsJsonArray("from");var to=element.getAsJsonArray("to");
                    boxes.add(new AABB(from.get(0).getAsDouble()/16-.5,from.get(1).getAsDouble()/16,(from.get(2).getAsDouble()/16-.5)*LENGTH_SCALE,
                            to.get(0).getAsDouble()/16-.5,to.get(1).getAsDouble()/16,(to.get(2).getAsDouble()/16-.5)*LENGTH_SCALE));
                }
                if(boxes.isEmpty())throw new IllegalStateException("Пустая модель выбора");result.put(kind,List.copyOf(boxes));
            } catch(IOException failure){throw new UncheckedIOException(failure);}
        }
        return Map.copyOf(result);
    }
    public static AABB at(StockKind kind,Vec3 position,float yaw){return new Bounds(SHAPES.get(kind),position,Math.toRadians(rotation(yaw)));}
    private static final class Bounds extends AABB {
        private final List<AABB> boxes;private final Vec3 origin;private final double angle,cos,sin;
        private Bounds(List<AABB> boxes,Vec3 origin,double angle){this(boxes,origin,angle,envelope(boxes,origin,angle));}
        private Bounds(List<AABB> boxes,Vec3 origin,double angle,AABB bounds) {
            super(bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ);
            this.boxes=boxes;this.origin=origin;this.angle=angle;cos=Math.cos(angle);sin=Math.sin(angle);
        }
        private static AABB envelope(List<AABB> boxes,Vec3 origin,double angle) {
            double c=Math.cos(angle),s=Math.sin(angle);AABB result=null;
            for(AABB b:boxes)for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY,b.maxY})for(double z:new double[]{b.minZ,b.maxZ}) {
                Vec3 p=origin.add(x*c+z*s,y,-x*s+z*c);AABB point=new AABB(p,p);result=result==null?point:result.minmax(point);
            }
            return result;
        }
        private Vec3 local(Vec3 world){var p=world.subtract(origin);return new Vec3(p.x*cos-p.z*sin,p.y,p.x*sin+p.z*cos);}
        private Vec3 world(Vec3 p){return origin.add(p.x*cos+p.z*sin,p.y,-p.x*sin+p.z*cos);}
        @Override public Optional<Vec3> clip(Vec3 from,Vec3 to) {
            Vec3 a=local(from),b=local(to);return boxes.stream().map(box->box.clip(a,b)).flatMap(Optional::stream)
                    .min(Comparator.comparingDouble(a::distanceToSqr)).map(this::world);
        }
        @Override public boolean contains(Vec3 p){return contains(p.x,p.y,p.z);}
        @Override public boolean contains(double x,double y,double z){Vec3 p=local(new Vec3(x,y,z));return boxes.stream().anyMatch(box->box.contains(p));}
        @Override public double distanceToSqr(Vec3 p){Vec3 local=local(p);return boxes.stream().mapToDouble(box->box.distanceToSqr(local)).min().orElseThrow();}
        @Override public AABB inflate(double x,double y,double z) {
            if(x==0&&y==0&&z==0)return this;
            double dx=Math.abs(cos)*x+Math.abs(sin)*z,dz=Math.abs(sin)*x+Math.abs(cos)*z;
            return new Bounds(boxes.stream().map(box->box.inflate(dx,y,dz)).toList(),origin,angle);
        }
    }
}
