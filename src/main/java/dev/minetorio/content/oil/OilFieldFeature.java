package dev.minetorio.content.oil;

import dev.minetorio.content.orefield.OreFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class OilFieldFeature extends Feature<NoneFeatureConfiguration> {
    public OilFieldFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int cx = SectionPos.blockToSectionCoord(ctx.origin().getX()), cz = SectionPos.blockToSectionCoord(ctx.origin().getZ());
        boolean placed = false;
        for (OilFields.Well well : OilFields.inChunk(level.getSeed(), cx, cz)) {
            BlockPos spawn=level.getLevel().getSharedSpawnPos();
            if (Math.hypot(well.x()-spawn.getX(),well.z()-spawn.getZ()) < OilFields.EXCLUSION_RADIUS) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, well.x(), well.z()) - 1;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - OreFields.MAX_SCAN); y--) {
                BlockState state = level.getBlockState(pos.set(well.x(), y, well.z()));
                if (state.isAir()) continue;
                if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) break;
                if (state.is(OreFields.REPLACEABLE)) {
                    level.setBlock(pos, OilRegistry.OIL_SOURCE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(pos) instanceof OilSourceBlockEntity source) {
                        double originDistance=Math.hypot(well.x(),well.z());
                        double spawnDistance=Math.hypot(well.x()-spawn.getX(),well.z()-spawn.getZ());
                        source.initialize(well.fieldKey(),well.initialYield()+(spawnDistance-originDistance)/5000.0);
                    }
                    placed = true;
                    break;
                }
                if (!state.canBeReplaced() && state.blocksMotion()) break;
            }
        }
        return placed;
    }
}
