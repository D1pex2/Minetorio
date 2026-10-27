package dev.minetorio.content.orefield;

import dev.minetorio.core.orefield.FieldStamp;
import dev.minetorio.core.orefield.OrePatch;
import dev.minetorio.core.orefield.PatchGenerator;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * World generation of ore fields (placed once per chunk in every Overworld biome at the
 * {@code top_layer_modification} step, see {@code data/minetorio/neoforge/biome_modifier/ore_fields.json}):
 * stamps the part of the generated patches ({@link PatchGenerator}, seed only) that falls into the
 * chunk being generated, each column on its own surface ({@link OreFields#stampColumn}).
 *
 * <p>Patches are laid out around the world origin (0, 0), not the world spawn: the spawn is not
 * known yet while the first chunks generate and can move later, while the layout must never change
 * between chunks. The amount's distance factor is measured from the origin too.
 *
 * <p>Columns are searched down from the {@code WORLD_SURFACE} heightmap (kept up to date during the
 * feature steps, unlike the {@code _WG} ones), so trees placed earlier are seen and skipped.
 */
public class OreFieldFeature extends Feature<NoneFeatureConfiguration> {
    public OreFieldFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int x0 = SectionPos.blockToSectionCoord(ctx.origin().getX()) << 4;
        int z0 = SectionPos.blockToSectionCoord(ctx.origin().getZ()) << 4;
        List<OrePatch> patches = new PatchGenerator(level.getSeed(), 0, 0).patchesTouching(x0, z0, x0 + 15, z0 + 15);
        if (patches.isEmpty()) return false;
        boolean any = false;
        for (FieldStamp.Cell c : FieldStamp.plan(patches, x0, z0, x0 + 15, z0 + 15, 0, 0)) {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, c.x(), c.z()) - 1;
            any |= OreFields.stampColumn(level, c.x(), c.z(), top, level.getMinBuildHeight(), c.type(), c.amount());
        }
        return any;
    }
}
