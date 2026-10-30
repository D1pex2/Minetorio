package dev.minetorio.content.rail;

import java.util.*;
import dev.minetorio.core.combat.CombatRules;
import dev.minetorio.core.data.RailData;
import dev.minetorio.core.rail.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Итоговые траектории разрешаются до owner commit; изменение исхода заново проверяет соседние sweeps. */
public final class RailWorldCollision {
    private RailWorldCollision() {}
    private record Event(UUID attacker,UUID targetCar,LivingEntity targetEntity,double time,UUID hittingCar,boolean solid) {
        String key(){return attacker+":"+(solid?"solid":targetCar!=null?targetCar:targetEntity.getUUID());}
    }
    private record Damage(UUID attacker,UUID car,double amount) {}
    private record Decision(double fraction,double endSpeed,double contactSpeed) {
        static Decision stopped(){return new Decision(0,0,0);}
    }
    private record Body(RailSweep.Body sweep,List<UUID> cars) {}
    private static RailSweep.Box box(AABB b){return new RailSweep.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static AABB box(RailSweep.Box b){return new AABB(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1());}
    private static double energy(RailData.StockKind kind){return kind==RailData.StockKind.FLUID?6:5;}

    private static Map<UUID,Body> bodies(RailwayState state,Map<UUID,TrainMotion.Proposal> moves,Map<UUID,Decision> decisions,Map<UUID,Double> hp) {
        Map<UUID,Body> result=new LinkedHashMap<>();
        for(var train:state.trains().values().stream().sorted(Comparator.comparing(RailwayState.Train::id)).toList()) {
            if(TrainMotion.carPoses(state,train.id()).size()!=train.cars().size())continue;
            var proposal=moves.get(train.id());boolean valid=proposal!=null&&proposal.validFor(state);
            RailSweep.Body original=valid?RailSweep.moving(proposal):RailSweep.stationary(state,train);
            double fraction=valid?decisions.get(train.id()).fraction():0;
            List<Integer> indices=new ArrayList<>();for(int i=0;i<train.cars().size();i++)if(hp.getOrDefault(train.cars().get(i),state.cars().get(train.cars().get(i)).hp())>0)indices.add(i);
            if(indices.isEmpty())continue;
            List<UUID> cars=indices.stream().map(train.cars()::get).toList();
            RailSweep.Body effective=new RailSweep.Body() {
                public int cars(){return indices.size();}
                public double travel(){return original.travel()*fraction;}
                public RailGeometry.Vec point(int car,double longitudinal,double time){return original.point(indices.get(car),longitudinal,time*fraction);}
            };
            result.put(train.id(),new Body(effective,cars));
        }
        return result;
    }
    private static List<Event> contacts(ServerLevel level,RailwayState state,Map<UUID,TrainMotion.Proposal> moves,Map<UUID,Decision> decisions,Map<UUID,Double> hp) {
        var bodies=bodies(state,moves,decisions,hp);List<Event> events=new ArrayList<>();List<UUID> ids=new ArrayList<>(bodies.keySet());
        for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++) {
            UUID a=ids.get(i),b=ids.get(j);Body ba=bodies.get(a),bb=bodies.get(b);
            double da=ba.sweep().travel(),db=bb.sweep().travel();if(da==0&&db==0)continue;
            var contact=RailSweep.pair(ba.sweep(),bb.sweep());if(contact.isEmpty())continue;
            double sa=da>0?decisions.get(a).contactSpeed():0,sb=db>0?decisions.get(b).contactSpeed():0;
            boolean first=sa>=sb;var c=contact.get();
            events.add(new Event(first?a:b,first?bb.cars().get(c.carB()):ba.cars().get(c.carA()),null,c.time(),first?ba.cars().get(c.carA()):bb.cars().get(c.carB()),false));
        }
        for(var entry:bodies.entrySet()) {
            UUID id=entry.getKey();Body body=entry.getValue();if(body.sweep().travel()==0)continue;
            var area=RailSweep.bounds(body.sweep());AABB aabb=box(area);
            for(int x=(int)Math.floor(area.x0());x<=Math.floor(area.x1());x++)for(int z=(int)Math.floor(area.z0());z<=Math.floor(area.z1());z++) {
                var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
                for(int y=(int)Math.floor(area.y0());y<=Math.floor(area.y1());y++) {
                    BlockPos pos=new BlockPos(x,y,z);var block=chunk.getBlockState(pos);if(block.getBlock() instanceof RailCellBlock)continue;
                    for(AABB shape:block.getCollisionShape(level,pos).toAabbs())RailSweep.box(body.sweep(),box(shape.move(pos))).ifPresent(c->events.add(new Event(id,null,null,c.time(),body.cars().get(c.carA()),true)));
                }
            }
            for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,aabb,e->e.isAlive()&&!e.isPassenger()&&!(e instanceof Player p&&(p.isSpectator()||p.isCreative()))&&!e.isInvulnerableTo(level.damageSources().generic())))
                RailSweep.box(body.sweep(),box(entity.getBoundingBox())).ifPresent(c->events.add(new Event(id,null,entity,c.time(),body.cars().get(c.carA()),false)));
        }
        events.sort(Comparator.comparingDouble(Event::time).thenComparing(Event::attacker).thenComparing(e->e.targetCar()!=null?e.targetCar():e.targetEntity()!=null?e.targetEntity().getUUID():new UUID(0,0)));
        return events;
    }
    public static void resolve(ServerLevel level,RailwayState state,List<TrainMotion.Proposal> proposals) {
        Map<UUID,TrainMotion.Proposal> moves=new LinkedHashMap<>();proposals.stream().filter(p->p.validFor(state)).sorted(Comparator.comparing(TrainMotion.Proposal::trainId)).forEach(p->moves.put(p.trainId(),p));
        Map<UUID,Decision> decisions=new HashMap<>();Map<UUID,Double> remainingHp=new HashMap<>();List<Damage> damage=new ArrayList<>();Set<String> seen=new HashSet<>();
        state.cars().forEach((id,car)->remainingHp.put(id,car.hp()));
        for(var proposal:moves.values())decisions.put(proposal.trainId(),new Decision(1,Math.abs(proposal.speed()),Math.abs(proposal.contactSpeed())));
        while(true) {
            // После каждого результата поиск начинается заново по фактически разрешенным траекториям.
            var next=contacts(level,state,moves,decisions,remainingHp).stream().filter(e->!seen.contains(e.key())).findFirst();if(next.isEmpty())break;
            Event event=next.get();seen.add(event.key());var proposal=moves.get(event.attacker());Decision decision=decisions.get(event.attacker());
            if(proposal==null||decision.fraction()==0||!proposal.validFor(state))continue;
            if(event.solid()){decisions.put(event.attacker(),Decision.stopped());continue;}
            double hp=event.targetCar()!=null?remainingHp.get(event.targetCar()):event.targetEntity().getHealth()/CombatRules.HEALTH_SCALE;if(hp<=0)continue;
            double weight=proposal.before().cars().stream().map(state.cars()::get).mapToDouble(c->RailData.stock(c.kind()).weight()).sum();
            double energy=energy(state.cars().get(event.hittingCar()).kind());double raw=(float)(decision.contactSpeed()*weight/energy);boolean survived;
            if(event.targetCar()!=null) {
                double amount=CombatRules.physicalDamage(raw,50,60);remainingHp.put(event.targetCar(),Math.max(0,hp-amount));damage.add(new Damage(event.attacker(),event.targetCar(),amount));survived=hp>amount;
            } else {
                event.targetEntity().hurt(level.damageSources().generic(),CombatRules.health(raw));survived=event.targetEntity().isAlive();
                if(!proposal.validFor(state)){decisions.put(event.attacker(),Decision.stopped());continue;}
            }
            if(survived)decisions.put(event.attacker(),Decision.stopped());
            else {double speed=Math.max(0,decision.contactSpeed()-hp*energy/weight);decisions.put(event.attacker(),new Decision(decision.fraction(),Math.min(decision.endSpeed(),speed),speed));}
        }
        Set<UUID> committed=new HashSet<>();
        for(var proposal:moves.values()){Decision decision=decisions.get(proposal.trainId());if(proposal.commit(decision.fraction(),decision.endSpeed()))committed.add(proposal.trainId());}
        for(Damage effect:damage)if(committed.contains(effect.attacker()))RailwayManager.damageStock(level,effect.car(),effect.amount());
    }
}
