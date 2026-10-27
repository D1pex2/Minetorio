package dev.minetorio.client.belt;

import org.joml.Matrix4f;

/**
 * Rotations that orient an item model on a belt, written as explicit basis vectors so the result
 * does not depend on the model's own display transforms (items are rendered with
 * {@code ItemDisplayContext.NONE}, i.e. in raw model space: a generated item icon is the quad
 * x,y ∈ [0,1] at z ≈ 0.5, its textured front facing +Z and the top of the picture at +Y).
 *
 * <p>Columns of each matrix are the world directions of the model's +X, +Y and +Z axes.
 * {@code (dx, dz)} is the unit travel direction on the belt (world XZ).
 */
public final class LanePose {
    private LanePose() {}

    /** {@link #flatItem(double, double, double)} on a flat belt. */
    public static Matrix4f flatItem(double dx, double dz) {
        return flatItem(dx, dz, 0);
    }

    /**
     * Icon lying face-up on the belt surface: model +Y (picture top) → travel T, model +Z (front
     * normal) → surface normal N, model +X → (−dz, 0, dx) so the basis stays right-handed (no
     * mirroring). {@code grade} is the surface's rise per block of run along the travel (0 flat,
     * ±1 on a ramp): T = (dx·c, s, dz·c) and N = (−dx·s, c, −dz·s) with the pitch's sine s and
     * cosine c; flat, T is the travel and N is +Y.
     */
    public static Matrix4f flatItem(double dx, double dz, double grade) {
        float x = (float) dx;
        float z = (float) dz;
        float c = (float) (1 / Math.sqrt(1 + grade * grade));
        float s = (float) grade * c;
        return new Matrix4f(
                -z, 0, x, 0,               // column 0: image of +X
                x * c, s, z * c, 0,        // column 1: image of +Y (travel)
                -x * s, c, -z * s, 0,      // column 2: image of +Z (surface normal)
                0, 0, 0, 1);
    }

    /** {@link #uprightBlock(double, double, double)} on a flat belt. */
    public static Matrix4f uprightBlock(double dx, double dz) {
        return uprightBlock(dx, dz, 0);
    }

    /**
     * Block model standing on the surface: model +Y → surface normal N, model +Z → travel T (as in
     * {@link #flatItem(double, double, double)}), model +X → (dz, 0, −dx).
     */
    public static Matrix4f uprightBlock(double dx, double dz, double grade) {
        float x = (float) dx;
        float z = (float) dz;
        float c = (float) (1 / Math.sqrt(1 + grade * grade));
        float s = (float) grade * c;
        return new Matrix4f(
                z, 0, -x, 0,
                -x * s, c, -z * s, 0,
                x * c, s, z * c, 0,
                0, 0, 0, 1);
    }
}
