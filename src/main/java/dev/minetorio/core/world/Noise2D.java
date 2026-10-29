package dev.minetorio.core.world;

import java.util.SplittableRandom;

/**
 * Seeded 2D gradient (Perlin) noise with fractal octaves. Pure and deterministic: the same seed and
 * coordinates always give the same value, on any thread. Values of one octave lie roughly within
 * ±0.7; {@link #fbm} normalizes by the summed amplitudes.
 */
public final class Noise2D {
    private static final double[] GX = new double[16];
    private static final double[] GY = new double[16];

    static {
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16 + 0.1;
            GX[i] = Math.cos(a);
            GY[i] = Math.sin(a);
        }
    }

    private final int[] perm = new int[512];
    private final double offX;
    private final double offZ;

    public Noise2D(long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        // a random sub-cell offset, so lattice points (value 0) do not line up with the origin
        offX = r.nextDouble() * 256;
        offZ = r.nextDouble() * 256;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double grad(int ix, int iz, double dx, double dz) {
        int h = perm[perm[ix & 255] + (iz & 255)] & 15;
        return GX[h] * dx + GY[h] * dz;
    }

    /** One octave at (x, z) in lattice units. */
    public double sample(double x, double z) {
        x += offX;
        z += offZ;
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double u = fade(fx);
        double v = fade(fz);
        double a = grad(ix, iz, fx, fz);
        double b = grad(ix + 1, iz, fx - 1, fz);
        double c = grad(ix, iz + 1, fx, fz - 1);
        double d = grad(ix + 1, iz + 1, fx - 1, fz - 1);
        double ab = a + u * (b - a);
        double cd = c + u * (d - c);
        return ab + v * (cd - ab);
    }

    /**
     * Fractal noise at block (x, z): {@code octaves} octaves, the first with wavelength
     * {@code wavelength} blocks, each next one half as long and half as strong. Normalized by the
     * summed amplitudes, then scaled ×3 so that 90 % of values lie within about ±0.7 (σ ≈ 0.43; rarely beyond ±1.5).
     */
    public double fbm(double x, double z, double wavelength, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1 / wavelength;
        for (int o = 0; o < octaves; o++) {
            sum += amp * sample(x * f + o * 31.7, z * f - o * 17.3);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm * 3.0;
    }
}
