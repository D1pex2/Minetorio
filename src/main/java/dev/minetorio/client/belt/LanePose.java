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

    /**
     * Icon lying face-up: model +Z (front normal) → world +Y, model +Y (picture top) → travel,
     * model +X → travel × up = (−dz, 0, dx) so the basis stays right-handed (no mirroring).
     */
    public static Matrix4f flatItem(double dx, double dz) {
        float x = (float) dx;
        float z = (float) dz;
        return new Matrix4f(
                -z, 0, x, 0,   // column 0: image of +X
                x, 0, z, 0,    // column 1: image of +Y
                0, 1, 0, 0,    // column 2: image of +Z
                0, 0, 0, 1);
    }

    /** Block model standing upright: model +Y → up, model +Z → travel, model +X → (dz, 0, −dx). */
    public static Matrix4f uprightBlock(double dx, double dz) {
        float x = (float) dx;
        float z = (float) dz;
        return new Matrix4f(
                z, 0, -x, 0,
                0, 1, 0, 0,
                x, 0, z, 0,
                0, 0, 0, 1);
    }
}
