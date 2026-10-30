package dev.minetorio.content.logistics;
import java.util.*;
import dev.minetorio.content.logistics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public final class PersonalRobotActivityGameTest {
    @GameTest(template="empty16")
    public static void admittedPersonalRobotRemainsActiveUntilTerminalRemoval(GameTestHelper h) {
        UUID owner=UUID.randomUUID();var level=h.getLevel();
        h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(0)),"Успешная startup перепись доказывает ноль старых роботов");
        var robot=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),level);
        var pos=h.absolutePos(new BlockPos(5,2,5));robot.moveTo(pos.getX(),pos.getY(),pos.getZ());
        robot.startPersonalBuild(owner,pos,new ItemStack(Items.STONE));
        h.assertTrue(level.addFreshEntity(robot),"Настоящий admission робота");
        h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(1)),"Принятый робот учитывается до первого entity tick");
        var duplicate=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),level);duplicate.setUUID(robot.getUUID());duplicate.startPersonalBuild(owner,pos,new ItemStack(Items.STONE));
        h.assertTrue(!level.addFreshEntity(duplicate),"Повторный UUID отклонен платформой");duplicate.discard();
        h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(1)),"Удаление непринятой копии не стирает владельца");
        var nbt=new net.minecraft.nbt.CompoundTag();h.assertTrue(robot.save(nbt),"Canonical entity NBT записан");
        var loaded=(LogisticRobotEntity)net.minecraft.world.entity.EntityType.loadEntityRecursive(nbt,level,e->e);h.assertTrue(loaded!=null,"NBT создает loaded duplicate");
        level.addLegacyChunkEntities(java.util.stream.Stream.of(loaded));loaded.discard();
        PersonalRobotActivity.admitted(robot);PersonalRobotActivity.admitted(robot);
        h.assertTrue(level.getEntity(robot.getUUID())==robot&&PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(1)),"Rejected loaded duplicate и повтор register не меняют accepted identity/count");
        robot.setRemoved(Entity.RemovalReason.DISCARDED);
        robot.setRemoved(Entity.RemovalReason.DISCARDED);PersonalRobotActivity.removed(robot,Entity.RemovalReason.DISCARDED);
        h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(0)),"Прямой terminal setRemoved очищает membership");h.succeed();
    }
    @GameTest(template="empty16",batch="railway_review")
    public static void hiddenAcceptedTerminalCallbacksClearMembership(GameTestHelper h) {
        var level=h.getLevel();UUID owner=UUID.randomUUID();
        for(var reason:List.of(Entity.RemovalReason.KILLED,Entity.RemovalReason.DISCARDED)) {
            var robot=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),level);var pos=new BlockPos(700000,250,700000);robot.moveTo(pos.getX(),pos.getY(),pos.getZ());robot.startPersonalBuild(owner,pos,new ItemStack(Items.STONE));
            try {
                h.assertTrue(level.addFreshEntity(robot),"Hidden entity accepted manager admission");
                h.assertTrue(level.getEntity(robot.getUUID())==null&&!level.isPositionEntityTicking(pos),"Принятая entity действительно hidden/non-ticking");
                h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(1)),"Hidden accepted учитывается");
                robot.setRemoved(reason);h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(0)),"Actual terminal callback очищает hidden membership: "+reason);
            } finally {robot.discard();}
        }
        h.succeed();
    }
    @GameTest(template="empty16",batch="railway_review")
    public static void crossDimensionAcceptedDuplicateCannotProduceKnownZero(GameTestHelper h) {
        UUID owner=UUID.randomUUID(),id=UUID.randomUUID();var level=h.getLevel();var other=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(other!=null,"Второе фактическое ServerLevel существует");
        var a=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),level);var b=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),other);var pos=h.absolutePos(new BlockPos(5,2,5));
        try {
            a.setUUID(id);a.moveTo(pos.getX(),pos.getY(),pos.getZ());a.startPersonalBuild(owner,pos,new ItemStack(Items.STONE));h.assertTrue(level.addFreshEntity(a),"A accepted в overworld");
            b.setUUID(id);b.moveTo(8,80,8);b.startPersonalBuild(owner,new BlockPos(8,80,8),new ItemStack(Items.STONE));h.assertTrue(other.addFreshEntity(b),"B с тем жеUUID accepted в другом dimension");
            b.setRemoved(Entity.RemovalReason.DISCARDED);
            h.assertTrue(!PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(0)),"F1: terminal B не превращает живой A в known0");h.succeed();
        } finally {a.discard();b.discard();}
    }
    @GameTest(template="empty16",batch="railway_review")
    public static void legitimatePlatformDimensionTransferKeepsOneAcceptedIdentity(GameTestHelper h) {
        var level=h.getLevel();var source=Objects.requireNonNull(level.getServer().getLevel(net.minecraft.world.level.Level.NETHER));UUID owner=UUID.randomUUID();
        var before=new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(),source);var pos=h.absolutePos(new BlockPos(5,2,5));before.moveTo(8,80,8);before.startPersonalBuild(owner,pos,new ItemStack(Items.STONE,3));Entity after=null;
        try {
            h.assertTrue(source.addFreshEntity(before),"Actual source admission");h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(1)),"До transfer count1");
            after=before.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(level,new net.minecraft.world.phys.Vec3(pos.getX(),pos.getY(),pos.getZ()),net.minecraft.world.phys.Vec3.ZERO,0,0,net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
            h.assertTrue(after instanceof LogisticRobotEntity&&after!=before&&before.isRemoved()&&after.getUUID().equals(before.getUUID()),"Настоящий platform transfer создал copy после удаления source identity");var moved=(LogisticRobotEntity)after;
            PersonalRobotActivity.admitted(moved);h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(1))&&moved.carried().getCount()==3,"Legal transfer/repeated callback сохраняет known1/carry");
            moved.discard();h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(0)),"Terminal target после legal transfer дает known0");h.succeed();
        } finally {before.discard();if(after!=null)after.discard();}
    }
}
