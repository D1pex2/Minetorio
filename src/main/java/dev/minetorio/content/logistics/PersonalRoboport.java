package dev.minetorio.content.logistics;

import dev.minetorio.content.registry.ModItems;
import dev.minetorio.content.robot.ConstructionManager;
import dev.minetorio.content.robot.Ghosts;
import dev.minetorio.core.logistics.LogisticStats;
import dev.minetorio.core.robot.ConstructionArea;
import dev.minetorio.core.robot.ConstructionQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Factorio's personal roboport without armor: a personal roboport item anywhere in the player's
 * inventory makes the player a mobile roboport for the construction robots in that inventory.
 * Every {@value #INTERVAL} ticks, ghosts within {@value LogisticStats#PERSONAL_CONSTRUCTION_RADIUS}
 * blocks of the player whose item the player carries are handed out in queue order: a robot item and
 * the ghost's item leave the inventory, the robot flies from the player to the ghost, builds it and
 * comes back into the inventory. Up to {@value LogisticStats#PERSONAL_ROBOT_LIMIT} robots per
 * personal roboport item are out at once. Robots need no charging here (Factorio charges them from
 * the armor's energy, which Minetorio does not have).
 */
public final class PersonalRoboport {
    static final int INTERVAL = 10;

    private PersonalRoboport() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % INTERVAL == 0) tick(p);
        });
    }

    /** Personal construction robots of {@code player} now out. */
    static int robotsOut(ServerPlayer player) {
        var complete=PersonalRobotActivity.count(player.server,java.util.Set.of(player.getUUID()));
        if(complete.isPresent())return complete.getAsInt();
        int n = 0;
        for (LogisticRobotEntity r : LogisticsManager.get(player.serverLevel()).robots()) {
            if (!r.isRemoved() && r.kind() == LogisticRobotEntity.Kind.PERSONAL_CONSTRUCTION && player.getUUID().equals(r.homePlayer())) n++;
        }
        return n;
    }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        int roboports = PlayerItems.count(player, LogisticsRegistry.PERSONAL_ROBOPORT.get());
        if (roboports <= 0) return;
        int robots = PlayerItems.count(player, ModItems.CONSTRUCTION_ROBOT.get());
        int free = Math.min(robots, roboports * LogisticStats.PERSONAL_ROBOT_LIMIT - robotsOut(player));
        if (free <= 0) return;
        ServerLevel level = player.serverLevel();
        ConstructionManager m = ConstructionManager.get(level);
        ConstructionArea area = new ConstructionArea(player.getX(), player.getY(), player.getZ(), LogisticStats.PERSONAL_CONSTRUCTION_RADIUS);
        for (ConstructionQueue.Job job : m.queue().pending(ConstructionQueue.Kind.BUILD, area::contains)) {
            if (free <= 0) break;
            BlockPos pos = ConstructionManager.pos(job.cell());
            // only where robots keep ticking (RobotReach): one flying into another chunk would freeze holding the claim
            if (!level.isPositionEntityTicking(pos) || !Ghosts.canBuild(level, pos)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(job.item());
            if (rl == null) continue;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (PlayerItems.count(player, item) <= 0) continue;
            if (launch(level, m, player, job, item)) free--;
        }
    }

    private static boolean launch(ServerLevel level, ConstructionManager m, ServerPlayer player, ConstructionQueue.Job job, Item item) {
        LogisticRobotEntity robot = new LogisticRobotEntity(LogisticsRegistry.LOGISTIC_ROBOT_ENTITY.get(), level);
        Vec3 at = player.position().add(0, player.getBbHeight() + 0.4, 0);
        robot.moveTo(at.x, at.y, at.z, player.getYRot(), 0);
        if (!m.queue().claim(job.cell(), robot.robotId())) return false;
        if (PlayerItems.take(player, ModItems.CONSTRUCTION_ROBOT.get(), 1) != 1) {
            m.queue().release(job.cell(), robot.robotId());
            return false;
        }
        if (PlayerItems.take(player, item, 1) != 1) {
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CONSTRUCTION_ROBOT.get()));
            m.queue().release(job.cell(), robot.robotId());
            return false;
        }
        robot.startPersonalBuild(player.getUUID(), ConstructionManager.pos(job.cell()), new ItemStack(item));
        if (!level.addFreshEntity(robot)) {
            m.queue().release(job.cell(), robot.robotId());
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CONSTRUCTION_ROBOT.get()));
            player.getInventory().placeItemBackInInventory(new ItemStack(item));
            return false;
        }
        LogisticsManager.get(level).track(robot);
        return true;
    }
}
