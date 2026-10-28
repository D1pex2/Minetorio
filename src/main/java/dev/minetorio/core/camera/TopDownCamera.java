package dev.minetorio.core.camera;

/**
 * Pure math of the top-down build camera, in Minecraft's angle conventions: yaw in degrees with
 * 0 facing +Z (south) and 90 facing -X (west); pitch in degrees, positive looking down. World
 * vectors are {@code double[3]} as {x, y, z}.
 */
public final class TopDownCamera {
    /** Camera pitch: looks down at this angle like Factorio's view. */
    public static final double PITCH = 50.0;
    public static final double MIN_HEIGHT = 8.0;
    public static final double MAX_HEIGHT = 40.0;
    public static final double DEFAULT_HEIGHT = 16.0;
    /** Height factor per mouse-wheel notch. */
    public static final double ZOOM_STEP = 1.15;

    private TopDownCamera() {}

    /** Unit view direction for {@code yaw}/{@code pitch} (Minecraft's {@code Entity.calculateViewVector}). */
    public static double[] forward(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    /** Unit vector to the screen's left (horizontal; independent of pitch). */
    public static double[] left(double yaw) {
        double y = Math.toRadians(yaw);
        return new double[] {Math.cos(y), 0, Math.sin(y)};
    }

    /** Unit vector to the screen's top: {@code forward x left}. */
    public static double[] up(double yaw, double pitch) {
        return cross(forward(yaw, pitch), left(yaw));
    }

    /**
     * Camera position orbiting {@code center} at {@code height} blocks above it, looking at it from
     * {@code yaw}/{@code pitch} (the camera sits back along the view direction).
     */
    public static double[] orbit(double cx, double cy, double cz, double yaw, double pitch, double height) {
        double dist = height / Math.sin(Math.toRadians(pitch));
        double[] f = forward(yaw, pitch);
        return new double[] {cx - f[0] * dist, cy - f[1] * dist, cz - f[2] * dist};
    }

    /** Height clamped to {@link #MIN_HEIGHT}..{@link #MAX_HEIGHT}. */
    public static double clampHeight(double h) {
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
    }

    /** Height after {@code notches} wheel notches (positive zooms in, i.e. lowers the camera). */
    public static double zoom(double height, double notches) {
        return clampHeight(height / Math.pow(ZOOM_STEP, notches));
    }

    /** Normalized device coordinates (-1..1, y up) of a cursor at pixel {@code (mx, my)} (y down). */
    public static double[] ndc(double mx, double my, double width, double height) {
        return new double[] {2.0 * mx / width - 1.0, 1.0 - 2.0 * my / height};
    }

    /**
     * Unit world direction of the ray through the screen point {@code (ndcX, ndcY)} of a
     * perspective camera with vertical field of view {@code fovDeg} and width/height {@code aspect}.
     */
    public static double[] cursorRay(double yaw, double pitch, double fovDeg, double aspect, double ndcX, double ndcY) {
        double t = Math.tan(Math.toRadians(fovDeg) / 2);
        double[] f = forward(yaw, pitch), l = left(yaw), u = up(yaw, pitch);
        double sx = -ndcX * t * aspect, sy = ndcY * t; // screen right is -left
        return normalize(new double[] {
                f[0] + l[0] * sx + u[0] * sy, f[1] + l[1] * sx + u[1] * sy, f[2] + l[2] * sx + u[2] * sy});
    }

    /**
     * World horizontal move {dx, dz} for movement input ({@code forward} up the screen,
     * {@code strafe} to the screen's left) seen by a camera at {@code yaw}, as Minecraft's
     * {@code Entity.getInputVector} turns input into motion.
     */
    public static double[] worldMove(double forward, double strafe, double yaw) {
        double y = Math.toRadians(yaw), s = Math.sin(y), c = Math.cos(y);
        return new double[] {strafe * c - forward * s, forward * c + strafe * s};
    }

    /**
     * Movement input {forward, strafe} for an entity facing {@code entityYaw} that moves the same
     * way in the world as {@code (forward, strafe)} does seen from a camera at {@code cameraYaw}.
     */
    public static double[] remapInput(double forward, double strafe, double cameraYaw, double entityYaw) {
        double d = Math.toRadians(cameraYaw - entityYaw), s = Math.sin(d), c = Math.cos(d);
        return new double[] {forward * c + strafe * s, strafe * c - forward * s};
    }

    /** Yaw/pitch {yaw, pitch} that looks from {@code from} to {@code to}. */
    public static double[] aim(double[] from, double[] to) {
        double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
        double h = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(dy, h));
        return new double[] {wrap(yaw), pitch};
    }

    /** The multiple of 90 degrees nearest {@code yaw}, wrapped to -180..180. */
    public static double snapYaw(double yaw) {
        return wrap(Math.round(yaw / 90.0) * 90.0);
    }

    /** Angle wrapped to [-180, 180). */
    public static double wrap(double deg) {
        double d = ((deg + 180.0) % 360.0 + 360.0) % 360.0;
        return d - 180.0;
    }

    /**
     * {@code current} moved toward {@code target} by the share {@code 1 - exp(-rate * dt)} of the
     * way (frame-rate independent smoothing); snaps when within {@code eps}.
     */
    public static double approach(double current, double target, double rate, double dt, double eps) {
        double next = current + (target - current) * (1.0 - Math.exp(-rate * dt));
        return Math.abs(target - next) < eps ? target : next;
    }

    /** {@link #approach} for angles: takes the short way round; the result is not wrapped. */
    public static double approachAngle(double current, double target, double rate, double dt, double eps) {
        return approach(current, current + wrap(target - current), rate, dt, eps);
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / len, v[1] / len, v[2] / len};
    }
}
