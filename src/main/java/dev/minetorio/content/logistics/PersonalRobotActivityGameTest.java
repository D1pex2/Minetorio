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
        robot.setRemoved(Entity.RemovalReason.DISCARDED);
        h.assertTrue(PersonalRobotActivity.count(level.getServer(),Set.of(owner)).equals(OptionalInt.of(0)),"Прямой terminal setRemoved очищает membership");h.succeed();
    }
}
