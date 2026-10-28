package dev.minetorio.gametest;

import static dev.minetorio.gametest.MachineGameTest.player;

import com.mojang.authlib.GameProfile;
import dev.minetorio.Minetorio;
import dev.minetorio.content.player.CharacterCorpseEntity;
import dev.minetorio.content.player.CharacterHealth;
import dev.minetorio.content.player.FishCatching;
import dev.minetorio.content.player.MinetorioGameRules;
import dev.minetorio.content.player.PlayerBasics;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.player.CharacterRules;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Factorio player: no vanilla recipes, mobs, portals or hunger; regeneration, fish, corpse, day length. */
@GameTestHolder(Minetorio.MODID)
@PrefixGameTestTemplate(false)
public class PlayerBasicsGameTest {
    private static final BlockPos AT = new BlockPos(3, 2, 3);

    private static void command(MinecraftServer server, String cmd) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    private static boolean logToPlanks(ServerLevel level) {
        return level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(new ItemStack(Items.OAK_LOG))), level).isPresent();
    }

    private static boolean smeltRawIron(ServerLevel level) {
        return level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(Items.RAW_IRON)), level).isPresent();
    }

    /** No vanilla recipe is left (crafting, smelting, any type); the game rule brings them back and takes them away again. */
    @GameTest(template = "empty16", batch = "vanilla_recipes")
    public static void noVanillaRecipes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        h.assertFalse(level.getGameRules().getBoolean(MinetorioGameRules.VANILLA_RECIPES), "rule default on");
        for (RecipeHolder<?> r : server.getRecipeManager().getRecipes()) {
            h.assertTrue(r.id().getNamespace().equals(Minetorio.MODID), "vanilla recipe left: " + r.id());
        }
        h.assertFalse(logToPlanks(level), "log → planks still crafts");
        h.assertFalse(smeltRawIron(level), "raw iron still smelts");

        command(server, "gamerule minetorio:vanillaRecipes true");
        h.assertTrue(level.getGameRules().getBoolean(MinetorioGameRules.VANILLA_RECIPES), "/gamerule did not set the rule");
        h.assertTrue(logToPlanks(level) && smeltRawIron(level), "vanilla recipes not restored");
        command(server, "gamerule minetorio:vanillaRecipes false");
        h.assertFalse(logToPlanks(level) || smeltRawIron(level), "vanilla recipes not removed again");
        h.succeed();
    }

    @AfterBatch(batch = "vanilla_recipes")
    public static void restoreRecipes(ServerLevel level) {
        level.getGameRules().getRule(MinetorioGameRules.VANILLA_RECIPES).set(false, level.getServer());
    }

    /** Biomes list no vanilla spawns; any world-caused spawn of a vanilla mob is refused; spawn eggs still work. */
    @GameTest(template = "empty16")
    public static void noMobSpawns(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Biome plains = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        for (MobCategory c : MobCategory.values()) {
            h.assertTrue(plains.getMobSettings().getMobs(c).isEmpty(), "plains still spawns " + c + ": " + plains.getMobSettings().getMobs(c).unwrap());
        }
        BlockPos at = h.absolutePos(AT);
        h.assertTrue(EntityType.ZOMBIE.spawn(level, at, MobSpawnType.NATURAL) == null, "zombie spawned naturally");
        h.assertTrue(EntityType.COW.spawn(level, at, MobSpawnType.CHUNK_GENERATION) == null, "cow spawned with the chunk");
        h.assertTrue(EntityType.PILLAGER.spawn(level, at, MobSpawnType.PATROL) == null, "patrol spawned");
        h.assertTrue(EntityType.WANDERING_TRADER.spawn(level, at, MobSpawnType.EVENT) == null, "wandering trader spawned");
        h.assertTrue(EntityType.PHANTOM.spawn(level, at, MobSpawnType.NATURAL) == null, "phantom spawned");
        Entity egg = EntityType.ZOMBIE.spawn(level, at, MobSpawnType.SPAWN_EGG);
        h.assertTrue(egg != null, "spawn egg refused");
        egg.discard();
        @SuppressWarnings("unchecked")
        EntityType<?> fishType = BuiltInRegistries.ENTITY_TYPE.get(FishCatching.FISH_ID);
        Entity fish = fishType.spawn(level, at, MobSpawnType.NATURAL);
        h.assertTrue(fish != null, "the mod's fish refused");
        fish.discard();
        h.succeed();
    }

    /** Fire inside an obsidian frame lights no Nether portal. */
    @GameTest(template = "empty16")
    public static void netherPortalDoesNotLight(GameTestHelper h) {
        int y0 = 2;
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                boolean frame = x == 0 || x == 3 || y == 0 || y == 4;
                if (frame) h.setBlock(new BlockPos(4 + x, y0 + y, 6), Blocks.OBSIDIAN);
            }
        }
        h.setBlock(new BlockPos(5, y0 + 1, 6), Blocks.FIRE);
        for (int x = 1; x < 3; x++) {
            for (int y = 1; y < 4; y++) {
                h.assertFalse(h.getBlockState(new BlockPos(4 + x, y0 + y, 6)).is(Blocks.NETHER_PORTAL), "portal lit");
            }
        }
        h.succeed();
    }

    /** Exhaustion (sprinting, jumping, fighting) never drains the food bar, and the food tick does nothing. */
    @GameTest(template = "empty16")
    public static void hungerNeverDrops(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        for (int i = 0; i < 200; i++) {
            p.causeFoodExhaustion(4.0f);
            p.getFoodData().tick(p);
        }
        h.assertTrue(p.getFoodData().getFoodLevel() == 20, "food dropped to " + p.getFoodData().getFoodLevel());
        h.assertTrue(p.getFoodData().getExhaustionLevel() == 0f, "exhaustion " + p.getFoodData().getExhaustionLevel());
        // no vanilla saturation regeneration either
        p.setHealth(10);
        for (int i = 0; i < 100; i++) p.getFoodData().tick(p);
        h.assertTrue(p.getHealth() == 10, "food healed to " + p.getHealth());
        h.succeed();
    }

    /** Health comes back by itself only after {@link CharacterRules#REGEN_DELAY_TICKS} ticks without damage. */
    @GameTest(template = "empty16", timeoutTicks = 300)
    public static void regenAfterQuietSpell(GameTestHelper h) {
        FakePlayer p = player(h, AT);
        p.setHealth(10);
        CharacterHealth.markDamaged(p);
        h.runAfterDelay(CharacterRules.REGEN_DELAY_TICKS - 20, () -> {
            CharacterHealth.tick(p);
            h.assertTrue(p.getHealth() == 10, "healed while in combat: " + p.getHealth());
        });
        h.runAfterDelay(CharacterRules.REGEN_DELAY_TICKS + 20, () -> {
            for (int i = 0; i < 100; i++) CharacterHealth.tick(p);
            float expected = 10 + (float) (100 * CharacterRules.regenPerTick(20));
            h.assertTrue(Math.abs(p.getHealth() - expected) < 0.01, "health " + p.getHealth() + ", expected " + expected);
            h.succeed();
        });
    }

    /** Right-click or hit a fish: it is caught for raw fish; eating one heals 6.4 at once, then a cooldown. */
    @GameTest(template = "empty16")
    public static void catchAndEatFish(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer p = player(h, AT);
        EntityType<?> fishType = BuiltInRegistries.ENTITY_TYPE.get(FishCatching.FISH_ID);
        Entity fish = fishType.create(level);
        fish.moveTo(h.absoluteVec(AT.getCenter()).add(1, 0, 0));
        level.addFreshEntity(fish);
        p.interactOn(fish, InteractionHand.MAIN_HAND);
        h.assertFalse(fish.isAlive(), "fish not caught by right-click");
        h.assertTrue(p.getInventory().countItem(ModItems.RAW_FISH.get()) == CharacterRules.FISH_PER_CATCH,
                "raw fish after right-click: " + p.getInventory().countItem(ModItems.RAW_FISH.get()));

        Entity fish2 = fishType.create(level);
        fish2.moveTo(h.absoluteVec(AT.getCenter()).add(1, 0, 0));
        level.addFreshEntity(fish2);
        p.attack(fish2);
        h.assertFalse(fish2.isAlive(), "fish not caught by a hit");
        h.assertTrue(p.getInventory().countItem(ModItems.RAW_FISH.get()) == 2 * CharacterRules.FISH_PER_CATCH, "raw fish after a hit");

        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RAW_FISH.get(), 3));
        p.setHealth(10);
        p.getItemInHand(InteractionHand.MAIN_HAND).use(level, p, InteractionHand.MAIN_HAND);
        h.assertTrue(Math.abs(p.getHealth() - 16.4f) < 0.01, "health after fish " + p.getHealth());
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "fish not eaten");
        h.assertTrue(p.getCooldowns().isOnCooldown(ModItems.RAW_FISH.get()), "no cooldown");
        p.setHealth(20);
        p.getItemInHand(InteractionHand.MAIN_HAND).use(level, p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "fish eaten at full health");
        h.succeed();
    }

    /** A real (non-fake) server player that can die: FakePlayer.die does nothing. */
    private static ServerPlayer mortal(GameTestHelper h, BlockPos rel) {
        MinecraftServer server = h.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "corpse-test");
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(server, connection, p, CommonListenerCookie.createInitial(profile, false));
        BlockPos at = h.absolutePos(rel);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    /** Dying leaves one corpse holding the whole inventory (main, hotbar, armour, offhand); no item scatters. */
    @GameTest(template = "empty16")
    public static void deathLeavesCorpse(GameTestHelper h) {
        ServerPlayer p = mortal(h, AT);
        p.getInventory().setItem(0, new ItemStack(ModItems.IRON_PLATE.get(), 50));
        p.getInventory().setItem(20, new ItemStack(ModItems.COPPER_PLATE.get(), 7));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.RAW_FISH.get(), 3));
        p.die(p.damageSources().genericKill());

        AABB near = new AABB(h.absolutePos(AT)).inflate(3);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, near).isEmpty(), "items scattered");
        List<CharacterCorpseEntity> corpses = h.getLevel().getEntitiesOfClass(CharacterCorpseEntity.class, near);
        h.assertTrue(corpses.size() == 1, "corpses: " + corpses.size());
        CharacterCorpseEntity corpse = corpses.get(0);
        h.assertTrue(corpse.items().countItem(ModItems.IRON_PLATE.get()) == 50, "iron in corpse");
        h.assertTrue(corpse.items().countItem(ModItems.COPPER_PLATE.get()) == 7, "copper in corpse");
        h.assertTrue(corpse.items().countItem(Items.IRON_HELMET) == 1, "helmet in corpse");
        h.assertTrue(corpse.items().countItem(ModItems.RAW_FISH.get()) == 3, "offhand in corpse");
        h.assertTrue(p.getInventory().isEmpty(), "player kept items");

        // saved and loaded, it holds the same
        CompoundTag tag = corpse.saveWithoutId(new CompoundTag());
        CharacterCorpseEntity copy = new CharacterCorpseEntity(PlayerBasics.CHARACTER_CORPSE.get(), h.getLevel());
        copy.load(tag);
        h.assertTrue(copy.items().countItem(ModItems.IRON_PLATE.get()) == 50 && copy.items().countItem(Items.IRON_HELMET) == 1,
                "corpse lost items in save/load");
        corpse.discard();
        h.succeed();
    }

    /** Anyone can loot a corpse: sneak-use takes everything; the corpse goes once empty (also when emptied by hand). */
    @GameTest(template = "empty16", timeoutTicks = 40)
    public static void corpseLoot(GameTestHelper h) {
        ServerPlayer dead = mortal(h, AT);
        FakePlayer looter = player(h, AT);
        CharacterCorpseEntity corpse = CharacterCorpseEntity.of(dead,
                List.of(new ItemStack(ModItems.IRON_PLATE.get(), 10), new ItemStack(Items.IRON_HELMET)));
        h.getLevel().addFreshEntity(corpse);
        looter.setShiftKeyDown(true);
        looter.interactOn(corpse, InteractionHand.MAIN_HAND);
        h.assertTrue(looter.getInventory().countItem(ModItems.IRON_PLATE.get()) == 10, "iron not looted");
        h.assertTrue(looter.getInventory().countItem(Items.IRON_HELMET) == 1, "helmet not looted");
        h.assertFalse(corpse.isAlive(), "empty corpse stays");

        CharacterCorpseEntity second = CharacterCorpseEntity.of(dead, List.of(new ItemStack(ModItems.COPPER_PLATE.get(), 4)));
        h.getLevel().addFreshEntity(second);
        h.assertTrue(second.items().getContainerSize() == 9, "one row for one stack");
        second.items().removeItemNoUpdate(0);
        h.runAfterDelay(2, () -> {
            h.assertFalse(second.isAlive(), "corpse emptied through its slots stays");
            h.succeed();
        });
    }

    /** The day is Factorio-length: the clock runs 24000 / (416 × 20) ticks per tick. */
    @GameTest(template = "empty16")
    public static void factorioDayLength(GameTestHelper h) {
        float rate = h.getLevel().getDayTimePerTick();
        h.assertTrue(Math.abs(rate - CharacterRules.dayTimePerTick(CharacterRules.FACTORIO_DAY_SECONDS)) < 1e-4,
                "day time per tick " + rate);
        h.succeed();
    }
}
