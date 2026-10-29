package dev.minetorio.content.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.content.registry.ModWorld;
import dev.minetorio.core.world.NauvisTerrain;
import dev.minetorio.core.world.NauvisParams;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A Factorio-style tree on the surface at the origin: a slender trunk with a round crown of leaves,
 * or (no leaves) a dry dead tree with a few bare branches. Placed only on Minetorio ground, on level
 * land (not on or under a cliff edge, not in a rift); with {@code forest} the chance follows the
 * terrain's forest density, so trees grow in groves like on Nauvis.
 */
public class NauvisTreeFeature extends Feature<NauvisTreeFeature.Config> {
    public record Config(BlockState trunk, Optional<BlockState> leaves, Optional<BlockState> branches, int minHeight, int maxHeight,
                         boolean forest, float chance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("trunk").forGetter(Config::trunk),
                BlockState.CODEC.optionalFieldOf("leaves").forGetter(Config::leaves),
                BlockState.CODEC.optionalFieldOf("branches").forGetter(Config::branches),
                Codec.intRange(1, 12).fieldOf("min_height").forGetter(Config::minHeight),
                Codec.intRange(1, 12).fieldOf("max_height").forGetter(Config::maxHeight),
                Codec.BOOL.fieldOf("forest").forGetter(Config::forest),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Config::chance)
        ).apply(i, Config::new));
    }

    public NauvisTreeFeature() {
        super(Config.CODEC);
    }

    /** Whether column (x, z) is level land: no cliff, no rift, no water, every neighbour within one block. */
    static boolean levelLand(NauvisTerrain t, int x, int z) {
        if (t.isWater(x, z) || t.riftDepth(x, z) > 0) return false;
        int y = t.surfaceY(x, z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(t.surfaceY(x + dx, z + dz) - y) > 1) return false;
            }
        }
        return true;
    }

    /** Whether the block under {@code pos} is Minetorio ground. */
    static boolean onGround(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(ModWorld.NAUVIS_GROUND) && level.getBlockState(pos).isAir();
    }

    private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        Config c = ctx.config();
        RandomSource r = ctx.random();
        BlockPos o = ctx.origin();
        if (Math.hypot(o.getX(), o.getZ()) < NauvisParams.STARTER_CLEAR_RADIUS) return false;
        NauvisTerrain t = NauvisDensity.terrain(level.getLevel());
        if (t == null || !onGround(level, o) || !levelLand(t, o.getX(), o.getZ())) return false;
        double chance = c.chance() * (c.forest() ? t.forest(o.getX(), o.getZ()) : 1);
        if (r.nextFloat() >= chance) return false;
        return grow(level, o, c, r);
    }

    /** Grows the tree with its trunk's foot at {@code o} (no terrain checks); false if the trunk has no room. */
    public static boolean grow(WorldGenLevel level, BlockPos o, Config c, RandomSource r) {
        int h = c.minHeight() + r.nextInt(c.maxHeight() - c.minHeight() + 1);
        // Harvesting works on one connected crown. Keep a one-block air gap between natural trees.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = 0; y <= h + 2; y++) {
            if (level.getBlockState(o.offset(dx, y, dz)).getBlock() instanceof NaturalObjectBlock b
                    && b.group() == NaturalObjectBlock.Group.TREE) return false;
        }
        for (int y = 0; y < h; y++) {
            if (!level.getBlockState(o.above(y)).isAir()) return false;
        }
        for (int y = 0; y < h; y++) level.setBlock(o.above(y), c.trunk(), Block.UPDATE_CLIENTS);
        BlockPos top = o.above(h - 1);
        if (c.leaves().isPresent()) {
            BlockState leaves = c.leaves().get();
            // a round crown: radius 2 around the upper trunk, radius 1 on top, corners ragged
            for (int dy = -2; dy <= 1; dy++) {
                int rad = dy == 1 || dy == -2 ? 1 : 2;
                for (int dx = -rad; dx <= rad; dx++) {
                    for (int dz = -rad; dz <= rad; dz++) {
                        boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad;
                        if (corner && (rad == 1 ? r.nextInt(2) == 0 : r.nextInt(4) != 0)) continue;
                        if (rad == 2 && dy == -2) continue;
                        put(level, top.offset(dx, dy, dz), leaves);
                    }
                }
            }
            put(level, top.above(2), leaves);
        }
        if (c.branches().isPresent()) {
            BlockState branches = c.branches().get();
            put(level, top.above(), branches);
            int n = 1 + r.nextInt(2);
            for (int i = 0; i < n; i++) {
                int y = Math.max(1, h - 1 - r.nextInt(2));
                net.minecraft.core.Direction d = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(r);
                put(level, o.above(y).relative(d), branches);
            }
        }
        return true;
    }
}
