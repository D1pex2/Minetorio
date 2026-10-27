package dev.minetorio.gametest;

import dev.minetorio.content.multiblock.MultiblockBlockItem;
import dev.minetorio.content.multiblock.MultiblockPartBlockEntity;
import dev.minetorio.content.multiblock.MultiblockShape;
import dev.minetorio.content.registry.ModBlocks;
import dev.minetorio.content.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minetorio")
@PrefixGameTestTemplate(false)
public class MultiblockGameTest {
    private static final BlockPos ORIGIN = new BlockPos(2, 1, 2);
    /** A part of the furnace at ORIGIN (top, right, back corner). */
    private static final BlockPos PART = new BlockPos(3, 2, 1);

    /** Places a stone_furnace with its controller at {@code relative}; with no player the facing is NORTH. */
    private static InteractionResult placeFurnace(GameTestHelper h, BlockPos relative) {
        ItemStack stack = new ItemStack(ModItems.STONE_FURNACE.get());
        BlockPos abs = MultiblockBlockItem.targetFor(stack.getItem(), h.absolutePos(relative), Direction.NORTH);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockPlaceContext ctx = new BlockPlaceContext(h.getLevel(), null, InteractionHand.MAIN_HAND, stack, hit);
        return ((BlockItem) stack.getItem()).place(ctx);
    }

    private static List<BlockPos> furnaceFootprint(GameTestHelper h) {
        return MultiblockShape.STONE_FURNACE.footprint(h.absolutePos(ORIGIN), Direction.NORTH);
    }

    private static void assertAllAir(GameTestHelper h) {
        for (BlockPos p : furnaceFootprint(h)) h.assertTrue(h.getLevel().getBlockState(p).isAir(), "left over at " + p);
    }

    private static int droppedFurnaces(GameTestHelper h) {
        int n = 0;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
            if (e.getItem().is(ModItems.STONE_FURNACE.get())) n += e.getItem().getCount();
        }
        return n;
    }

    @GameTest(template = "empty16")
    public static void placesAllParts(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        BlockPos origin = h.absolutePos(ORIGIN);
        List<BlockPos> footprint = furnaceFootprint(h);
        h.assertTrue(footprint.size() == 8, "footprint size " + footprint.size());
        int parts = 0;
        for (BlockPos p : footprint) {
            if (p.equals(origin)) {
                h.assertTrue(h.getLevel().getBlockState(p).is(ModBlocks.STONE_FURNACE.get()), "controller missing");
                continue;
            }
            h.assertTrue(h.getLevel().getBlockState(p).is(ModBlocks.MULTIBLOCK_PART.get()), "part missing at " + p);
            MultiblockPartBlockEntity be = (MultiblockPartBlockEntity) h.getLevel().getBlockEntity(p);
            h.assertTrue(be != null, "part block entity missing at " + p);
            h.assertTrue(be.controllerOffset().equals(origin.subtract(p)), "wrong offset at " + p + ": " + be.controllerOffset());
            h.assertTrue(be.controller() != null, "controller lookup failed at " + p);

            var registries = h.getLevel().registryAccess();
            MultiblockPartBlockEntity copy = new MultiblockPartBlockEntity(p, h.getLevel().getBlockState(p));
            copy.loadWithComponents(be.saveWithoutMetadata(registries), registries);
            h.assertTrue(copy.controllerOffset().equals(be.controllerOffset()), "offset not persisted at " + p);
            parts++;
        }
        h.assertTrue(parts == 7, "expected 7 parts, got " + parts);
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void refusesWhenObstructed(GameTestHelper h) {
        BlockPos stone = PART;
        h.setBlock(stone, Blocks.STONE);
        h.assertFalse(placeFurnace(h, ORIGIN).consumesAction(), "placement should fail");
        for (BlockPos p : furnaceFootprint(h)) {
            if (p.equals(h.absolutePos(stone))) {
                h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE), "stone replaced");
            } else {
                h.assertTrue(h.getLevel().getBlockState(p).isAir(), "block placed at " + p);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void breakingPartBreaksAllDropsOne(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        BlockPos part = h.absolutePos(PART);
        h.assertTrue(h.getLevel().getBlockState(part).is(ModBlocks.MULTIBLOCK_PART.get()), "not a part");
        h.getLevel().destroyBlock(part, true);
        for (BlockPos p : furnaceFootprint(h)) h.assertTrue(h.getLevel().getBlockState(p).isAir(), "left over at " + p);
        h.assertTrue(droppedFurnaces(h) == 1, "dropped " + droppedFurnaces(h) + " furnaces");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void breakingControllerBreaksAllDropsOne(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        h.getLevel().destroyBlock(h.absolutePos(ORIGIN), true);
        for (BlockPos p : furnaceFootprint(h)) h.assertTrue(h.getLevel().getBlockState(p).isAir(), "left over at " + p);
        h.assertTrue(droppedFurnaces(h) == 1, "dropped " + droppedFurnaces(h) + " furnaces");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void survivalBreakOfPartDropsOne(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        ServerPlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(PART)), "break refused");
        assertAllAir(h);
        h.assertTrue(droppedFurnaces(h) == 1, "dropped " + droppedFurnaces(h) + " furnaces");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void creativeBreakOfPartDropsNothing(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        ServerPlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(PART)), "break refused");
        assertAllAir(h);
        h.assertTrue(droppedFurnaces(h) == 0, "dropped " + droppedFurnaces(h) + " furnaces");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void destroyPartWithoutDropsDropsNothing(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        h.getLevel().destroyBlock(h.absolutePos(PART), false);
        assertAllAir(h);
        h.assertTrue(droppedFurnaces(h) == 0, "dropped " + droppedFurnaces(h) + " furnaces");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void replacingPartKeepsReplacement(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        h.setBlock(PART, Blocks.STONE);
        BlockPos stone = h.absolutePos(PART);
        for (BlockPos p : furnaceFootprint(h)) {
            if (p.equals(stone)) h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE), "stone removed");
            else h.assertTrue(h.getLevel().getBlockState(p).isAir(), "left over at " + p);
        }
        h.assertTrue(droppedFurnaces(h) == 0, "dropped " + droppedFurnaces(h) + " furnaces");
        h.succeed();
    }

    @GameTest(template = "empty16")
    public static void partDelegatesCapabilitiesToController(GameTestHelper h) {
        h.assertTrue(placeFurnace(h, ORIGIN).consumesAction(), "placement failed");
        IItemHandler atController = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(ORIGIN), null);
        IItemHandler atPart = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(PART), Direction.UP);
        h.assertTrue(atController != null, "controller has no item handler");
        h.assertTrue(atPart == atController, "part did not return the controller's item handler");
        h.succeed();
    }
}
