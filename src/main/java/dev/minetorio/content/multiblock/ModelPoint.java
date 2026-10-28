package dev.minetorio.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a point of a machine's model is in the world, for client effects (smoke from a chimney, steam
 * from a valve): models are authored in the north orientation around the footprint's center block
 * (model x = z = 8 is the footprint center, 16 px per block; see client MachineRender.toCenter, which
 * this mirrors).
 */
public final class ModelPoint {
    private ModelPoint() {}

    /** World position of model point ({@code x}, {@code y}, {@code z}) px of the machine whose controller is at {@code origin}. */
    public static Vec3 world(BlockPos origin, Direction facing, MultiblockShape shape, double x, double y, double z) {
        Direction right = facing.getClockWise();
        double w = (shape.width() - 1) / 2.0, d = (shape.depth() - 1) / 2.0;
        double side = (x - 8) / 16, ahead = -(z - 8) / 16;
        return new Vec3(
                origin.getX() + 0.5 + right.getStepX() * (w + side) + facing.getStepX() * (d + ahead),
                origin.getY() + y / 16,
                origin.getZ() + 0.5 + right.getStepZ() * (w + side) + facing.getStepZ() * (d + ahead));
    }

    /** Puffs {@code count} particles rising from model point ({@code x}, {@code y}, {@code z}), spread {@code spread} px. */
    public static void puff(Level level, RandomSource random, ParticleOptions particle, int count, BlockPos origin, Direction facing,
                            MultiblockShape shape, double x, double y, double z, double spread, double rise) {
        Vec3 p = world(origin, facing, shape, x, y, z);
        double s = spread / 16;
        for (int i = 0; i < count; i++) {
            level.addParticle(particle, p.x + (random.nextDouble() - 0.5) * s, p.y, p.z + (random.nextDouble() - 0.5) * s,
                    (random.nextDouble() - 0.5) * 0.01, rise * (0.7 + 0.6 * random.nextDouble()), (random.nextDouble() - 0.5) * 0.01);
        }
    }
}
