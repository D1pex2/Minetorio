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

/** Все контакты рассчитываются до owner commit. Мир просматривается только через уже загруженные chunks. */
public final class RailWorldCollision {
    private RailWorldCollision() {}
    private record Event(UUID attacker,UUID targetCar,LivingEntity targetEntity,double time,int hittingCar,boolean solid) {}
    private record Damage(UUID attacker,UUID car,LivingEntity entity,double amount) {}
    private record Decision(double fraction,double speed) {}
    private static RailSweep.Box box(AABB b){return new RailSweep.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static AABB box(RailSweep.Box b){return new AABB(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1());}
    private static double energy(RailData.StockKind kind){return kind==RailData.StockKind.FLUID?6:5;}
    public static void resolve(ServerLevel level,RailwayState state,List<TrainMotion.Proposal> proposals) {
        Map<UUID,TrainMotion.Proposal> moves=new LinkedHashMap<>();proposals.stream().filter(p->p.validFor(state)).sorted(Comparator.comparing(TrainMotion.Proposal::trainId)).forEach(p->moves.put(p.trainId(),p));
        Map<UUID,RailSweep.Body> bodies=new LinkedHashMap<>();
        for(var train:state.trains().values().stream().sorted(Comparator.comparing(RailwayState.Train::id)).toList()) {
            if(TrainMotion.carPoses(state,train.id()).size()!=train.cars().size())continue;
            bodies.put(train.id(),moves.containsKey(train.id())?RailSweep.moving(moves.get(train.id())):RailSweep.stationary(state,train));
        }
        List<Event> events=new ArrayList<>();List<UUID> ids=new ArrayList<>(bodies.keySet());
        for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++) {
            UUID a=ids.get(i),b=ids.get(j);var pa=moves.get(a);var pb=moves.get(b);
            double sa=pa==null?0:Math.abs(pa.speed()),sb=pb==null?0:Math.abs(pb.speed());if(sa==0&&sb==0)continue;
            var contact=RailSweep.pair(bodies.get(a),bodies.get(b));if(contact.isEmpty())continue;
            // Направленное событие: первым действует более быстро приближающийся состав, при равенстве UUID.
            boolean first=sa>=sb;UUID attacker=first?a:b,target=first?b:a;var c=contact.get();
            int targetIndex=first?c.carB():c.carA(),hitting=first?c.carA():c.carB();
            events.add(new Event(attacker,state.trains().get(target).cars().get(targetIndex),null,c.time(),hitting,false));
        }
        for(var proposal:moves.values()) {
            if(proposal.speed()==0)continue;RailSweep.Body body=bodies.get(proposal.trainId());if(body==null)continue;
            var area=RailSweep.bounds(body);AABB aabb=box(area);
            // BlockState читается из getChunkNow, отсутствующий chunk не запрашивается и не создается.
            for(int x=(int)Math.floor(area.x0());x<=Math.floor(area.x1());x++)for(int z=(int)Math.floor(area.z0());z<=Math.floor(area.z1());z++) {
                var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
                for(int y=(int)Math.floor(area.y0());y<=Math.floor(area.y1());y++) {
                    BlockPos pos=new BlockPos(x,y,z);var block=chunk.getBlockState(pos);
                    if(block.getBlock() instanceof RailCellBlock)continue;
                    for(AABB shape:block.getCollisionShape(level,pos).toAabbs()) {
                        var contact=RailSweep.box(body,box(shape.move(pos)));
                        contact.ifPresent(c->events.add(new Event(proposal.trainId(),null,null,c.time(),c.carA(),true)));
                    }
                }
            }
            for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,aabb,e->e.isAlive()&&!e.isPassenger()&&!(e instanceof Player p&&(p.isSpectator()||p.isCreative()))&&!e.isInvulnerableTo(level.damageSources().generic()))) {
                var contact=RailSweep.box(body,box(entity.getBoundingBox()));
                contact.ifPresent(c->events.add(new Event(proposal.trainId(),null,entity,c.time(),c.carA(),false)));
            }
        }
        events.sort(Comparator.comparingDouble(Event::time).thenComparing(Event::attacker).thenComparing(e->e.targetCar()!=null?e.targetCar():e.targetEntity()!=null?e.targetEntity().getUUID():new UUID(0,0)));
        Map<UUID,Decision> decisions=new HashMap<>();Map<UUID,Double> remainingHp=new HashMap<>();List<Damage> damage=new ArrayList<>();Set<String> seen=new HashSet<>();
        state.cars().forEach((id,car)->remainingHp.put(id,car.hp()));
        for(var proposal:moves.values())decisions.put(proposal.trainId(),new Decision(1,Math.abs(proposal.speed())));
        for(Event event:events) {
            var proposal=moves.get(event.attacker());Decision decision=decisions.get(event.attacker());if(proposal==null||decision.speed()==0||!proposal.validFor(state))continue;
            String key=event.attacker()+":"+(event.solid()?"solid":event.targetCar()!=null?event.targetCar():event.targetEntity().getUUID());if(!seen.add(key))continue;
            if(event.solid()){decisions.put(event.attacker(),new Decision(0,0));continue;}
            UUID target=event.targetCar()!=null?event.targetCar():event.targetEntity().getUUID();
            double hp=event.targetCar()!=null?remainingHp.get(target):event.targetEntity().getHealth()/CombatRules.HEALTH_SCALE;if(hp<=0)continue;
            double weight=proposal.before().cars().stream().map(state.cars()::get).mapToDouble(c->RailData.stock(c.kind()).weight()).sum();
            var hitting=state.cars().get(proposal.before().cars().get(event.hittingCar()));double energy=energy(hitting.kind());
            // Native 1.1.110 fixture: float32 raw, вес всего состава, E ударившего вагона.
            double raw=(float)(decision.speed()*weight/energy);
            boolean survived;
            if(event.targetCar()!=null) {
                double amount=CombatRules.physicalDamage(raw,50,60);
                remainingHp.put(target,Math.max(0,hp-amount));damage.add(new Damage(event.attacker(),event.targetCar(),null,amount));survived=hp>amount;
            } else {
                // Minecraft armor/events определяют фактическое выживание. Их side effects не откатываются.
                event.targetEntity().hurt(level.damageSources().generic(),CombatRules.health(raw));survived=event.targetEntity().isAlive();
                // Hurt callback мог изменить owner, authority или fuel. При stale попытке дальнейших ударов и оплаты нет.
                if(!proposal.validFor(state)){decisions.put(event.attacker(),new Decision(0,0));continue;}
            }
            if(survived)decisions.put(event.attacker(),new Decision(0,0));
            else decisions.put(event.attacker(),new Decision(decision.fraction(),Math.max(0,decision.speed()-hp*energy/weight)));
        }
        Set<UUID> committed=new HashSet<>();
        for(var proposal:moves.values()) {
            Decision decision=decisions.get(proposal.trainId());
            if(proposal.commit(decision.fraction(),decision.speed()))committed.add(proposal.trainId());
        }
        // Смерть и удаление применяются после motion commit, чтобы не инвалидировать соседний snapshot в партии.
        for(Damage effect:damage)if(committed.contains(effect.attacker())) {
            if(effect.car()!=null)RailwayManager.damageStock(level,effect.car(),effect.amount());
            else if(effect.entity().isAlive())effect.entity().hurt(level.damageSources().generic(),CombatRules.health(effect.amount()));
        }
    }
}
