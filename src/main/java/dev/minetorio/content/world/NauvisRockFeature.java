package dev.minetorio.content.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.core.world.NauvisTerrain;
import dev.minetorio.core.world.NauvisParams;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A rock on level Minetorio ground at the origin: one block ({@code radius} 0, Factorio's big rock)
 * or a half-buried boulder of {@code radius} 1–2 (huge rock) mined as one object.
 */
public class NauvisRockFeature extends Feature<NauvisRockFeature.Config> {
    public record Config(BlockState block, int radius, float chance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Config::block),
                Codec.intRange(0, 2).fieldOf("radius").forGetter(Config::radius),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Config::chance)
        ).apply(i, Config::new));
    }

    public NauvisRockFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        Config c = ctx.config();
        RandomSource r = ctx.random();
        BlockPos o = ctx.origin();
        if (Math.hypot(o.getX(), o.getZ()) < NauvisParams.STARTER_CLEAR_RADIUS) return false;
        NauvisTerrain t = NauvisDensity.terrain(level.getLevel());
        if (t == null || r.nextFloat() >= c.chance() || !NauvisTreeFeature.onGround(level, o)
                || !NauvisTreeFeature.levelLand(t, o.getX(), o.getZ())) return false;
        int rad = c.radius();
        // Two touching huge rocks would become one connected harvest. Reserve an air gap first.
        for (int dx = -rad - 1; dx <= rad + 1; dx++) for (int dz = -rad - 1; dz <= rad + 1; dz++) {
            for (int dy = -1; dy <= rad + 2; dy++) {
                if (level.getBlockState(o.offset(dx, dy, dz)).getBlock() instanceof NaturalObjectBlock b
                        && b.group() == NaturalObjectBlock.Group.ROCK) return false;
            }
        }
        if (rad == 0) {
            level.setBlock(o, c.block(), Block.UPDATE_CLIENTS);
            return true;
        }
        double rx = rad + 0.3 + r.nextFloat() * 0.5;
        double rz = rad + 0.3 + r.nextFloat() * 0.5;
        double ry = rad * 0.8 + 0.4;
        for (int dx = -rad - 1; dx <= rad + 1; dx++) {
            for (int dz = -rad - 1; dz <= rad + 1; dz++) {
                for (int dy = -1; dy <= rad + 1; dy++) {
                    double e = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz) + ((dy + 0.5) * (dy + 0.5)) / (ry * ry);
                    if (e > 1) continue;
                    BlockPos p = o.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    // above ground only into air; one layer sinks into the ground (half-buried)
                    if (dy >= 0 ? s.isAir() : s.is(dev.minetorio.content.registry.ModWorld.NAUVIS_GROUND)) {
                        level.setBlock(p, c.block(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }
}
