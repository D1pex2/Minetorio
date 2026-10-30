package dev.minetorio.core.rail;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.UUID;
import static dev.minetorio.core.rail.RailGeometry.*;

/** Общая геометрия 3D-адаптера для серверной погрузки и предпросмотра. */
public final class RailDockGeometry {
    public static final double ALIGNMENT_TOLERANCE=.0625;
    private RailDockGeometry() {}

    private static boolean finite(Vec v) {
        return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());
    }
    private static boolean valid(Pose pose) {
        return pose!=null&&finite(pose.position())&&finite(pose.tangent())
                &&Math.abs(pose.tangent().y())<=1e-9
                &&Math.abs(Math.hypot(pose.tangent().x(),pose.tangent().z())-1)<=1e-6;
    }
    public static Vec dockPoint(Pose pose,int side,int connector) {
        if(!valid(pose)||Math.abs(side)!=1||connector< -1||connector>1)throw new IllegalArgumentException("Неверный штуцер вагона");
        Vec c=pose.position(),t=pose.tangent();
        return new Vec(c.x()+2*connector*t.x()-side*t.z(),c.y()+.5,c.z()+2*connector*t.z()+side*t.x());
    }
    private static Vec direction(RailDir direction) {
        return switch(direction) {
            case N -> new Vec(0,0,-1);
            case E -> new Vec(1,0,0);
            case S -> new Vec(0,0,1);
            case W -> new Vec(-1,0,0);
            default -> throw new IllegalArgumentException("Порт помпы должен быть горизонтальным cardinal");
        };
    }
    public static Vec pumpPoint(Point cell,RailDir outward) {return pumpPoint(cell,outward,0);}
    public static Vec pumpPoint(Point cell,RailDir outward,int anchorSign) {
        if(cell==null||outward==null||anchorSign< -1||anchorSign>1)throw new IllegalArgumentException("Неверный якорь помпы");
        Vec d=direction(outward);
        return new Vec(cell.x()+.5+.5*d.x()-.5*anchorSign*d.z(),cell.y()+.5,cell.z()+.5+.5*d.z()+.5*anchorSign*d.x());
    }
    public static OptionalInt anchor(Pose pose,int side,int connector,Point cell,RailDir outward) {
        if(!valid(pose)||Math.abs(side)!=1||connector< -1||connector>1||cell==null||outward==null||(outward.ordinal()&1)!=0)return OptionalInt.empty();
        Vec t=pose.tangent(),d=direction(outward);
        // Направление должно смотреть к данному борту. Допуск угла кузова задает отдельная eligibility.
        if(side*(-t.z()*d.x()+t.x()*d.z())>=0)return OptionalInt.empty();
        Vec dock=dockPoint(pose,side,connector);
        for(int sign:new int[]{0,-1,1}) {
            Vec pump=pumpPoint(cell,outward,sign);
            double x=dock.x()-pump.x(),y=dock.y()-pump.y(),z=dock.z()-pump.z();
            if(x*x+y*y+z*z<=ALIGNMENT_TOLERANCE*ALIGNMENT_TOLERANCE)return OptionalInt.of(sign);
        }
        return OptionalInt.empty();
    }
    public static boolean matches(Pose pose,int side,int connector,Point cell,RailDir outward) {
        return anchor(pose,side,connector,cell,outward).isPresent();
    }

    /** Локальная система штуцера на реальном вхождении рельса, без запрета кривой под другим концом. */
    public static Optional<Pose> connectorFrame(RailwayState state,UUID trainId,UUID carId,int connector) {
        if(connector< -1||connector>1)return Optional.empty();
        var train=state.trains().get(trainId);var car=state.cars().get(carId);
        if(train==null||car==null||train.head().isEmpty())return Optional.empty();
        int index=train.cars().indexOf(carId);if(index<0)return Optional.empty();
        double q=state.headCoordinate(train)-7*index+2*connector*(car.reversed()?-1:1);
        if(q<0||q>state.tapeLength(train.tape()))return Optional.empty();
        var at=state.locate(train.tape(),q);var piece=state.graph().piece(at.cursor().traversal().piece()).orElse(null);
        if(piece==null||piece.shape()!=Shape.STRAIGHT)return Optional.empty();
        return Optional.of(state.pose(train.tape(),q,car.reversed()));
    }

    /** Q - центр целевой клетки; from - направление от нее к манипулятору. */
    public static OptionalDouble itemContact(Pose pose,Vec q,Vec from) {
        if(!valid(pose)||!finite(q)||!finite(from))return OptionalDouble.empty();
        Vec c=pose.position(),t=pose.tangent();double dx=q.x()-c.x(),dz=q.z()-c.z();
        double[] end={dx*t.x()+dz*t.z(),-dx*t.z()+dz*t.x(),q.y()-c.y()};
        double[] delta={from.x()*t.x()+from.z()*t.z(),-from.x()*t.z()+from.z()*t.x(),from.y()};
        double[] low={-2.75,-.6,.15},high={2.75,.6,2.65};
        double entry=0,exit=1;
        for(int axis=0;axis<3;axis++) {
            if(end[axis]<low[axis]||end[axis]>high[axis])return OptionalDouble.empty();
            double start=end[axis]+delta[axis],velocity=-delta[axis];
            if(Math.abs(velocity)<1e-12) {
                if(start<low[axis]||start>high[axis])return OptionalDouble.empty();
            } else {
                double a=(low[axis]-start)/velocity,b=(high[axis]-start)/velocity;
                entry=Math.max(entry,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));
                if(entry>exit)return OptionalDouble.empty();
            }
        }
        return OptionalDouble.of(entry);
    }
}
