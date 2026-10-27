package dev.minetorio.gametest;

import static dev.minetorio.gametest.BeltGameTest.place;
import static dev.minetorio.gametest.InserterGameTest.container;
import static dev.minetorio.gametest.InserterGameTest.handler;
import static dev.minetorio.gametest.InserterGameTest.placeInserter;

import dev.minetorio.content.belt.LaneStacks;
import dev.minetorio.content.inserter.InserterBlockEntity;
import dev.minetorio.content.machine.BurnerDrillBlockEntity;
import dev.minetorio.content.power.BoilerBlockEntity;
import dev.minetorio.content.registry.ModItems;
import dev.minetorio.core.machine.Burner;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Burner fuel slots store fuel by plain item id, so fuel with data components (a renamed coal) must
 * never enter them, whatever the path: by hand, from an inserter, or kept by a burner inserter.
 */
@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class BurnerFuelGameTest {
    private static final String COAL = "minecraft:coal";
    /** Burner drill at (2,1,2) facing east: footprint x 2..3, z 2..3. */
    private static final BlockPos DRILL = new BlockPos(2, 1, 2);
    /** Boiler at (9,1,12) facing north: footprint x 9..11, z 11..12. */
    private static final BlockPos BOILER = new BlockPos(9, 1, 12);

    private static ItemStack namedCoal(int count) {
        ItemStack s = new ItemStack(Items.COAL, count);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("special"));
        return s;
    }

    private static int named(IItemHandler handler) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.is(Items.COAL) && s.has(DataComponents.CUSTOM_NAME)) n += s.getCount();
        }
        return n;
    }

    /** A burner drill without an ore field under it (so it never burns its fuel). */
    private static BurnerDrillBlockEntity drill(GameTestHelper h) {
        place(h, ModItems.BURNER_MINING_DRILL.get(), DRILL, Direction.EAST);
        return (BurnerDrillBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(DRILL));
    }

    private static BoilerBlockEntity boiler(GameTestHelper h) {
        place(h, ModItems.BOILER.get(), BOILER, Direction.NORTH);
        return (BoilerBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BOILER));
    }

    /** Right-clicking the boiler, the burner drill or a burner inserter with renamed coal: refused, the hand keeps it. */
    @GameTest(template = "empty16")
    public static void namedFuelRefusedByHand(GameTestHelper h) {
        BurnerDrillBlockEntity drill = drill(h);
        BoilerBlockEntity boiler = boiler(h);
        BlockPos inserterAt = new BlockPos(6, 1, 6);
        InserterBlockEntity inserter = placeInserter(h, ModItems.BURNER_INSERTER.get(), inserterAt, Direction.EAST);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (BlockPos at : new BlockPos[] {DRILL, BOILER, inserterAt}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, namedCoal(3));
            h.useBlock(at, player);
            h.assertTrue(player.getMainHandItem().getCount() == 3 && player.getMainHandItem().has(DataComponents.CUSTOM_NAME),
                    "hand at " + at + " has " + player.getMainHandItem());
        }
        h.assertTrue(drill.burner().fuel().isEmpty(), "drill took named coal: " + drill.burner().fuel());
        h.assertTrue(boiler.burner().fuel().isEmpty(), "boiler took named coal: " + boiler.burner().fuel());
        h.assertTrue(inserter.burner().fuel().isEmpty(), "inserter took named coal: " + inserter.burner().fuel());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 3));
        h.useBlock(DRILL, player);
        h.assertTrue(drill.burner().fuel().getOrDefault(COAL, 0) == 3, "plain coal refused: " + drill.burner().fuel());
        h.succeed();
    }

    /**
     * An inserter between a chest of renamed coal and a burner drill leaves the coal in the chest;
     * the boiler's inserter target refuses the key too. Plain coal still goes through.
     */
    @GameTest(template = "empty16", timeoutTicks = 200)
    public static void namedFuelRefusedFromInserters(GameTestHelper h) {
        BurnerDrillBlockEntity drill = drill(h);
        BoilerBlockEntity boiler = boiler(h);
        BlockPos chest = new BlockPos(2, 1, 0);
        container(h, Blocks.CHEST, chest, namedCoal(2));
        InserterBlockEntity inserter = placeInserter(h, ModItems.INSERTER.get(), new BlockPos(2, 1, 1), Direction.SOUTH);
        String key = LaneStacks.key(namedCoal(1), h.getLevel().registryAccess());
        h.assertFalse(boiler.asTarget().wants(key) || boiler.asTarget().accept(key), "boiler target took the named coal key");
        h.assertFalse(drill.asTarget().wants(key) || drill.asTarget().accept(key), "drill target took the named coal key");
        h.assertTrue(boiler.burner().fuel().isEmpty() && drill.burner().fuel().isEmpty(), "fuel taken through the targets");
        h.runAtTickTime(80, () -> {
            h.assertTrue(named(handler(h, chest)) == 2, "named coal left the chest: " + named(handler(h, chest)));
            h.assertTrue(inserter.logic().held() == null, "inserter holds " + inserter.logic().held());
            h.assertTrue(drill.burner().fuel().isEmpty(), "drill fuel " + drill.burner().fuel());
            handler(h, chest).insertItem(5, new ItemStack(Items.COAL, 1), false);
        });
        h.runAtTickTime(160, () -> {
            h.assertTrue(drill.burner().fuel().getOrDefault(COAL, 0) == 1, "plain coal not delivered: " + drill.burner().fuel());
            h.assertTrue(named(handler(h, chest)) == 2, "named coal left the chest");
            h.succeed();
        });
    }

    /** A fuelless burner inserter holding renamed coal does not burn it: the item stays in the hand. */
    @GameTest(template = "empty16")
    public static void burnerInserterDoesNotKeepNamedFuel(GameTestHelper h) {
        InserterBlockEntity be = placeInserter(h, ModItems.BURNER_INSERTER.get(), new BlockPos(2, 1, 2), Direction.EAST);
        String key = LaneStacks.key(namedCoal(1), h.getLevel().registryAccess());
        be.logic().load(Map.of("phase", "TO_DROP", "held", key, "leg", 0.0));
        h.runAtTickTime(5, () -> {
            h.assertTrue(be.burner().fuel().isEmpty() && be.burner().remainingJ() == 0, "kept as fuel: " + be.burner().fuel());
            h.assertTrue(key.equals(be.logic().held()), "hand holds " + be.logic().held());
            h.succeed();
        });
    }

    /** The burner NBT layout ({burner: {fuel: {id: n}, remaining: J}}) loads and saves unchanged on all three burner machines. */
    @GameTest(template = "empty16")
    public static void burnerNbtLayoutUnchanged(GameTestHelper h) {
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        BurnerDrillBlockEntity drill = drill(h);
        BoilerBlockEntity boiler = boiler(h);
        InserterBlockEntity inserter = placeInserter(h, ModItems.BURNER_INSERTER.get(), new BlockPos(6, 1, 6), Direction.EAST);
        checkBurnerNbt(h, drill, drill.burner(), registries);
        checkBurnerNbt(h, boiler, boiler.burner(), registries);
        checkBurnerNbt(h, inserter, inserter.burner(), registries);
        h.succeed();
    }

    private static void checkBurnerNbt(GameTestHelper h, BlockEntity be, Burner burner, HolderLookup.Provider registries) {
        CompoundTag tag = be.saveWithoutMetadata(registries);
        CompoundTag legacy = new CompoundTag();
        CompoundTag fuel = new CompoundTag();
        fuel.putInt(COAL, 4);
        legacy.put("fuel", fuel);
        legacy.putLong("remaining", 12_345L);
        tag.put("burner", legacy);
        be.loadWithComponents(tag, registries);
        h.assertTrue(burner.fuel().equals(Map.of(COAL, 4)) && burner.remainingJ() == 12_345L,
                be.getType() + " loaded " + burner.fuel() + " / " + burner.remainingJ());
        CompoundTag saved = be.saveWithoutMetadata(registries).getCompound("burner");
        h.assertTrue(saved.equals(legacy), be.getType() + " saved " + saved);
    }
}
