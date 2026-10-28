package dev.minetorio.core.blueprint;

import dev.minetorio.core.drag.DragDir;
import java.util.ArrayList;
import java.util.List;

/**
 * How a blueprint is turned for pasting: first mirrored left-right (x to -x) when {@code mirror},
 * then turned {@code rotation} quarter turns clockwise (seen from above). Every orientation of the
 * square group is one of these eight. R, F and G compose onto the current transform
 * ({@link #rotated}, {@link #flippedX}, {@link #flippedZ}).
 */
public record BlueprintTransform(int rotation, boolean mirror) {
    public static final BlueprintTransform IDENTITY = new BlueprintTransform(0, false);

    public BlueprintTransform {
        rotation = Math.floorMod(rotation, 4);
    }

    /** One more quarter turn clockwise (R). */
    public BlueprintTransform rotated() {
        return new BlueprintTransform(rotation + 1, mirror);
    }

    /** The current orientation mirrored left-right in the world (x to -x; F). */
    public BlueprintTransform flippedX() {
        // Mx . R^r . M^m = R^-r . Mx . M^m
        return new BlueprintTransform(-rotation, !mirror);
    }

    /** The current orientation mirrored front-back in the world (z to -z; G): Mz = R^2 . Mx. */
    public BlueprintTransform flippedZ() {
        return new BlueprintTransform(2 - rotation, !mirror);
    }

    /** Network/byte form: rotation in bits 0-1, mirror in bit 2. */
    public int bits() {
        return rotation | (mirror ? 4 : 0);
    }

    public static BlueprintTransform ofBits(int bits) {
        return new BlueprintTransform(bits & 3, (bits & 4) != 0);
    }

    /** The cell {@code (x, z)} transformed (about the origin). */
    public int[] apply(int x, int z) {
        if (mirror) x = -x;
        for (int i = 0; i < rotation; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[] {x, z};
    }

    public DragDir apply(DragDir d) {
        int[] v = apply(d.dx(), d.dz());
        for (DragDir o : DragDir.values()) {
            if (o.dx() == v[0] && o.dz() == v[1]) return o;
        }
        throw new IllegalStateException("not a direction: " + v[0] + "," + v[1]);
    }

    /**
     * A part transformed: its footprint box goes where the transform takes it, its facing turns
     * (and flips with a mirror) and its origin becomes the box's left-front corner for the new facing.
     */
    public BlueprintPart apply(BlueprintPart p) {
        int[] a = apply(p.minX(), p.minZ());
        int[] b = apply(p.maxX(), p.maxZ());
        int minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]);
        int minZ = Math.min(a[1], b[1]), maxZ = Math.max(a[1], b[1]);
        DragDir facing = p.facing() == null ? null : apply(p.facing());
        DragDir f = facing == null ? DragDir.NORTH : facing, r = f.clockWise();
        // the origin is the corner from which width runs along r and depth along f (exactly one of them has an x step)
        int ox = (r.dx() + f.dx()) > 0 ? minX : maxX;
        int oz = (r.dz() + f.dz()) > 0 ? minZ : maxZ;
        return new BlueprintPart(p.block(), ox, p.y(), oz, facing, p.width(), p.depth(), p.height(), p.variant());
    }

    /** Every part transformed, then moved so the footprints start at (0, 0, 0) again (order kept). */
    public List<BlueprintPart> apply(List<BlueprintPart> parts) {
        List<BlueprintPart> out = new ArrayList<>(parts.size());
        for (BlueprintPart p : parts) out.add(apply(p));
        return Blueprints.normalized(out);
    }
}
