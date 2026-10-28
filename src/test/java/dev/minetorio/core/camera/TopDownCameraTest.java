package dev.minetorio.core.camera;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TopDownCameraTest {
    private static final double EPS = 1e-9;

    private static void assertVec(double[] expected, double[] actual, double eps) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i], eps, "component " + i);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    @Test
    void forwardMatchesMinecraftFacings() {
        assertVec(new double[] {0, 0, 1}, TopDownCamera.forward(0, 0), EPS);    // south
        assertVec(new double[] {-1, 0, 0}, TopDownCamera.forward(90, 0), EPS);  // west
        assertVec(new double[] {0, 0, -1}, TopDownCamera.forward(180, 0), EPS); // north
        assertVec(new double[] {1, 0, 0}, TopDownCamera.forward(-90, 0), EPS);  // east
        assertVec(new double[] {0, -1, 0}, TopDownCamera.forward(0, 90), EPS);  // straight down
    }

    @Test
    void basisIsOrthonormalAndUpPointsUp() {
        for (double yaw = -180; yaw < 180; yaw += 45) {
            double[] f = TopDownCamera.forward(yaw, TopDownCamera.PITCH);
            double[] l = TopDownCamera.left(yaw);
            double[] u = TopDownCamera.up(yaw, TopDownCamera.PITCH);
            assertEquals(0, dot(f, l), EPS);
            assertEquals(0, dot(f, u), EPS);
            assertEquals(0, dot(l, u), EPS);
            assertEquals(1, dot(u, u), EPS);
            assertTrue(u[1] > 0, "up has a positive y");
        }
        // facing south, the screen's left is east (+x)
        assertVec(new double[] {1, 0, 0}, TopDownCamera.left(0), EPS);
    }

    @Test
    void orbitSitsAboveAndBehindTheCenter() {
        for (int step = 0; step < 4; step++) {
            double yaw = step * 90;
            double[] cam = TopDownCamera.orbit(10, 64, -5, yaw, 50, 20);
            assertEquals(84, cam[1], 1e-9, "height above the center");
            // looking from the camera along forward reaches the center after height/sin(pitch)
            double[] f = TopDownCamera.forward(yaw, 50);
            double d = 20 / Math.sin(Math.toRadians(50));
            assertVec(new double[] {10, 64, -5}, new double[] {cam[0] + f[0] * d, cam[1] + f[1] * d, cam[2] + f[2] * d}, 1e-9);
        }
        // yaw 0 looks south, so the camera is north of the center (smaller z)
        double[] south = TopDownCamera.orbit(0, 0, 0, 0, 50, 20);
        assertEquals(0, south[0], EPS);
        assertEquals(-20 / Math.tan(Math.toRadians(50)), south[2], 1e-9);
        // yaw 90 looks west, so the camera is east of the center
        assertTrue(TopDownCamera.orbit(0, 0, 0, 90, 50, 20)[0] > 0);
    }

    @Test
    void zoomStaysWithinLimits() {
        assertEquals(TopDownCamera.MIN_HEIGHT, TopDownCamera.zoom(9, 10), EPS);
        assertEquals(TopDownCamera.MAX_HEIGHT, TopDownCamera.zoom(39, -10), EPS);
        assertEquals(16 / 1.15, TopDownCamera.zoom(16, 1), EPS);
        assertEquals(16 * 1.15, TopDownCamera.zoom(16, -1), EPS);
    }

    @Test
    void ndcMapsCornersAndCenter() {
        assertVec(new double[] {0, 0}, TopDownCamera.ndc(960, 540, 1920, 1080), EPS);
        assertVec(new double[] {-1, 1}, TopDownCamera.ndc(0, 0, 1920, 1080), EPS);
        assertVec(new double[] {1, -1}, TopDownCamera.ndc(1920, 1080, 1920, 1080), EPS);
    }

    @Test
    void cursorRayThroughCenterIsForward() {
        assertVec(TopDownCamera.forward(30, 50), TopDownCamera.cursorRay(30, 50, 70, 16 / 9.0, 0, 0), EPS);
    }

    @Test
    void cursorRayAtScreenEdgesSpansTheFieldOfView() {
        double fov = 70, aspect = 16 / 9.0;
        double[] f = TopDownCamera.forward(0, 50);
        // top edge: the angle to forward is half the vertical fov, tilted toward up
        double[] top = TopDownCamera.cursorRay(0, 50, fov, aspect, 0, 1);
        assertEquals(Math.cos(Math.toRadians(fov / 2)), dot(top, f), 1e-9);
        assertTrue(top[1] > f[1], "the top of the screen looks less steeply down");
        // right edge: horizontal half-angle is atan(tan(fov/2) * aspect), toward -left
        double[] right = TopDownCamera.cursorRay(0, 50, fov, aspect, 1, 0);
        double half = Math.atan(Math.tan(Math.toRadians(fov / 2)) * aspect);
        assertEquals(Math.cos(half), dot(right, f), 1e-9);
        assertTrue(dot(right, TopDownCamera.left(0)) < 0, "screen right is the camera's right");
        // facing south, screen right is west (-x)
        assertTrue(right[0] < 0);
        assertEquals(1, dot(right, right), 1e-9);
    }

    @Test
    void cursorRayHitsTheGroundWhereExpected() {
        // camera above the origin's orbit; the center ray lands on the orbit center
        double yaw = 90, pitch = 50, h = 20;
        double[] cam = TopDownCamera.orbit(5, 0, 7, yaw, pitch, h);
        double[] dir = TopDownCamera.cursorRay(yaw, pitch, 70, 1.5, 0, 0);
        double t = -cam[1] / dir[1];
        assertEquals(5, cam[0] + dir[0] * t, 1e-9);
        assertEquals(7, cam[2] + dir[2] * t, 1e-9);
        // a cursor lower on the screen lands nearer the camera
        double[] low = TopDownCamera.cursorRay(yaw, pitch, 70, 1.5, 0, -0.5);
        double tl = -cam[1] / low[1];
        double nearX = cam[0] + low[0] * tl;
        assertTrue(nearX > 5 && nearX < cam[0], "between the center and the camera (camera is east)");
    }

    @Test
    void worldMoveFollowsTheCameraRotation() {
        // W (forward) moves the way the camera looks
        assertVec(new double[] {0, 1}, TopDownCamera.worldMove(1, 0, 0), EPS);    // south
        assertVec(new double[] {-1, 0}, TopDownCamera.worldMove(1, 0, 90), EPS);  // west
        assertVec(new double[] {0, -1}, TopDownCamera.worldMove(1, 0, 180), EPS); // north
        assertVec(new double[] {1, 0}, TopDownCamera.worldMove(1, 0, 270), EPS);  // east
        // A (strafe left) facing south goes east
        assertVec(new double[] {1, 0}, TopDownCamera.worldMove(0, 1, 0), EPS);
        // D (strafe right) facing west goes north
        assertVec(new double[] {0, -1}, TopDownCamera.worldMove(0, -1, 90), EPS);
    }

    @Test
    void remappedInputMovesTheEntityLikeTheCamera() {
        double[][] inputs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {0.3, -0.3}};
        for (double camYaw = 0; camYaw < 360; camYaw += 90) {
            for (double entityYaw = -170; entityYaw < 180; entityYaw += 37) {
                for (double[] in : inputs) {
                    double[] want = TopDownCamera.worldMove(in[0], in[1], camYaw);
                    double[] r = TopDownCamera.remapInput(in[0], in[1], camYaw, entityYaw);
                    assertVec(want, TopDownCamera.worldMove(r[0], r[1], entityYaw), 1e-9);
                    // remapping keeps the input's length (sneak/speed scaling survives)
                    assertEquals(Math.hypot(in[0], in[1]), Math.hypot(r[0], r[1]), 1e-9);
                }
            }
        }
    }

    @Test
    void aimLooksAtTheTarget() {
        double[] from = {1, 70, 2};
        double[] to = {-4, 60, 9};
        double[] a = TopDownCamera.aim(from, to);
        double[] f = TopDownCamera.forward(a[0], a[1]);
        double[] d = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
        double len = Math.sqrt(dot(d, d));
        assertVec(new double[] {d[0] / len, d[1] / len, d[2] / len}, f, 1e-9);
        assertTrue(a[1] > 0, "looking down is a positive pitch");
    }

    @Test
    void snapAndWrap() {
        assertEquals(0, TopDownCamera.snapYaw(30), EPS);
        assertEquals(90, TopDownCamera.snapYaw(50), EPS);
        assertEquals(-180, TopDownCamera.snapYaw(170), EPS);
        assertEquals(-90, TopDownCamera.snapYaw(-100), EPS);
        assertEquals(-90, TopDownCamera.snapYaw(270), EPS);
        assertEquals(-180, TopDownCamera.wrap(180), EPS);
        assertEquals(10, TopDownCamera.wrap(370), EPS);
        assertEquals(-10, TopDownCamera.wrap(-370), EPS);
    }

    @Test
    void approachConvergesAndTakesTheShortWayRound() {
        double v = 0;
        for (int i = 0; i < 200; i++) v = TopDownCamera.approach(v, 10, 12, 1 / 60.0, 1e-3);
        assertEquals(10, v, EPS);
        double half = TopDownCamera.approach(0, 10, Math.log(2), 1, 0);
        assertEquals(5, half, 1e-9);
        // from 170 to -170 goes up through 180, not down through 0
        double a = TopDownCamera.approachAngle(170, -170, 12, 1 / 60.0, 1e-3);
        assertTrue(a > 170, "moved toward 190, got " + a);
    }
}
