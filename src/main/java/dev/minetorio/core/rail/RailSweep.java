package dev.minetorio.core.rail;

import java.util.*;
import java.util.function.DoubleFunction;
import static dev.minetorio.core.rail.RailGeometry.*;

/** 3D-адаптация: тела вдоль ленты и тонкие сцепки. Временные интервалы проверяются с верхней границей перемещения. */
public final class RailSweep {
    private RailSweep() {}
    public interface Body {
        int cars();
        double travel();
        Vec point(int car,double longitudinal,double time);
    }
    public record Segment(Vec a,Vec b,double radius,int car) {}
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public boolean intersects(Box other) {return x1>=other.x0&&x0<=other.x1&&y1>other.y0&&y0<other.y1&&z1>=other.z0&&z0<=other.z1;}
    }
    public record Contact(double time,int carA,int carB) {}
    public static Body moving(TrainMotion.Proposal p) {
        return new Body() {
            public int cars(){return p.before().cars().size();}public double travel(){return p.distance();}
            public Vec point(int car,double longitudinal,double time){return p.point(car,longitudinal,time).position();}
        };
    }
    public static Body stationary(RailwayState state,RailwayState.Train train) {
        // Снимок геометрии создается до commit любого поезда этой партии.
        List<List<Vec>> points=new ArrayList<>();
        for(int car=0;car<train.cars().size();car++) {
            List<Vec> row=new ArrayList<>();for(int i=0;i<=28;i++)row.add(TrainMotion.bodyPoint(state,train,car,-3.5+i*.25).position());points.add(List.copyOf(row));
        }
        return new Body() {
            public int cars(){return points.size();}public double travel(){return 0;}
            public Vec point(int car,double longitudinal,double time){double q=(longitudinal+3.5)*4;int i=Math.min(27,(int)q);return mix(points.get(car).get(i),points.get(car).get(i+1),q-i);}
        };
    }
    public static Box bounds(Body body) {
        double x0=Double.POSITIVE_INFINITY,z0=x0,y0=x0,x1=-x0,z1=-x0,y1=-x0;
        for(int car=0;car<body.cars();car++){Vec p=body.point(car,0,0);double r=4.2+body.travel();x0=Math.min(x0,p.x()-r);x1=Math.max(x1,p.x()+r);z0=Math.min(z0,p.z()-r);z1=Math.max(z1,p.z()+r);y0=Math.min(y0,p.y()+.15);y1=Math.max(y1,p.y()+2.65);}
        return new Box(x0,y0,z0,x1,y1,z1);
    }
    public static List<Segment> segments(Body body,double time) {
        List<Segment> result=new ArrayList<>();
        for(int car=0;car<body.cars();car++)for(int i=0;i<28;i++) {
            double q=-3.5+i*.25;double radius=q>=-2.75&&q<2.75?.6:.15;
            result.add(new Segment(body.point(car,q,time),body.point(car,q+.25,time),radius,car));
        }
        return result;
    }
    public static Optional<Contact> pair(Body a,Body b) {
        if(!bounds(a).intersects(bounds(b)))return Optional.empty();
        return search((time,padding)->{
            List<Segment> aa=segments(a,time),bb=segments(b,time);
            for(Segment x:aa)for(Segment y:bb)if(Math.abs(x.a().y()-y.a().y())<2.5) {
                double radius=x.radius()+y.radius()+padding+.002;
                if(Math.abs(x.a().x()-y.a().x())>radius+.6||Math.abs(x.a().z()-y.a().z())>radius+.6)continue;
                if(segmentDistance(x.a(),x.b(),y.a(),y.b())<=radius*radius)return new Contact(time,x.car(),y.car());
            }
            return null;
        },(a.travel()+b.travel())*1.01,0,1);
    }
    /** Постановка допускает только штатное касание совпадающих внешних сцепок; кузова не пересекаются. */
    public static boolean placementOverlap(Body a,Body b) {
        if(!bounds(a).intersects(bounds(b)))return false;
        Set<String> joints=new HashSet<>();
        for(boolean frontA:new boolean[]{false,true})for(boolean frontB:new boolean[]{false,true}) {
            int ca=frontA?0:a.cars()-1,cb=frontB?0:b.cars()-1;double qa=frontA?3.5:-3.5,qb=frontB?3.5:-3.5;
            Vec pa=a.point(ca,qa,0),pb=b.point(cb,qb,0);
            if(Math.abs(pa.y()-pb.y())>1e-8||Math.hypot(pa.x()-pb.x(),pa.z()-pb.z())>1e-8)continue;
            Vec ia=a.point(ca,qa-Math.signum(qa)*.01,0),ib=b.point(cb,qb-Math.signum(qb)*.01,0);
            double ax=pa.x()-ia.x(),az=pa.z()-ia.z(),bx=pb.x()-ib.x(),bz=pb.z()-ib.z();
            if((ax*bx+az*bz)/Math.hypot(ax,az)/Math.hypot(bx,bz)<-.999)joints.add(ca+":"+cb);
        }
        for(Segment x:segments(a,0))for(Segment y:segments(b,0)) {
            if(Math.abs(x.a().y()-y.a().y())>=2.5)continue;
            if(joints.contains(x.car()+":"+y.car())&&(x.radius()==.15||y.radius()==.15))continue;
            double radius=x.radius()+y.radius()+.002;
            if(segmentDistance(x.a(),x.b(),y.a(),y.b())<=radius*radius)return true;
        }
        return false;
    }
    public static Optional<Contact> box(Body body,Box box) {
        if(!bounds(body).intersects(box))return Optional.empty();
        return search((time,padding)->{
            for(Segment segment:segments(body,time)) {
                if(segment.a().y()+2.65<=box.y0()||segment.a().y()+.15>=box.y1())continue;
                double radius=segment.radius()+padding+.002;
                if(segmentBoxDistance(segment.a(),segment.b(),box)<=radius*radius)return new Contact(time,segment.car(),-1);
            }
            return null;
        },body.travel()*1.01,0,1);
    }
    private interface Probe { Contact hit(double time,double padding); }
    private static Optional<Contact> search(Probe probe,double speed,double low,double high) {
        double middle=(low+high)/2;Contact hit=probe.hit(middle,speed*(high-low)/2);
        if(hit==null)return Optional.empty();
        if(speed==0||high-low<1e-5)return Optional.of(new Contact(low,hit.carA(),hit.carB()));
        Optional<Contact> first=search(probe,speed,low,middle);return first.isPresent()?first:search(probe,speed,middle,high);
    }
    private static Vec mix(Vec a,Vec b,double t){return new Vec(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);}
    private static double dot(double x,double z,double xx,double zz){return x*xx+z*zz;}
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
    private static double segmentDistance(Vec a,Vec b,Vec c,Vec d) {
        double ux=b.x()-a.x(),uz=b.z()-a.z(),vx=d.x()-c.x(),vz=d.z()-c.z(),wx=a.x()-c.x(),wz=a.z()-c.z();
        double aa=dot(ux,uz,ux,uz),bb=dot(ux,uz,vx,vz),cc=dot(vx,vz,vx,vz),dd=dot(ux,uz,wx,wz),ee=dot(vx,vz,wx,wz),den=aa*cc-bb*bb;
        double s=den>1e-14?clamp((bb*ee-cc*dd)/den):0;double t=(bb*s+ee)/cc;
        if(t<0){t=0;s=clamp(-dd/aa);}else if(t>1){t=1;s=clamp((bb-dd)/aa);}
        double dx=wx+s*ux-t*vx,dz=wz+s*uz-t*vz;return dx*dx+dz*dz;
    }
    private static double segmentBoxDistance(Vec a,Vec b,Box box) {
        double dx=b.x()-a.x(),dz=b.z()-a.z();List<Double> breaks=new ArrayList<>(List.of(0.0,1.0));
        if(dx!=0)for(double edge:new double[]{box.x0(),box.x1()}){double t=(edge-a.x())/dx;if(t>0&&t<1)breaks.add(t);}
        if(dz!=0)for(double edge:new double[]{box.z0(),box.z1()}){double t=(edge-a.z())/dz;if(t>0&&t<1)breaks.add(t);}
        breaks.sort(Double::compare);double result=Double.POSITIVE_INFINITY;
        for(int i=1;i<breaks.size();i++) {
            double lo=breaks.get(i-1),hi=breaks.get(i),mid=(lo+hi)/2;double x=a.x()+dx*mid,z=a.z()+dz*mid;
            double ax=x<box.x0()||x>box.x1()?dx:0,az=z<box.z0()||z>box.z1()?dz:0;
            double bx=x<box.x0()?a.x()-box.x0():x>box.x1()?a.x()-box.x1():0,bz=z<box.z0()?a.z()-box.z0():z>box.z1()?a.z()-box.z1():0;
            double quadratic=ax*ax+az*az;double t=quadratic==0?mid:Math.max(lo,Math.min(hi,-(ax*bx+az*bz)/quadratic));
            double ex=bx+ax*t,ez=bz+az*t;result=Math.min(result,ex*ex+ez*ez);
        }
        return result;
    }
}
