package dev.minetorio.content.deposit;

import dev.minetorio.core.deposit.Compass;
import dev.minetorio.core.deposit.Deposit;
import dev.minetorio.core.deposit.OreType;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Deposit scanner: right-click reports, in the action bar, the deposit under the player and how
 * much is left; otherwise the nearest deposit (by center) within {@link Deposits#SCAN_RADIUS}
 * blocks with its distance and 8-way bearing; otherwise that there is none in range.
 */
public class ScannerItem extends Item {
    public ScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            player.displayClientMessage(message(server, Mth.floor(player.getX()), Mth.floor(player.getZ())), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** The scanner's report for a player standing in column (x, z). */
    public static Component message(ServerLevel level, int x, int z) {
        Deposits deposits = Deposits.get(level);
        Optional<Deposit> here = deposits.depositAt(x, z);
        if (here.isPresent()) {
            Deposit d = here.get();
            return Component.translatable("minetorio.scanner.here", oreName(d.type()), amount(deposits.remaining(d)));
        }
        Optional<Deposit> near = deposits.nearest(x, z, Deposits.SCAN_RADIUS);
        if (near.isPresent()) {
            Deposit d = near.get();
            long dx = (long) d.centerX() - x;
            long dz = (long) d.centerZ() - z;
            long dist = Math.round(Math.sqrt((double) (dx * dx + dz * dz)));
            String dir = "minetorio.compass." + Compass.of(dx, dz).name().toLowerCase(Locale.ROOT);
            return Component.translatable("minetorio.scanner.nearest", oreName(d.type()), dist, Component.translatable(dir));
        }
        return Component.translatable("minetorio.scanner.none", Deposits.SCAN_RADIUS);
    }

    /** Lower-case ore name for use inside a sentence. */
    public static Component oreName(OreType type) {
        return Component.translatable("minetorio.ore." + type.name().toLowerCase(Locale.ROOT));
    }

    /** {@code 12345} → {@code "12 345"} (digit groups separated by spaces, in every language). */
    public static String amount(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }
}
