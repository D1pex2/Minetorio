package dev.minetorio.gametest;

import com.mojang.authlib.GameProfile;
import dev.minetorio.content.combat.*;
import dev.minetorio.content.character.CharacterInventory;
import dev.minetorio.content.map.RemoteView;
import dev.minetorio.core.combat.CombatRules;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class CombatGameTest {
    private static ServerPlayer player(GameTestHelper h, boolean automatic) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "combat-test"));
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(automatic ? CombatRegistry.SUBMACHINE_GUN.get() : CombatRegistry.PISTOL.get()));
        BlockPos at = h.absolutePos(new BlockPos(8,1,2));
        p.moveTo(at.getX()+.5, at.getY(), at.getZ()+.5);
        return p;
    }
    private static SmallBiterEntity target(GameTestHelper h, ServerPlayer player) {
        var biter = CombatRegistry.SMALL_BITER.get().create(h.getLevel());
        BlockPos at = h.absolutePos(new BlockPos(8,1,8));
        biter.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);
        biter.setNoAi(true);
        biter.setNoGravity(true);
        h.getLevel().addFreshEntity(biter);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, biter.getBoundingBox().getCenter());
        return biter;
    }
    private static void ammo(ServerPlayer p, int count) { CharacterInventory.main(p.getInventory()).setItem(79,new ItemStack(CombatRegistry.FIREARM_MAGAZINE.get(),count)); }
    /** FakePlayer.isInvulnerableTo всегда true (NeoForge FakePlayer.java), поэтому для укуса нужен настоящий ServerPlayer. */
    private static ServerPlayer vulnerablePlayer(GameTestHelper h) {
        GameProfile profile=new GameProfile(UUID.randomUUID(),"combat-mortal");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false));
        p.setGameMode(GameType.SURVIVAL);
        // ServerPlayer.hurt проверяет 60 тиков spawnInvulnerableTime. Игрок не добавлен в мир и сам не тикает.
        for(int i=0;i<61;i++)p.tick();
        BlockPos at=h.absolutePos(new BlockPos(8,1,2));p.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);
        return p;
    }

    @GameTest(template = "empty16")
    public static void combatItemsAndOwnEnemiesAreRegistered(GameTestHelper h) {
        for (String id : java.util.List.of("pistol", "submachine_gun", "firearm_magazine")) {
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("minetorio", id)), "нет предмета " + id);
        }
        for (String id : java.util.List.of("small_biter", "biter_spawner")) {
            h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath("minetorio", id)), "нет сущности " + id);
        }
        h.succeed();
    }

    @GameTest(template="empty16") public static void serverShotUsesExtendedInventoryAndRejectsSpam(GameTestHelper h) {
        ServerPlayer p = player(h,false); ammo(p,2);
        SmallBiterEntity biter = target(h,p);
        h.assertTrue(CombatManager.fire(p) == CombatManager.Result.FIRED,"сервер отказал в выстреле");
        h.assertTrue(Math.abs(biter.getHealth()-.8f)<.0001,"неверный урон " + biter.getHealth());
        h.assertTrue(p.getMainHandItem().getOrDefault(CombatRegistry.ROUNDS.get(),0)==9,"не сохранены 9 патронов");
        h.assertTrue(CharacterInventory.main(p.getInventory()).getItem(79).getCount()==1,"не списан ровно один магазин");
        for(int i=0;i<40;i++) h.assertTrue(CombatManager.fire(p)==CombatManager.Result.COOLDOWN,"повторный выстрел в тот же тик");
        h.assertTrue(Math.abs(biter.getHealth()-.8f)<.0001,"повторный клиентский урон");
        h.succeed();
    }

    @GameTest(template="empty16") public static void fastBulletsBypassVanillaImmunity(GameTestHelper h) {
        ServerPlayer p=player(h,true); ammo(p,1); SmallBiterEntity b=target(h,p);
        CombatManager.fire(p);
        h.runAfterDelay(2,()-> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES,b.getBoundingBox().getCenter());
            h.assertTrue(CombatManager.fire(p)==CombatManager.Result.FIRED,"автомат не стреляет через 2 тика");
            h.assertTrue(b.getHealth() < .42f,"ванильная неуязвимость поглотила вторую пулю: " + b.getHealth());
            h.assertTrue(p.getMainHandItem().getOrDefault(CombatRegistry.ROUNDS.get(),0)==8,"не списан второй патрон");
            h.succeed();
        });
    }

    @GameTest(template="empty16") public static void wallStopsBulletAndStillConsumesRound(GameTestHelper h) {
        ServerPlayer p=player(h,false); ammo(p,1); SmallBiterEntity b=target(h,p);
        for(int y=1;y<4;y++) h.setBlock(new BlockPos(8,y,5),Blocks.STONE);
        CombatManager.fire(p);
        h.assertTrue(Math.abs(b.getHealth()-1.2f)<.0001,"пуля прошла через стену");
        h.assertTrue(p.getMainHandItem().getOrDefault(CombatRegistry.ROUNDS.get(),0)==9,"промах не расходует патрон");
        h.succeed();
    }

    @GameTest(template="empty16") public static void shootingSlowsPlayerUntilWeaponCooldownAndDelaysHealing(GameTestHelper h) {
        ServerPlayer pistol=player(h,false),automatic=player(h,true);ammo(pistol,1);ammo(automatic,1);
        double base=pistol.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        pistol.setHealth(10);CombatManager.fire(pistol);CombatManager.fire(automatic);
        h.assertTrue(Math.abs(pistol.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-base*.8)<.00001,"нет замедления пистолетом");
        h.assertTrue(Math.abs(automatic.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-base*.3)<.00001,"нет замедления автоматом");
        dev.minetorio.content.player.CharacterHealth.tick(pistol);
        h.assertTrue(pistol.getHealth()==10,"выстрел не задержал восстановление HP");
        h.runAfterDelay(5,()-> {
            CombatManager.tick(pistol);CombatManager.tick(automatic);
            h.assertTrue(Math.abs(pistol.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-base)<.00001,"не снято замедление пистолета");
            h.assertTrue(Math.abs(automatic.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-base)<.00001,"не снято замедление автомата");
            h.succeed();
        });
    }

    @GameTest(template="empty16") public static void spawnerPhysicalResistanceIsApplied(GameTestHelper h) {
        ServerPlayer p=player(h,false); ammo(p,1);
        var nest=CombatRegistry.BITER_SPAWNER.get().create(h.getLevel());
        BlockPos at=h.absolutePos(new BlockPos(8,1,9)); nest.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5); nest.setNoAi(true);
        h.getLevel().addFreshEntity(nest);
        p.lookAt(EntityAnchorArgument.Anchor.EYES,nest.getBoundingBox().getCenter());
        CombatManager.fire(p);
        h.assertTrue(Math.abs(nest.getHealth()-27.796f)<.0001,"неверное сопротивление гнезда: " + nest.getHealth());
        h.succeed();
    }

    @GameTest(template="empty16") public static void deadSpectatorCameraAndContainerCannotShoot(GameTestHelper h) {
        ServerPlayer p=player(h,false); ammo(p,1);
        p.setGameMode(GameType.SPECTATOR);
        h.assertTrue(CombatManager.fire(p)==CombatManager.Result.FORBIDDEN,"наблюдатель стреляет");
        p.setGameMode(GameType.SURVIVAL);
        CombatManager.cameraBlocked(p,true);
        h.assertTrue(CombatManager.fire(p)==CombatManager.Result.FORBIDDEN,"камера стреляет");
        CombatManager.cameraBlocked(p,false);
        p.containerMenu=ChestMenu.oneRow(1,p.getInventory());
        h.assertTrue(CombatManager.fire(p)==CombatManager.Result.FORBIDDEN,"открытый контейнер стреляет");
        p.containerMenu=p.inventoryMenu;
        p.setHealth(0);
        h.assertTrue(CombatManager.fire(p)==CombatManager.Result.FORBIDDEN,"мертвый игрок стреляет");
        h.assertTrue(CharacterInventory.main(p.getInventory()).getItem(79).getCount()==1,"запрещенная стрельба расходует магазин");
        h.succeed();
    }

    @GameTest(template="empty16",batch="combat_remote") public static void remoteViewCannotShootEvenWithIntent(GameTestHelper h) {
        ServerPlayer p=player(h,false);ammo(p,1);
        h.assertTrue(RemoteView.enter(p,p.getX(),p.getZ())==RemoteView.Result.OK,"не включена удаленная камера");
        h.assertTrue(CombatManager.fire(p)==CombatManager.Result.FORBIDDEN,"удаленная камера стреляет");
        RemoteView.exit(p);
        h.succeed();
    }

    @GameTest(template="empty16") public static void fullInventoryKeepsAllAmmoAndLoadedGunCannotMerge(GameTestHelper h) {
        ServerPlayer p=player(h,false);var inventory=CharacterInventory.main(p.getInventory());
        for(int i=1;i<80;i++)inventory.setItem(i,new ItemStack(dev.minetorio.content.registry.ModItems.IRON_PLATE.get(),100));
        ammo(p,200);
        h.assertTrue(CombatManager.fire(p)==CombatManager.Result.FIRED,"полный рюкзак блокирует выстрел");
        h.assertTrue(inventory.getItem(79).getCount()==199,"потерян остаток стека магазинов");
        h.assertTrue(p.getMainHandItem().getMaxStackSize()==1,"заряженное оружие можно объединить");
        h.assertTrue(p.getMainHandItem().copy().split(1).getOrDefault(CombatRegistry.ROUNDS.get(),0)==9,"разделение теряет остаток");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(3)).isEmpty(),"боеприпасы выброшены из рюкзака");
        CompoundTag saved=(CompoundTag)p.getMainHandItem().save(h.getLevel().registryAccess());
        ItemStack restored=ItemStack.parseOptional(h.getLevel().registryAccess(),saved);
        h.assertTrue(restored.getOrDefault(CombatRegistry.ROUNDS.get(),0)==9 && restored.getMaxStackSize()==1,"остаток не переживает сохранение");
        h.succeed();
    }

    @GameTest(template="empty16",timeoutTicks=80) public static void oneMagazineFiresExactlyTenTimes(GameTestHelper h) {
        ServerPlayer p=player(h,true);ammo(p,1);
        for(int i=0;i<11;i++) {
            int shot=i;
            h.runAfterDelay(i*2L+1,()-> {
                var result=CombatManager.fire(p);
                h.assertTrue(result==(shot<10?CombatManager.Result.FIRED:CombatManager.Result.NO_AMMO),"выстрел " + shot + ": " + result);
                if(shot==10) { h.assertTrue(!p.getMainHandItem().has(CombatRegistry.ROUNDS.get()),"не снят пустой магазин");h.succeed(); }
            });
        }
    }

    @GameTest(template="empty16") public static void enemyHealthNestAndBiteCooldownSurviveSaving(GameTestHelper h) {
        ServerPlayer p=player(h,false);SmallBiterEntity b=target(h,p);UUID owner=UUID.randomUUID();b.nest(owner);b.setHealth(.7f);
        CompoundTag tag=b.saveWithoutId(new CompoundTag());
        var restored=CombatRegistry.SMALL_BITER.get().create(h.getLevel()); restored.load(tag);
        h.assertTrue(owner.equals(restored.nest())&&Math.abs(restored.getHealth()-.7f)<.0001,"потеря HP или владельца при сохранении");
        var nest=CombatRegistry.BITER_SPAWNER.get().create(h.getLevel());nest.setHealth(12);
        var nestCopy=CombatRegistry.BITER_SPAWNER.get().create(h.getLevel());nestCopy.load(nest.saveWithoutId(new CompoundTag()));
        h.assertTrue(nestCopy.getHealth()==12&&nestCopy.spawnCooldown()==120,"гнездо потеряло HP или таймер");
        h.succeed();
    }

    @GameTest(template="empty16",timeoutTicks=100) public static void biterPursuesAndBitesPlayer(GameTestHelper h) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        ServerPlayer p=vulnerablePlayer(h);var b=target(h,p);b.setNoAi(false);b.setNoGravity(false);b.setTarget(p);
        double start=b.distanceToSqr(p);
        h.succeedWhen(()-> {
            h.assertTrue(b.distanceToSqr(p)<start,"кусака не преследует");
            h.assertTrue(p.getHealth()<20,"кусака не атакует");
            CompoundTag saved=b.saveWithoutId(new CompoundTag());
            long nextBite=saved.getLong("NextBite");
            h.assertTrue(nextBite>h.getLevel().getGameTime()*3,"после укуса не сохранен будущий NextBite");
            var restored=CombatRegistry.SMALL_BITER.get().create(h.getLevel());restored.load(saved);
            CompoundTag savedAgain=restored.saveWithoutId(new CompoundTag());
            h.assertTrue(savedAgain.getLong("NextBite")==nextBite&&nextBite!=0,"NextBite изменился или обнулился после восстановления");
        });
    }

    @GameTest(template="empty16",timeoutTicks=50) public static void biterStraightChaseHasTwelveBlocksPerSecond(GameTestHelper h) {
        for(int x=5;x<=11;x++)for(int z=0;z<48;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        ServerPlayer p=vulnerablePlayer(h);BlockPos at=h.absolutePos(new BlockPos(8,1,40));p.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);
        var b=target(h,p);b.setNoAi(false);b.setNoGravity(false);b.setTarget(p);
        Vec3[] start={Vec3.ZERO};
        h.runAfterDelay(5,()->start[0]=b.position());
        h.runAfterDelay(15,()-> {
            double speed=b.position().subtract(start[0]).horizontalDistance()*2;
            h.assertTrue(Math.abs(speed-12)<.6,"измеренная скорость по ровному камню " + speed + " блоков/с");
            System.out.println("Minetorio combat: скорость прямой погони " + speed + " блоков/с");
            h.succeed();
        });
    }

    @GameTest(template="empty16",timeoutTicks=160) public static void nestSpawnsOwnBiterAndPersistsRoster(GameTestHelper h) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var nest=CombatRegistry.BITER_SPAWNER.get().create(h.getLevel());BlockPos at=h.absolutePos(new BlockPos(8,1,8));
        nest.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);h.getLevel().addFreshEntity(nest);
        h.succeedWhen(()-> {
            h.assertTrue(!nest.ownedUnits().isEmpty(),"гнездо не выпускает кусаку");
            var copy=CombatRegistry.BITER_SPAWNER.get().create(h.getLevel());copy.load(nest.saveWithoutId(new CompoundTag()));
            h.assertTrue(copy.ownedUnits().equals(nest.ownedUnits()),"потерян список потомков");
        });
    }

    @GameTest(template="empty16") public static void deathWhileNestUnloadedDoesNotLeakOwnedLimit(GameTestHelper h) {
        var nest=CombatRegistry.BITER_SPAWNER.get().create(h.getLevel());
        var child=CombatRegistry.SMALL_BITER.get().create(h.getLevel());child.nest(nest.getUUID());
        BlockPos at=h.absolutePos(new BlockPos(8,1,8));nest.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);child.moveTo(at.getX()+7,at.getY(),at.getZ()+.5);
        CompoundTag initial=nest.saveWithoutId(new CompoundTag());
        var units=new net.minecraft.nbt.ListTag();units.add(net.minecraft.nbt.NbtUtils.createUUID(child.getUUID()));initial.put("OwnedUnits",units);
        nest.load(initial);nest.tick();
        CompoundTag saved=nest.saveWithoutId(new CompoundTag());
        // Гнездо отсутствует в индексе уровня, как при выгрузке его чанка.
        h.assertTrue(h.getLevel().getEntity(nest.getUUID())==null,"гнездо неожиданно загружено");
        h.getLevel().addFreshEntity(child);child.hurt(child.damageSources().genericKill(),10000);
        var registry=h.getLevel().registryAccess();
        var roster=NestRoster.load(NestRoster.get(h.getLevel()).save(new CompoundTag(),registry),registry);
        h.assertTrue(roster.units(nest.getUUID(),java.util.Set.of(child.getUUID())).isEmpty(),"сохранение вернуло погибшего потомка в лимит");
        var restored=CombatRegistry.BITER_SPAWNER.get().create(h.getLevel());restored.load(saved);restored.tick();
        h.assertFalse(restored.ownedUnits().contains(child.getUUID()),"погибший выгруженный потомок навсегда занимает лимит");
        h.succeed();
    }
}
