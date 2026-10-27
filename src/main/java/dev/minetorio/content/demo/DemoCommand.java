package dev.minetorio.content.demo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import dev.minetorio.content.deposit.Deposits;
import dev.minetorio.content.research.ResearchSavedData;
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
import org.slf4j.Logger;

/**
 * {@code /minetorio demo [<pos>]} (permission level 2): builds the demo showcase ({@link DemoBuilder})
 * around the executor's position, or around {@code pos} (the feet position: the floor is the block
 * below), and teleports a player executor to its entrance. The position form works from the server
 * console. Overworld only (the showcase needs a deposit). If the build fails, the command fails
 * with the reason and registers nothing. {@code /minetorio demo off} turns every demo effect off.
 */
public final class DemoCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DemoCommand() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> register(e.getDispatcher()));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minetorio")
                .requires(s -> s.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("demo")
                        .executes(ctx -> run(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
                        .then(Commands.literal("off").executes(ctx -> off(ctx.getSource())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> run(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"))))));
    }

    private static int run(CommandSourceStack source, BlockPos feet) {
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
        DemoBuilder.Layout layout;
        try {
            layout = DemoBuilder.build(level, origin);
        } catch (RuntimeException e) {
            LOGGER.error("Could not build the Minetorio demo at {}", origin, e);
            source.sendFailure(Component.translatable("minetorio.demo.failed", String.valueOf(e.getMessage())));
            return 0;
        }
        BlockPos in = layout.entrance();
        if (source.getEntity() instanceof ServerPlayer player) {
            player.teleportTo(level, in.getX() + 0.5, in.getY(), in.getZ() + 0.5, -90f, 0f); // looking east
        }
        Component research = ResearchSavedData.get(source.getServer()).state().current()
                .<Component>map(t -> Component.translatable(ResearchSavedData.nameKey(t.id())))
                .orElse(Component.translatable("minetorio.demo.no_research"));
        source.sendSuccess(() -> Component.translatable("minetorio.demo.built", in.getX(), in.getY(), in.getZ(), research), true);
        return 1;
    }

    /** Stops every demo effect in this world: endless chests and belt feeds, the research autopilot, the placed deposits. */
    private static int off(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        DemoSavedData data = DemoSavedData.getIfPresent(source.getServer());
        if (data != null) data.clear();
        int deposits = Deposits.removeAllPlacedDeposits(overworld);
        source.sendSuccess(() -> Component.translatable("minetorio.demo.off", deposits), true);
        return 1;
    }
}
