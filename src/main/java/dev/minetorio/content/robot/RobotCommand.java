package dev.minetorio.content.robot;

import com.mojang.brigadier.CommandDispatcher;
import dev.minetorio.content.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /minetorio drones} (permission level 2, creative/debug showcase): in front of the executor,
 * on its feet level, clears a 17×9 patch and builds a roboport with creative power and six robots,
 * a chest with the items, and ghosts of a small build — a belt line with a curve, an inserter and a
 * stone furnace — which the robots then build.
 */
public final class RobotCommand {
    private RobotCommand() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> register(e.getDispatcher()));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minetorio")
                .requires(s -> s.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("drones").executes(ctx -> run(ctx.getSource()))));
    }

    private static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Entity e = source.getEntity();
        Direction f = e != null ? e.getDirection() : Direction.NORTH;
        BlockPos feet = BlockPos.containing(source.getPosition());
        BlockPos base = feet.relative(f, 3);
        try {
            build(level, base, f);
        } catch (RuntimeException ex) {
            source.sendFailure(Component.translatable("minetorio.drones.failed", String.valueOf(ex.getMessage())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("minetorio.drones.built"), true);
        return 1;
    }

    /** The showcase; {@code base} is the front-center cell on the floor layer, {@code f} looks away from the player. */
    public static void build(ServerLevel level, BlockPos base, Direction f) {
        Direction r = f.getClockWise();
        // clear the patch and lay a floor
        for (int a = -9; a <= 7; a++) {
            for (int d = 0; d <= 8; d++) {
                BlockPos col = base.relative(r, a).relative(f, d);
                level.setBlock(col.below(), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                for (int y = 0; y < 4; y++) level.setBlock(col.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        level.getEntitiesOfClass(ConstructionRobotEntity.class, new AABB(base).inflate(12)).forEach(Entity::discard);

        // roboport (4x4) on the left, with power and robots
        BlockPos port = base.relative(r, -8);
        placeNow(level, port, ModItems.ROBOPORT.get(), f);
        if (!(level.getBlockEntity(port) instanceof RoboportBlockEntity rp)) throw new IllegalStateException("no roboport at " + port);
        rp.setCreativePower(true);
        rp.robots().setItem(0, new ItemStack(ModItems.CONSTRUCTION_ROBOT.get(), 6));

        // the chest with the items
        BlockPos chest = base.relative(r, -3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, f.getOpposite()),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity c) {
            c.setItem(0, new ItemStack(ModItems.TRANSPORT_BELT.get(), 16));
            c.setItem(1, new ItemStack(ModItems.INSERTER.get(), 4));
            c.setItem(2, new ItemStack(ModItems.STONE_FURNACE.get(), 2));
        }

        // ghosts: a belt line to the right that curves away, an inserter and a stone furnace
        for (int a = 0; a <= 3; a++) ghost(level, base.relative(r, a).relative(f, 1), ModItems.TRANSPORT_BELT.get(), r);
        for (int d = 1; d <= 3; d++) ghost(level, base.relative(r, 4).relative(f, d), ModItems.TRANSPORT_BELT.get(), f);
        ghost(level, base.relative(r, 4).relative(f, 4), ModItems.INSERTER.get(), f);
        ghost(level, base.relative(r, 4).relative(f, 5), ModItems.STONE_FURNACE.get(), f);
    }

    private static void ghost(ServerLevel level, BlockPos pos, Item item, Direction facing) {
        if (!Ghosts.place(level, pos, item, facing)) throw new IllegalStateException("no ghost at " + pos);
    }

    private static void placeNow(ServerLevel level, BlockPos pos, Item item, Direction facing) {
        ghost(level, pos, item, facing);
        if (!Ghosts.build(level, pos, null, new ItemStack(item))) throw new IllegalStateException("could not place at " + pos);
    }
}
