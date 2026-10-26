package dev.minetorio.content.demo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /minetorio demo [<pos>]} (permission level 2): builds the demo showcase ({@link DemoBuilder})
 * around the executor's position, or around {@code pos} (the feet position: the floor is the block
 * below), and teleports a player executor to its entrance. The position form works from the server
 * console. Overworld only (the showcase needs a deposit).
 */
public final class DemoCommand {
    private DemoCommand() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> register(e.getDispatcher()));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minetorio")
                .requires(s -> s.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("demo")
                        .executes(ctx -> run(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"))))));
    }

    private static int run(CommandSourceStack source, BlockPos feet) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable("minetorio.demo.not_overworld"));
            return 0;
        }
        BlockPos origin = DemoBuilder.originAround(feet);
        if (origin.getY() - 1 < level.getMinBuildHeight() || origin.getY() + DemoBuilder.CLEAR_HEIGHT >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.translatable("minetorio.demo.out_of_world", DemoBuilder.CLEAR_HEIGHT));
            return 0;
        }
        DemoBuilder.Layout result = DemoBuilder.build(level, origin);
        BlockPos in = result.entrance();
        if (source.getEntity() instanceof ServerPlayer player) {
            player.teleportTo(level, in.getX() + 0.5, in.getY(), in.getZ() + 0.5, -90f, 0f); // looking east
        }
        source.sendSuccess(() -> Component.translatable("minetorio.demo.built", in.getX(), in.getY(), in.getZ()), true);
        return 1;
    }
}
