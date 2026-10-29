package dev.minetorio.content.world;

import dev.minetorio.content.registry.ModWorld;
import dev.minetorio.core.world.NauvisTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Cliff walls (placed once per chunk at {@code local_modifications}): every column standing 2+
 * blocks above a dry neighbour ({@link NauvisTerrain#isCliffEdge}) is cliff rock from one below its
 * lowest neighbour's surface up to its own top, so terrace steps and rift walls are bare rock that
 * a pickaxe cannot break. Reads the pure terrain, not the chunk, so chunk borders need no neighbours.
 */
public class CliffRockFeature extends Feature<NoneFeatureConfiguration> {
    public CliffRockFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        NauvisTerrain t = NauvisDensity.terrain(level.getLevel());
        if (t == null) return false;
        int x0 = SectionPos.blockToSectionCoord(ctx.origin().getX()) << 4;
        int z0 = SectionPos.blockToSectionCoord(ctx.origin().getZ()) << 4;
        BlockState rock = ModWorld.CLIFF_ROCK.get().defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                if (!t.isCliffEdge(x, z)) continue;
                int top = t.surfaceY(x, z);
                int low = t.lowestAround(x, z);
                for (int y = low - 1; y <= top; y++) {
                    if (level.getBlockState(p.set(x, y, z)).isAir()) continue;
                    level.setBlock(p, rock, Block.UPDATE_CLIENTS);
                    any = true;
                }
            }
        }
        return any;
    }
}
