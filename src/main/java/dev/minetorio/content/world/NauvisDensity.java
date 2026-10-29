package dev.minetorio.content.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.minetorio.Minetorio;
import dev.minetorio.core.world.NauvisParams;
import dev.minetorio.core.world.NauvisTerrain;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Density functions that put the pure {@link NauvisTerrain} into the noise router of
 * {@code minetorio:nauvis} noise settings:
 * <ul>
 *   <li>{@code minetorio:nauvis_surface} — the column's top block y + 0.5 (2D; the final density is
 *       this minus y, so every block below the surface is solid);</li>
 *   <li>{@code minetorio:nauvis_climate} — a climate channel (water, moisture, aux) for the biomes.</li>
 * </ul>
 * Both take a {@code seed_noise}: density functions do not see the world seed, but the noises they
 * reference are seeded with it, so a few samples of that noise give the terrain seed
 * ({@link #terrain(NormalNoise)}). World generation code reaches the same terrain through the
 * level's random state ({@link #terrain(ServerLevel)}).
 */
public final class NauvisDensity {
    public static final ResourceKey<NoiseGeneratorSettings> SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "nauvis"));
    public static final ResourceKey<NormalNoise.NoiseParameters> SEED_NOISE =
            ResourceKey.create(Registries.NOISE, ResourceLocation.fromNamespaceAndPath(Minetorio.MODID, "nauvis_seed"));

    private NauvisDensity() {}

    /** The terrain whose seed is drawn from {@code seedNoise} (the world's instance of {@link #SEED_NOISE}). */
    public static NauvisTerrain terrain(NormalNoise seedNoise) {
        return NauvisTerrain.of(NauvisTerrain.seedFromSamples(
                seedNoise.getValue(0.5, 0, 0.25), seedNoise.getValue(-123.25, 0, 77.5), seedNoise.getValue(4567.125, 0, -89.75)));
    }

    /** The terrain of a level generated with the Minetorio noise settings, or null for any other generator. */
    @Nullable
    public static NauvisTerrain terrain(ServerLevel level) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator noise) || !noise.generatorSettings().is(SETTINGS)) return null;
        return terrain(level.getChunkSource().randomState().getOrCreateNoise(SEED_NOISE));
    }

    @Nullable
    private static NauvisTerrain terrain(DensityFunction.NoiseHolder holder) {
        return holder.noise() == null ? null : terrain(holder.noise());
    }

    /** Top block y + 0.5 of the column. */
    public record Surface(DensityFunction.NoiseHolder seedNoise, @Nullable NauvisTerrain terrain) implements DensityFunction.SimpleFunction {
        public static final MapCodec<Surface> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(Surface::seedNoise)
        ).apply(i, h -> new Surface(h, null)));
        public static final KeyDispatchDataCodec<Surface> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(FunctionContext ctx) {
            if (terrain == null) return NauvisParams.BASE_LAND;
            return terrain.surfaceY(ctx.blockX(), ctx.blockZ()) + 0.5;
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            DensityFunction.NoiseHolder h = visitor.visitNoise(seedNoise);
            return visitor.apply(new Surface(h, NauvisDensity.terrain(h)));
        }

        @Override
        public double minValue() { return NauvisParams.MIN_Y; }

        @Override
        public double maxValue() { return NauvisParams.MIN_Y + NauvisParams.HEIGHT; }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
    }

    public enum Channel implements StringRepresentable {
        WATER("water"), MOISTURE("moisture"), AUX("aux");

        public static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);
        private final String name;

        Channel(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    /** One climate channel of the column. */
    public record Climate(Channel channel, DensityFunction.NoiseHolder seedNoise, @Nullable NauvisTerrain terrain)
            implements DensityFunction.SimpleFunction {
        public static final MapCodec<Climate> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Channel.CODEC.fieldOf("channel").forGetter(Climate::channel),
                DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(Climate::seedNoise)
        ).apply(i, (c, h) -> new Climate(c, h, null)));
        public static final KeyDispatchDataCodec<Climate> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(FunctionContext ctx) {
            if (terrain == null) return 0;
            int x = ctx.blockX();
            int z = ctx.blockZ();
            return switch (channel) {
                case WATER -> terrain.water(x, z);
                case MOISTURE -> terrain.moisture(x, z);
                case AUX -> terrain.aux(x, z);
            };
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            DensityFunction.NoiseHolder h = visitor.visitNoise(seedNoise);
            return visitor.apply(new Climate(channel, h, NauvisDensity.terrain(h)));
        }

        @Override
        public double minValue() { return -1.5; }

        @Override
        public double maxValue() { return 1.5; }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
    }
}
