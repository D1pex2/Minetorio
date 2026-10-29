package dev.minetorio.core.rail;

import java.util.*;
import java.lang.ref.WeakReference;
import dev.minetorio.core.rail.RailwayState.Car;
import static dev.minetorio.core.rail.RailGeometry.*;
import static dev.minetorio.core.rail.RailwayState.*;

/** Брони и физическая занятость принадлежат единственному RailwayState. */
public final class TrainReservations {
    public record Leading(int tapeIndex, Cursor start) {}
    public record Plan(UUID trainId,long graphRevision,long networkRevision,long routeRevision,long consistRevision,
                       long motionRevision,int headIndex,int startTapeIndex,int travelSign,Cursor start,double speed,
                       double brakingBonus,double brakingDistance,List<Traversal> route,List<UUID> blocks,
                       double safeDistance,boolean exitFits) {
        public Plan {
            Objects.requireNonNull(trainId); Objects.requireNonNull(start); route=List.copyOf(route); blocks=List.copyOf(blocks);
            checkSign(travelSign); checkBonus(brakingBonus);
            if(!Double.isFinite(speed)||!Double.isFinite(brakingDistance)||brakingDistance<0
                    ||!Double.isFinite(safeDistance)||safeDistance<0) throw new IllegalArgumentException("Неверный план");
            if(brakingBonus==0) brakingBonus=0;
        }
    }
    /** Сохраняется лишь доказанный тормозной префикс, без права тяги. */
    public record BrakingCorridor(UUID trainId,int travelSign,double brakingBonus,double distance,List<Traversal> route) {
        public BrakingCorridor {
            Objects.requireNonNull(trainId); checkSign(travelSign); checkBonus(brakingBonus);
            if(!Double.isFinite(distance) || distance<0) throw new IllegalArgumentException("Неверная длина тормозного коридора");
            if(brakingBonus==0) brakingBonus=0;
            route=List.copyOf(route);
        }
    }
    private record Section(Traversal traversal,double from,double to) {}
    private record Authority(Plan plan,double remaining,boolean fresh,boolean brakingOnly) {}
    private final Map<UUID,Set<UUID>> occupancy=new HashMap<>();
    private final Map<UUID,Set<UUID>> claims=new HashMap<>();
    private final Map<UUID,Authority> authorities=new HashMap<>();
    // Запись выданных снимков не сериализуется: чужой owner или reload требует нового plan.
    private final Map<UUID,List<WeakReference<Plan>>> issued=new HashMap<>();
    TrainReservations() {}
    public Set<UUID> occupants(UUID block) { return occupancy.getOrDefault(block,Set.of()); }
    public Set<UUID> claimants(UUID block) { return claims.getOrDefault(block,Set.of()); }
    boolean reservedEntrance(RailSignals.Signal signal,RailSignals network) {
        for(Authority authority:authorities.values()) {
            Plan plan=authority.plan(); double q=-plan.start().distance();
            if(authority.remaining()>0 && plan.start().distance()==0 && network.startsAt(signal,plan.start().traversal())) return true;
            for(int i=0;i<plan.route().size();i++) {
                Traversal step=plan.route().get(i);
                if(i>0 && q<authority.remaining() && network.at(plan.route().get(i-1),step).contains(signal)) return true;
                q+=network.lengthOf(step); if(q>=authority.remaining()) break;
            }
        }
        return false;
    }
    public Optional<Plan> acceptedPlan(UUID train) {
        Authority authority=authorities.get(train);
        return authority==null || !authority.fresh()?Optional.empty():Optional.of(authority.plan());
    }
    List<Traversal> continuation(UUID train,int sign) {
        Authority authority=authorities.get(train);
        return authority!=null && authority.plan().travelSign()==sign?authority.plan().route():List.of();
    }
    public double remainingDistance(UUID train) { Authority a=authorities.get(train); return a==null?0:a.remaining(); }
    private static void checkSign(int sign) { if(sign!=1&&sign!=-1) throw new IllegalArgumentException("Направление должно быть +1 или -1"); }
    private static void checkBonus(double bonus) { if(!Double.isFinite(bonus)||bonus<0) throw new IllegalArgumentException("Недопустимый бонус торможения"); }
    private static Train train(RailwayState state,UUID id,int sign,double bonus) {
        checkSign(sign); checkBonus(bonus);
        Train train=Objects.requireNonNull(state.trains().get(id));
        if(train.head().isEmpty() || train.speed()!=0 && Math.signum(train.speed())!=sign) throw new IllegalArgumentException("Неверное направление или отсутствие пути");
        return train;
    }
    public static Leading leading(RailwayState state,UUID id,int travelSign) {
        Train train=train(state,id,travelSign,0); return leading(state,train,travelSign);
    }
    private static Leading leading(RailwayState state,Train train,int sign) {
        double q=state.headCoordinate(train)+(sign>0?3.5:-7*(train.cars().size()-1)-3.5);
        var position=state.locate(train.tape(),q); Cursor cursor=position.cursor();
        if(sign<0) cursor=new Cursor(RailwayState.reverse(cursor.traversal()),state.railLength(cursor.traversal())-cursor.distance());
        return new Leading(position.index(),cursor);
    }
    public static Plan plan(RailwayState state,UUID id,RailRouter.Route route,int travelSign,double brakingBonus) {
        Train train=train(state,id,travelSign,brakingBonus); Leading start=leading(state,train,travelSign);
        validateRoute(state,route,start.start());
        List<Section> sections=sections(state,route.steps(),start.start().distance(),route.length());
        List<Car> cars=train.cars().stream().map(state.cars()::get).toList();
        double braking=TrainMotion.stoppingDistance(train,cars,brakingBonus);
        double needed=Math.max(braking,TrainMotion.lookaheadDistance(train,cars,travelSign,brakingBonus));
        double safe=0; boolean exitFits=true;
        LinkedHashSet<UUID> blocks=new LinkedHashSet<>();
        // Округление только до конца уже необходимого блока, а не до всей дороги.
        for(int i=0;i<sections.size();i++) {
            Section part=sections.get(i);
            if(i>0 && part.from()>=needed && !state.signals().blocks(part.traversal()).equals(state.signals().blocks(sections.get(i-1).traversal()))) break;
            Traversal preceding=i>0?sections.get(i-1).traversal():preceding(train,start,travelSign);
            if(i==0 && preceding!=null && !state.signals().allowed(preceding,part.traversal())) break;
            boolean chain=preceding!=null && state.signals().at(preceding,part.traversal()).stream().anyMatch(s->s.kind()==RailSignals.Kind.CHAIN);
            if(chain) {
                double exit=chainExit(state,sections,i,train.cars().size()*7.0,id);
                if(exit<0) { exitFits=false; break; }
                needed=Math.max(needed,exit);
            }
            Set<UUID> resources=state.signals().blocks(part.traversal());
            if(!free(state,id,resources)) break;
            if(part.to()>part.from()) blocks.addAll(resources);
            safe=part.to();
        }
        Plan result=snapshot(state,train,start,travelSign,brakingBonus,braking,route.steps(),List.copyOf(blocks),safe,exitFits);
        List<WeakReference<Plan>> issued=state.reservations().issued.computeIfAbsent(id,k->new ArrayList<>());
        issued.removeIf(reference->reference.get()==null); issued.add(new WeakReference<>(result));
        return result;
    }
    private static Traversal preceding(Train train,Leading start,int sign) {
        if(start.start().distance()!=0) return null;
        int index=start.tapeIndex()-sign;
        if(index<0 || index>=train.tape().size()) return null;
        Traversal step=train.tape().get(index);
        return sign>0?step:RailwayState.reverse(step);
    }
    private static double chainExit(RailwayState state,List<Section> parts,int chain,double carsLength,UUID id) {
        double exit=-1;
        for(int i=chain;i<parts.size();i++) {
            Section part=parts.get(i);
            if(!free(state,id,state.signals().blocks(part.traversal()))) return -1;
            List<RailSignals.Signal> boundary=i>chain?state.signals().at(parts.get(i-1).traversal(),part.traversal()):List.of();
            if(exit>=0 && !boundary.isEmpty() && part.from()<exit+carsLength) return -1;
            if(exit<0 && boundary.stream().anyMatch(s->s.kind()==RailSignals.Kind.REGULAR)) exit=part.from();
            if(exit>=0 && part.to()>=exit+carsLength) return exit+carsLength;
        }
        return -1;
    }
    private static Plan snapshot(RailwayState state,Train train,Leading start,int sign,double bonus,double braking,
                                 List<Traversal> route,List<UUID> blocks,double safe,boolean fits) {
        return new Plan(train.id(),state.graph().revision(),state.signals().revision(),train.routeRevision(),train.consistRevision(),
                train.motionRevision(),train.headIndex(),start.tapeIndex(),sign,start.start(),train.speed(),bonus,braking,route,blocks,safe,fits);
    }
    private static void validateRoute(RailwayState state,RailRouter.Route route,Cursor start) {
        if(route.graphRevision()!=state.graph().revision() || route.steps().isEmpty() || !route.steps().getFirst().equals(start.traversal()))
            throw new IllegalArgumentException("Маршрут не начинается у ведущей сцепки");
        double max=-start.distance(), beforeLast=0;
        for(int i=0;i<route.steps().size();i++) {
            Traversal step=route.steps().get(i);
            if(i>0 && !state.signals().allowed(route.steps().get(i-1),step)) throw new IllegalArgumentException("Запрещенный переход маршрута");
            beforeLast=max; max+=state.railLength(step);
        }
        if(route.length()>max+1e-9 || route.steps().size()>1 && route.length()<beforeLast)
            throw new IllegalArgumentException("Длина маршрута не соответствует последовательности");
    }
    private static List<Section> sections(RailwayState state,List<Traversal> route,double offset,double distance) {
        List<Section> result=new ArrayList<>(); double q=0;
        for(int i=0;i<route.size();i++) {
            double end=q+state.railLength(route.get(i))-(i==0?offset:0);
            result.add(new Section(route.get(i),q,Math.min(end,distance)));
            q=end; if(q>=distance) break;
        }
        return result;
    }
    private static boolean free(RailwayState state,UUID id,Collection<UUID> blocks) {
        TrainReservations owned=state.reservations();
        return blocks.stream().noneMatch(b->owned.occupants(b).stream().anyMatch(t->!t.equals(id))
                || owned.claimants(b).stream().anyMatch(t->!t.equals(id)));
    }
    public static boolean commit(RailwayState state,UUID id,Plan plan,int travelSign,double brakingBonus) {
        Train train=train(state,id,travelSign,brakingBonus);
        if(!matches(state,train,plan,travelSign,brakingBonus) || !free(state,id,plan.blocks())) return false;
        if(state.reservations().issued.getOrDefault(id,List.of()).stream().noneMatch(reference->reference.get()==plan)) return false;
        TrainReservations owned=state.reservations();
        Authority previous=owned.authorities.get(id);
        if(previous!=null && train.mode()==Mode.AUTOMATIC && train.speed()!=0 && plan.safeDistance()+1e-9<plan.brakingDistance()) return false;
        owned.issued.remove(id);
        owned.authorities.put(id,new Authority(plan,plan.safeDistance(),true,false));
        owned.rebuildClaims(state); state.reservationsChanged(); return true;
    }
    private static boolean matches(RailwayState state,Train train,Plan plan,int sign,double bonus) {
        if(!plan.trainId().equals(train.id()) || plan.graphRevision()!=state.graph().revision() || plan.networkRevision()!=state.signals().revision()
                || plan.routeRevision()!=train.routeRevision() || plan.consistRevision()!=train.consistRevision() || plan.motionRevision()!=train.motionRevision()
                || plan.headIndex()!=train.headIndex() || plan.speed()!=train.speed() || plan.travelSign()!=sign || plan.brakingBonus()!=bonus) return false;
        Leading current=leading(state,train,sign);
        return current.tapeIndex()==plan.startTapeIndex() && current.start().equals(plan.start())
                && plan.brakingDistance()==TrainMotion.stoppingDistance(train,train.cars().stream().map(state.cars()::get).toList(),bonus);
    }
    static boolean permits(RailwayState state,Train train,int sign,double bonus,boolean braking,double distance,double nextSpeed) {
        checkBonus(bonus); if(sign!=1&&sign!=-1) return distance==0 && nextSpeed==train.speed();
        Authority a=state.reservations().authorities.get(train.id());
        if(a==null || (!a.fresh() && !a.brakingOnly()) || !matches(state,train,a.plan(),sign,bonus)
                || a.brakingOnly() && !braking || distance>a.remaining()+1e-9 || distance<0) return false;
        Train next=new Train(train.id(),train.cars(),train.head(),train.headIndex(),train.tape(),nextSpeed,train.mode(),train.status(),train.routeRevision(),train.consistRevision(),train.motionRevision());
        double stop=TrainMotion.stoppingDistance(next,train.cars().stream().map(state.cars()::get).toList(),bonus);
        return distance+stop<=a.remaining()+1e-9;
    }
    static boolean permitsLayout(RailwayState state,Train old,Train next,int sign,double distance,double bonus,boolean braking) {
        if(!permits(state,old,sign,bonus,braking,distance,next.speed())) return false;
        Authority authority=state.reservations().authorities.get(old.id());
        if(authority==null) return distance==0 && old.head().equals(next.head()) && old.tape().equals(next.tape());
        Plan plan=authority.plan(); Leading target=leading(state,next,sign);
        // Сравниваем весь пройденный хвост route occurrence, а не равенство конечного Cursor.
        double left=distance+plan.start().distance(); int index=0;
        while(index<plan.route().size()-1 && left>=state.railLength(plan.route().get(index))) { left-=state.railLength(plan.route().get(index)); index++; }
        if(!target.start().traversal().equals(plan.route().get(index)) || Math.abs(target.start().distance()-left)>1e-8) {
            // Обратный canonical стык остается на прежнем occurrence с distance=L.
            if(!(sign<0 && left==0 && index>0 && target.start().traversal().equals(plan.route().get(index-1))
                    && target.start().distance()==state.railLength(plan.route().get(index-1)))) return false;
            index--;
        }
        int candidateStart=target.tapeIndex()-sign*index;
        if(candidateStart<0 || candidateStart>=next.tape().size()) return false;
        for(int i=0;i<=index;i++) {
            Traversal actual=next.tape().get(candidateStart+sign*i);
            if(sign<0) actual=RailwayState.reverse(actual);
            if(!actual.equals(plan.route().get(i))) return false;
        }
        // Общая история тела позволяет проверить также кратность полного оборота петли.
        int oldStart=plan.startTapeIndex(), shift=candidateStart-oldStart;
        if(next.tape().equals(old.tape()) && shift!=0) return false;
        double oldQ=state.headCoordinate(old), nextQ=state.headCoordinate(next);
        // Смена незанятой ветки может заменить короткий straight длинной curve.
        // Перенос начала координат считается по длинам до общего occurrence, не по числу pieces.
        double added=next.tape().subList(0,candidateStart).stream().mapToDouble(state::railLength).sum()
                -old.tape().subList(0,oldStart).stream().mapToDouble(state::railLength).sum();
        if(Math.abs(nextQ-oldQ-added-sign*distance)>1e-8) return false;
        double low=Math.max(oldQ-7*(old.cars().size()-1)-3.5,nextQ-added-7*(next.cars().size()-1)-3.5);
        double high=Math.min(oldQ+3.5,nextQ-added+3.5), q=0;
        for(int i=0;i<old.tape().size();i++) {
            int j=i+shift; double end=q+state.railLength(old.tape().get(i));
            if(Math.min(end,high)>Math.max(q,low) && (j<0 || j>=next.tape().size() || !old.tape().get(i).equals(next.tape().get(j)))) return false;
            q=end;
        }
        return true;
    }
    static void consumed(RailwayState state,Train previous,Train next,int sign,double distance) {
        TrainReservations owned=state.reservations(); Authority old=owned.authorities.get(previous.id());
        if(old!=null) {
            Plan plan=old.plan(); double left=plan.start().distance()+distance; int index=0;
            while(index<plan.route().size()-1 && left>=state.railLength(plan.route().get(index))) { left-=state.railLength(plan.route().get(index)); index++; }
            Leading start=leading(state,next,sign);
            if(sign<0 && left==0 && index>0 && start.start().traversal().equals(plan.route().get(index-1))) index--;
            double remaining=Math.max(0,old.remaining()-distance);
            List<Traversal> route=plan.route().subList(index,plan.route().size());
            Plan advanced=snapshot(state,next,start,sign,plan.brakingBonus(),TrainMotion.stoppingDistance(next,next.cars().stream().map(state.cars()::get).toList(),plan.brakingBonus()),
                    route,List.copyOf(resources(state,route,start.start().distance(),remaining)),remaining,plan.exitFits());
            if(old.brakingOnly() && next.speed()==0) owned.authorities.remove(next.id());
            else owned.authorities.put(next.id(),new Authority(advanced,remaining,false,old.brakingOnly()));
        }
        refreshOccupancy(state); owned.rebuildClaims(state);
    }
    private static Set<UUID> resources(RailwayState state,List<Traversal> route,double offset,double distance) {
        Set<UUID> blocks=new HashSet<>();
        for(Section section:sections(state,route,offset,distance)) if(section.to()>section.from()) blocks.addAll(state.signals().blocks(section.traversal()));
        return blocks;
    }
    public static void refreshOccupancy(RailwayState state) {
        TrainReservations owned=state.reservations(); Map<UUID,Set<UUID>> next=new HashMap<>();
        for(Train train:state.trains().values()) if(train.head().isPresent()) {
            double high=state.headCoordinate(train)+3.5, low=high-7*train.cars().size(), q=0;
            for(Traversal traversal:train.tape()) {
                double end=q+state.railLength(traversal);
                if(Math.min(end,high)>Math.max(q,low)) for(UUID block:state.signals().blocks(traversal)) next.computeIfAbsent(block,k->new HashSet<>()).add(train.id());
                q=end; if(q>=high) break;
            }
        }
        owned.occupancy.clear(); next.forEach((key,value)->owned.occupancy.put(key,Set.copyOf(value)));
    }
    public static void releaseFuture(RailwayState state,UUID id) {
        TrainReservations owned=state.reservations(); Train train=state.trains().get(id);
        Authority previous=owned.authorities.get(id);
        if(previous!=null && train!=null && train.mode()==Mode.AUTOMATIC && train.speed()!=0)
            owned.authorities.put(id,owned.brakingCorridor(state,train,previous));
        else owned.authorities.remove(id);
        owned.issued.remove(id);
        owned.rebuildClaims(state);
        if(previous!=null) state.reservationsChanged();
    }
    public static List<BrakingCorridor> brakingCorridors(RailwayState state) {
        List<BrakingCorridor> result=new ArrayList<>();
        state.reservations().authorities.forEach((id,authority)-> {
            Train train=state.trains().get(id); Plan plan=authority.plan();
            if(train==null || train.mode()!=Mode.AUTOMATIC || train.speed()==0 || !matches(state,train,plan,plan.travelSign(),plan.brakingBonus())) return;
            double braking=TrainMotion.stoppingDistance(train,train.cars().stream().map(state.cars()::get).toList(),plan.brakingBonus());
            if(authority.remaining()+1e-9<braking) return;
            List<Traversal> route=sections(state,plan.route(),plan.start().distance(),braking).stream().map(Section::traversal).toList();
            result.add(new BrakingCorridor(id,plan.travelSign(),plan.brakingBonus(),braking,route));
        });
        return result.stream().sorted(Comparator.comparing(BrakingCorridor::trainId)).toList();
    }
    /** Граница загрузки проверяет весь набор до восстановления только тормозных прав. */
    public static void restoreBrakingCorridors(RailwayState state,Collection<BrakingCorridor> input) {
        TrainReservations owned=state.reservations();
        if(!owned.authorities.isEmpty()) throw new IllegalStateException("Восстановление поверх работающего владельца");
        Map<UUID,Authority> restored=new HashMap<>();
        for(BrakingCorridor corridor:input) {
            Train train=train(state,corridor.trainId(),corridor.travelSign(),corridor.brakingBonus());
            if(train.mode()!=Mode.AUTOMATIC || train.speed()==0 || restored.containsKey(train.id())) throw new IllegalArgumentException("Неверный состав тормозного коридора");
            Leading start=leading(state,train,corridor.travelSign());
            double braking=TrainMotion.stoppingDistance(train,train.cars().stream().map(state.cars()::get).toList(),corridor.brakingBonus());
            if(corridor.route().isEmpty() || !corridor.route().getFirst().equals(start.start().traversal()) || Math.abs(braking-corridor.distance())>1e-9)
                throw new IllegalArgumentException("Тормозной коридор не соответствует текущему составу");
            double length=-start.start().distance();
            for(int i=0;i<corridor.route().size();i++) {
                Traversal step=corridor.route().get(i);
                // Новый сигнал мог запретить тягу на старом пути; физические переходы все равно обязательны.
                if(i>0 && !state.graph().next(corridor.route().get(i-1)).contains(step)) throw new IllegalArgumentException("Разрыв тормозного коридора");
                length+=state.railLength(step);
            }
            if(length+1e-9<corridor.distance()) throw new IllegalArgumentException("Короткий тормозной коридор");
            Plan plan=snapshot(state,train,start,corridor.travelSign(),corridor.brakingBonus(),braking,corridor.route(),
                    List.copyOf(resources(state,corridor.route(),start.start().distance(),corridor.distance())),corridor.distance(),false);
            restored.put(train.id(),new Authority(plan,corridor.distance(),false,true));
        }
        owned.issued.clear();owned.authorities.putAll(restored);refreshOccupancy(state);owned.rebuildClaims(state);
    }
    private void rebuildClaims(RailwayState state) {
        claims.clear(); Map<UUID,Set<UUID>> next=new HashMap<>();
        authorities.forEach((id,a)->resources(state,a.plan().route(),a.plan().start().distance(),a.remaining()).forEach(b->next.computeIfAbsent(b,k->new HashSet<>()).add(id)));
        next.forEach((key,value)->claims.put(key,Set.copyOf(value)));
    }
    void networkChanged(RailwayState state,boolean railsChanged) {
        issued.clear();
        if(railsChanged) authorities.clear();
        else {
            authorities.entrySet().removeIf(e->state.trains().get(e.getKey()).speed()==0);
            authorities.replaceAll((id,a)->brakingCorridor(state,state.trains().get(id),a));
        }
        refreshOccupancy(state); rebuildClaims(state);
    }
    private Authority brakingCorridor(RailwayState state,Train train,Authority authority) {
        Plan previous=authority.plan(); Leading start=leading(state,train,previous.travelSign());
        double braking=TrainMotion.stoppingDistance(train,train.cars().stream().map(state.cars()::get).toList(),previous.brakingBonus());
        double remaining=Math.min(authority.remaining(),braking);
        Plan remapped=snapshot(state,train,start,previous.travelSign(),previous.brakingBonus(),braking,
                previous.route(),List.copyOf(resources(state,previous.route(),start.start().distance(),remaining)),remaining,previous.exitFits());
        return new Authority(remapped,remaining,false,true);
    }
    void consistsChanged(RailwayState state,Set<UUID> ids) { ids.forEach(issued::remove); ids.forEach(authorities::remove); refreshOccupancy(state); rebuildClaims(state); }
}
