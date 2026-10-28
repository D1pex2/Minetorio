package dev.minetorio.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No two faces of our JSON models z-fight: same plane (within 0.001 px), same facing, overlapping
 * area. Faces facing opposite ways are never seen together (back faces are culled); an overlap whose
 * front is filled by a third element is covered. Besides every model on its own, models drawn
 * together by a block entity renderer or a multipart blockstate are checked with the renderer's
 * offsets ({@link #composites()}). Java port of {@code art/check_zfight.py}, which prints the
 * findings as a table; both must agree.
 */
class ModelZFightTest {
    private static final Path MODELS = Paths.get(System.getProperty("minetorio.projectDir", "."),
            "src/main/resources/assets/minetorio/models");
    private static final double PLANE_EPS = 0.001;
    private static final double AREA_EPS = 1e-4;

    // ------------------------------------------------------------------ geometry

    record V(double x, double y, double z) {
        V add(V o) { return new V(x + o.x, y + o.y, z + o.z); }
        V sub(V o) { return new V(x - o.x, y - o.y, z - o.z); }
        V mul(double k) { return new V(x * k, y * k, z * k); }
        double dot(V o) { return x * o.x + y * o.y + z * o.z; }
        V cross(V o) { return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
        V norm() { return mul(1 / Math.sqrt(dot(this))); }
        double get(int i) { return i == 0 ? x : i == 1 ? y : z; }
    }

    private static final V ZERO = new V(0, 0, 0);
    private static final V X = new V(1, 0, 0);
    private static final V Y = new V(0, 1, 0);
    private static final V Z = new V(0, 0, 1);

    /** A translation (axis 't', offset v) or a right-handed rotation about axis x/y/z through origin v. */
    record Tr(char axis, double deg, V v) {
        static Tr t(double dx, double dy, double dz) { return new Tr('t', 0, new V(dx, dy, dz)); }
        static Tr r(char axis, double deg, V origin) { return new Tr(axis, deg, origin); }

        V apply(V p, boolean vector, boolean inverse) {
            if (axis == 't') return vector ? p : inverse ? p.sub(v) : p.add(v);
            double a = Math.toRadians(inverse ? -deg : deg);
            double c = Math.cos(a), s = Math.sin(a);
            V o = vector ? ZERO : v;
            V q = p.sub(o);
            V r = switch (axis) {
                case 'x' -> new V(q.x, q.y * c - q.z * s, q.y * s + q.z * c);
                case 'y' -> new V(q.z * s + q.x * c, q.y, q.z * c - q.x * s);
                default -> new V(q.x * c - q.y * s, q.x * s + q.y * c, q.z);
            };
            return r.add(o);
        }
    }

    record Face(String name, V n, List<V> pts) {}

    private static final Map<String, int[][]> CORNERS = Map.of(
            "north", new int[][] {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}},
            "south", new int[][] {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
            "west", new int[][] {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
            "east", new int[][] {{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}},
            "down", new int[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            "up", new int[][] {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}});
    private static final Map<String, V> NORMAL = Map.of("up", Y, "down", new V(0, -1, 0), "north", new V(0, 0, -1),
            "south", new V(0, 0, 1), "east", X, "west", new V(-1, 0, 0));

    /** An element in model space (or a procedural part without volume when {@code from} is null). */
    static final class Solid {
        final String label;
        final V from, to;
        final List<Tr> trs;
        final List<String> faceNames;
        final List<Face> faces = new ArrayList<>();

        Solid(String label, V from, V to, List<Tr> trs, List<String> faceNames) {
            this.label = label;
            this.from = from;
            this.to = to;
            this.trs = trs;
            this.faceNames = faceNames;
            for (String name : faceNames) {
                List<V> pts = new ArrayList<>();
                for (int[] pick : CORNERS.get(name)) {
                    V p = new V(pick[0] == 0 ? from.x : to.x, pick[1] == 0 ? from.y : to.y, pick[2] == 0 ? from.z : to.z);
                    for (Tr t : trs) p = t.apply(p, false, false);
                    pts.add(p);
                }
                V n = NORMAL.get(name);
                for (Tr t : trs) n = t.apply(n, true, false);
                faces.add(new Face(name, n, pts));
            }
        }

        Solid(String label, List<Face> quads) {
            this.label = label;
            this.from = this.to = null;
            this.trs = List.of();
            this.faceNames = List.of();
            this.faces.addAll(quads);
        }

        Solid moved(String newLabel, List<Tr> more) {
            if (from == null) return this;
            List<Tr> all = new ArrayList<>(trs);
            all.addAll(more);
            return new Solid(newLabel, from, to, all, faceNames);
        }

        boolean contains(V p) {
            if (from == null) return false;
            for (int i = trs.size() - 1; i >= 0; i--) p = trs.get(i).apply(p, false, true);
            for (int i = 0; i < 3; i++) {
                if (p.get(i) < from.get(i) - 1e-4 || p.get(i) > to.get(i) + 1e-4) return false;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ models

    private static JsonObject load(String rl) throws IOException {
        if (!rl.startsWith("minetorio:")) return null;
        Path p = MODELS.resolve(rl.substring("minetorio:".length()) + ".json");
        if (!Files.exists(p)) return null;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    /** The model's elements, or those of the nearest parent that has any. */
    static List<Solid> solids(String rl, String label) throws IOException {
        String cur = rl;
        while (cur != null) {
            JsonObject j = load(cur);
            if (j == null) return List.of();
            if (j.has("elements")) {
                List<Solid> out = new ArrayList<>();
                JsonArray els = j.getAsJsonArray("elements");
                for (int i = 0; i < els.size(); i++) {
                    JsonObject el = els.get(i).getAsJsonObject();
                    List<Tr> trs = new ArrayList<>();
                    if (el.has("rotation")) {
                        JsonObject r = el.getAsJsonObject("rotation");
                        double angle = r.get("angle").getAsDouble();
                        if (angle != 0) trs.add(Tr.r(r.get("axis").getAsString().charAt(0), angle, vec(r.getAsJsonArray("origin"))));
                    }
                    List<String> faces = el.has("faces") ? new ArrayList<>(el.getAsJsonObject("faces").keySet()) : List.of();
                    out.add(new Solid(label + "#" + i, vec(el.getAsJsonArray("from")), vec(el.getAsJsonArray("to")), trs, faces));
                }
                return out;
            }
            JsonElement parent = j.get("parent");
            cur = parent == null ? null : parent.getAsString();
        }
        return List.of();
    }

    private static V vec(JsonArray a) {
        return new V(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    // ------------------------------------------------------------------ overlap

    private record P2(double x, double y) {}

    private static double area(List<P2> poly) {
        double s = 0;
        for (int i = 0; i < poly.size(); i++) {
            P2 a = poly.get(i), b = poly.get((i + 1) % poly.size());
            s += a.x * b.y - b.x * a.y;
        }
        return s / 2;
    }

    private static List<P2> to2d(List<V> pts, V u, V v) {
        List<P2> poly = new ArrayList<>();
        for (V p : pts) poly.add(new P2(p.dot(u), p.dot(v)));
        if (area(poly) < 0) java.util.Collections.reverse(poly);
        return poly;
    }

    /** Sutherland-Hodgman intersection of two convex counter-clockwise polygons. */
    private static List<P2> clip(List<P2> subject, List<P2> clipper) {
        List<P2> out = subject;
        for (int i = 0; i < clipper.size() && !out.isEmpty(); i++) {
            P2 a = clipper.get(i), b = clipper.get((i + 1) % clipper.size());
            List<P2> in = out;
            out = new ArrayList<>();
            for (int j = 0; j < in.size(); j++) {
                P2 p = in.get(j), q = in.get((j + 1) % in.size());
                double sp = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x);
                double sq = (b.x - a.x) * (q.y - a.y) - (b.y - a.y) * (q.x - a.x);
                if (sp >= 0) out.add(p);
                if ((sp >= 0) != (sq >= 0)) {
                    double k = sp / (sp - sq);
                    out.add(new P2(p.x + (q.x - p.x) * k, p.y + (q.y - p.y) * k));
                }
            }
        }
        return out;
    }

    /** The 3D overlap polygon of two coplanar same-facing faces, or null. */
    static List<V> overlap(Face a, Face b) {
        if (a.n.dot(b.n) < 1 - 1e-6) return null;
        double d = a.n.dot(a.pts.get(0));
        if (Math.abs(d - a.n.dot(b.pts.get(0))) > PLANE_EPS) return null;
        V u = a.n.cross(Math.abs(a.n.x) < 0.9 ? X : Y).norm();
        V v = a.n.cross(u);
        List<P2> poly = clip(to2d(a.pts, u, v), to2d(b.pts, u, v));
        if (poly.size() < 3 || area(poly) <= AREA_EPS) return null;
        List<V> pts = new ArrayList<>();
        for (P2 p : poly) pts.add(u.mul(p.x).add(v.mul(p.y)).add(a.n.mul(d)));
        return pts;
    }

    /** The space just in front of the overlap is filled by one other element. */
    private static boolean covered(List<V> pts, V n, List<Solid> others) {
        List<V> probe = new ArrayList<>(pts);
        probe.add(pts.get(0).add(pts.get(1)).add(pts.get(2)).mul(1 / 3.0));
        for (Solid s : others) {
            if (probe.stream().allMatch(p -> s.contains(p.add(n.mul(0.005))))) return true;
        }
        return false;
    }

    record Finding(String a, String b, V n) {}

    /** Findings between solids of ga and gb; pass the same list twice for pairs within one model. */
    static List<Finding> pairs(List<Solid> ga, List<Solid> gb, List<Solid> all) {
        List<Finding> found = new ArrayList<>();
        boolean same = ga == gb;
        for (int i = 0; i < ga.size(); i++) {
            for (int j = same ? i + 1 : 0; j < gb.size(); j++) {
                Solid sa = ga.get(i), sb = gb.get(j);
                for (Face fa : sa.faces) {
                    for (Face fb : sb.faces) {
                        List<V> o = overlap(fa, fb);
                        if (o == null) continue;
                        List<Solid> others = all.stream().filter(s -> s != sa && s != sb).toList();
                        if (covered(o, fa.n, others)) continue;
                        found.add(new Finding(sa.label + "." + fa.name, sb.label + "." + fb.name, fa.n));
                    }
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ composites

    /** A part: a model (or procedural solids) in sampled poses; a turning part names its spin axis. */
    record Part(String label, String model, List<List<Tr>> poses, V spin, Supplier<List<Solid>> procedural) {
        static Part fixed(String label, String model, Tr... trs) {
            return new Part(label, model, List.of(List.of(trs)), null, null);
        }

        List<List<Solid>> expand() throws IOException {
            if (procedural != null) return procedural.get().stream().map(List::of).toList();
            List<Solid> base = solids("minetorio:block/" + model, label);
            List<List<Solid>> out = new ArrayList<>();
            for (List<Tr> pose : poses) out.add(pose.isEmpty() ? base : base.stream().map(s -> s.moved(s.label, pose)).toList());
            return out;
        }
    }

    record Composite(String name, List<Part> parts) {}

    private static final double[] SPIN = {0, 11.25, 22.5, 33.75, 45, 67.5};

    private static Part spinning(String label, String model, char axis, V origin, Tr... after) {
        List<List<Tr>> poses = new ArrayList<>();
        for (double a : SPIN) {
            List<Tr> pose = new ArrayList<>();
            if (a != 0) pose.add(Tr.r(axis, a, origin));
            pose.addAll(List.of(after));
            poses.add(pose);
        }
        return new Part(label, model, poses, axis == 'x' ? X : axis == 'z' ? Z : Y, null);
    }

    /** A part moved by each of the given translations (the first one is its rest pose). */
    private static Part sliding(String label, String model, Tr... moves) {
        List<List<Tr>> poses = new ArrayList<>();
        for (Tr m : moves) poses.add(m.v().equals(ZERO) ? List.of() : List.of(m));
        return new Part(label, model, poses, null, null);
    }

    /** Joint, arm and hand boxes of InserterRenderer.render (lines 97-111, claw fingers left out), in pixels, facing north. */
    static Solid inserterArm(int reach, double arm, double handHalf) {
        double a = Math.PI * arm;
        double c = -Math.cos(a), s = Math.sin(a);
        double hx = s, hz = -c; // facing north (0, -1), right = east (1, 0)
        double radius = 0.85 * reach, shoulderY = 5.5 / 16.0;
        V shoulder = new V(0.5, shoulderY, 0.5);
        V elbow = new V(0.5 + hx * radius * 0.1, shoulderY + 0.40 + 0.06 * reach, 0.5 + hz * radius * 0.1);
        V hand = new V(0.5 + hx * radius, shoulderY + 0.2, 0.5 + hz * radius);
        V side = new V(-hz, 0, hx);
        List<Face> quads = new ArrayList<>();
        Object[][] boxes = {{shoulder, shoulder.add(new V(0, 0.1, 0)), 0.065},
            {shoulder.add(new V(0, 0.05, 0)), elbow, 0.035},
            {elbow.sub(new V(0, 0.045, 0)), elbow.add(new V(0, 0.045, 0)), 0.045},
            {elbow, hand.add(new V(0, 0.03, 0)), 0.024},
            {hand.sub(new V(0, 0.06, 0)), hand.add(new V(0, 0.03, 0)), handHalf}};
        for (Object[] box : boxes) {
            V a0 = (V) box[0], b0 = (V) box[1];
            double half = (double) box[2];
            V u = b0.sub(a0).norm();
            V sv = side.norm();
            V vv = u.cross(sv).norm();
            sv = vv.cross(u).norm();
            V[] k = new V[8];
            for (int i = 0; i < 8; i++) {
                V base = (i & 4) != 0 ? b0 : a0;
                base = base.add(sv.mul((i & 2) != 0 ? half : -half)).add(vv.mul((i & 1) != 0 ? half : -half));
                k[i] = base.mul(16);
            }
            int[][] idx = {{2, 3, 7, 6}, {0, 4, 5, 1}, {1, 5, 7, 3}, {0, 2, 6, 4}, {4, 6, 7, 5}, {0, 1, 3, 2}};
            V[] ns = {sv, sv.mul(-1), vv, vv.mul(-1), u, u.mul(-1)};
            for (int q = 0; q < 6; q++) {
                quads.add(new Face("q" + (quads.size()), ns[q], List.of(k[idx[q][0]], k[idx[q][1]], k[idx[q][2]], k[idx[q][3]])));
            }
        }
        return new Solid("inserter_arm(reach=" + reach + ",arm=" + arm + ")", quads);
    }

    /** Same table as COMPOSITES in art/check_zfight.py. */
    static List<Composite> composites() {
        V center = new V(8, 0, 8);
        List<Composite> c = new ArrayList<>();
        // AssemblerRenderer.assemble: every part in model px. Cogs and gears turn about z through their axes, gear A
        // is drawn again 15 px east, the push rod slides -1.5..1.5 px in z, the carriage -7..7 px in x, arm and head
        // turn about the turret axis and the head drops up to 2 px, the fan turns; the lit lamp lens is fixed.
        for (int t = 1; t <= 2; t++) {
            List<Part> asm = new ArrayList<>(List.of(Part.fixed("body", "assembling_machine_" + t + "_body"),
                    spinning("cog", "assembling_machine_cog", 'z', new V(0, 25, 0)),
                    spinning("cog_b", "assembling_machine_cog_b", 'z', new V(10.5, 25, 0)),
                    Part.fixed("lamp", "assembling_machine_lamp")));
            if (t == 1) {
                asm.add(spinning("gear", "assembling_machine_gear", 'z', new V(9, 24.5, 0)));
                asm.add(spinning("gear_b", "assembling_machine_gear_b", 'z', new V(16.5, 24.5, 0)));
                asm.add(spinning("gear@+15", "assembling_machine_gear", 'z', new V(9, 24.5, 0), Tr.t(15, 0, 0)));
                asm.add(sliding("rod", "assembling_machine_rod", Tr.t(0, 0, 0), Tr.t(0, 0, -1.5), Tr.t(0, 0, -0.75),
                        Tr.t(0, 0, 0.75), Tr.t(0, 0, 1.5)));
                asm.add(sliding("slider", "assembling_machine_slider", Tr.t(0, 0, 0), Tr.t(-7, 0, 0), Tr.t(-3.5, 0, 0),
                        Tr.t(3.5, 0, 0), Tr.t(7, 0, 0)));
            } else {
                V turret = new V(10, 0, 10);
                asm.add(spinning("arm", "assembling_machine_arm", 'y', turret));
                List<List<Tr>> head = new ArrayList<>();
                for (double a : SPIN) {
                    for (int dy = 0; dy <= 2; dy++) {
                        List<Tr> pose = new ArrayList<>();
                        if (a != 0) pose.add(Tr.r('y', a, turret));
                        if (dy != 0) pose.add(Tr.t(0, -dy, 0));
                        head.add(pose);
                    }
                }
                asm.add(new Part("head", "assembling_machine_head", head, Y, null));
                asm.add(spinning("fan", "assembling_machine_fan", 'y', new V(20, 0, 20)));
            }
            c.add(new Composite("assembling_machine_" + t, asm));
        }
        // DrillRenderer: electric body and turning head at the center, the carriage riding 0..4 px up, the tower one
        // block up.
        List<List<Tr>> carriage = new ArrayList<>();
        for (int dy = 0; dy <= 4; dy++) carriage.add(dy == 0 ? List.of() : List.of(Tr.t(0, dy, 0)));
        c.add(new Composite("electric_mining_drill", List.of(Part.fixed("body", "electric_mining_drill_body"),
                spinning("head", "electric_mining_drill_head", 'y', center),
                new Part("carriage", "electric_mining_drill_carriage", carriage, null, null),
                Part.fixed("tower", "electric_mining_drill_tower", Tr.t(0, 16, 0)))));
        // Burner drill: body on the controller's blockstate; DrillRenderer puts the head at (+8, 0, -8) px.
        c.add(new Composite("burner_mining_drill", List.of(Part.fixed("body", "burner_mining_drill"),
                spinning("head", "burner_mining_drill_head", 'y', center, Tr.t(8, 0, -8)))));
        // LabRenderer: body and turning dome (glowing or idle).
        for (String dome : List.of("lab_dome", "lab_dome_idle")) {
            c.add(new Composite("lab+" + dome, List.of(Part.fixed("body", "lab_body"), spinning("dome", dome, 'y', center))));
        }
        // RadarRenderer: base at the center, the dish one block up turning about the vertical center axis.
        c.add(new Composite("radar", List.of(Part.fixed("base", "radar_base"),
                spinning("dish", "radar_dish", 'y', center, Tr.t(0, 16, 0)))));
        // RoboportRenderer: the eight pieces, each translated by its offset (piece px = machine px - offset).
        List<Part> roboport = new ArrayList<>();
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++)
                for (int k = 0; k < 2; k++)
                    roboport.add(Part.fixed("p" + i + j + k, "roboport_" + i + j + k, Tr.t(-16 + 16 * i, 40 * k, -16 + 16 * j)));
        // ...and its moving and lit parts (RoboportRenderer.assemble), each about a pivot at model (8, 0, 8): the 8 iris
        // blades (door 0 / 0.5 / 1), the antenna head turning on its mast, the lit charging points and status lamps.
        for (int k = 0; k < 8; k++) {
            List<List<Tr>> poses = new ArrayList<>();
            for (double d : new double[] {0, 0.5, 1}) {
                poses.add(List.of(Tr.t(8.3 * d, 0, 0), Tr.r('y', 45 * k + 60 * d, center), Tr.t(-8, 52.2 + 0.1 * k, -7)));
            }
            roboport.add(new Part("blade" + k, "roboport_blade", poses, Y, null));
        }
        roboport.add(spinning("antenna", "roboport_antenna", 'y', center, Tr.t(6, 58, 13)));
        int n = 0;
        for (int[] s : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            roboport.add(Part.fixed("charge" + n, "roboport_charge_glow", Tr.t(27.5 * s[0] - 8, 60, 27.5 * s[1] - 8)));
            roboport.add(Part.fixed("lampfoot" + n++, "roboport_lamp_green", Tr.t(31.25 * s[0] - 8, 7, 31.25 * s[1] - 8)));
        }
        for (double[] l : new double[][] {{-16, 17, -28.5}, {-6, 17, -28.5}, {24.5, 17, -9}, {-24.5, 17, 9}}) {
            roboport.add(Part.fixed("lamp" + n++, "roboport_lamp_green", Tr.t(l[0] - 8, l[1], l[2] - 8)));
        }
        c.add(new Composite("roboport", roboport));
        // ConstructionRobotRenderer: the body and its fan rotor turning about the vertical axis x = z = 8.
        c.add(new Composite("construction_robot", List.of(Part.fixed("body", "construction_robot"),
                spinning("rotor", "construction_robot_rotor", 'y', center))));
        // SteamMachineRenderer.Boiler: boiler body and fire.
        c.add(new Composite("boiler", List.of(Part.fixed("body", "boiler_body"), Part.fixed("fire", "boiler_fire"))));
        // SteamMachineRenderer.Engine: body at dz 0, front at -1.5 blocks, rear at +1.5, gears turning about x through
        // their models' (y 8, z 8), drawn at their pivots (Engine.GEARS).
        List<Part> engine = new ArrayList<>(List.of(Part.fixed("body", "steam_engine_body"),
                Part.fixed("front", "steam_engine_front", Tr.t(0, 0, -24)),
                Part.fixed("rear", "steam_engine_rear", Tr.t(0, 0, 24))));
        for (Object[] g : new Object[][] {{"steam_engine_gear", 15.0, 10.0}, {"steam_engine_gear_small", 12.0, -6.0},
            {"steam_engine_gear_small", 21.0, 25.0}}) {
            double py = (double) g[1], pz = (double) g[2];
            engine.add(spinning("gear@" + py + "," + pz, (String) g[0], 'x', new V(0, 8, 8), Tr.t(0, py - 8, pz - 8)));
        }
        c.add(new Composite("steam_engine", engine));
        // blockstates/small_electric_pole.json: the middle and top parts' models on the blocks above the lower part's.
        c.add(new Composite("small_electric_pole", List.of(Part.fixed("lower", "small_electric_pole"),
                Part.fixed("middle", "small_electric_pole_middle", Tr.t(0, 16, 0)),
                Part.fixed("top", "small_electric_pole_top", Tr.t(0, 32, 0)))));
        // InserterRenderer.java:97-111: procedural arm over the base.
        // the stack inserter's hand is wider (InserterRenderer.STACK_HAND_HALF)
        for (Object[] ins : new Object[][] {{"burner_inserter", 1, 0.05}, {"inserter", 1, 0.05}, {"long_handed_inserter", 2, 0.05},
                {"fast_inserter", 1, 0.05}, {"filter_inserter", 1, 0.05}, {"stack_inserter", 1, 0.085}}) {
            int reach = (int) ins[1];
            double handHalf = (double) ins[2];
            Supplier<List<Solid>> arms = () -> Stream.of(0.0, 0.25, 0.5, 0.75, 1.0).map(a -> inserterArm(reach, a, handHalf)).toList();
            c.add(new Composite(ins[0] + "+arm", List.of(Part.fixed("base", (String) ins[0]), new Part("arm", null, null, null, arms))));
        }
        // blockstates/pipe.json: core + four sides (y 0/90/180/270, clockwise about the block center) + up + down.
        List<Part> pipe = new ArrayList<>(List.of(Part.fixed("core", "pipe_core")));
        for (int y : new int[] {0, 90, 180, 270}) {
            pipe.add(y == 0 ? Part.fixed("side0", "pipe_side") : Part.fixed("side" + y, "pipe_side", Tr.r('y', -y, new V(8, 8, 8))));
        }
        pipe.add(Part.fixed("up", "pipe_up"));
        pipe.add(Part.fixed("down", "pipe_down"));
        c.add(new Composite("pipe(all arms)", pipe));
        // pipe.json with closed sides (art/check_zfight.py): caps on the sides + arms up/down, arms on the sides + caps up/down.
        V mid = new V(8, 8, 8);
        List<Part> sideCaps = new ArrayList<>(List.of(Part.fixed("core", "pipe_core")));
        for (int y : new int[] {0, 90, 180, 270}) {
            sideCaps.add(y == 0 ? Part.fixed("cap0", "pipe_cap") : Part.fixed("cap" + y, "pipe_cap", Tr.r('y', -y, mid)));
        }
        sideCaps.add(Part.fixed("up", "pipe_up"));
        sideCaps.add(Part.fixed("down", "pipe_down"));
        c.add(new Composite("pipe(side caps)", sideCaps));
        List<Part> vertCaps = new ArrayList<>(List.of(Part.fixed("core", "pipe_core")));
        for (int y : new int[] {0, 90, 180, 270}) {
            vertCaps.add(y == 0 ? Part.fixed("side0", "pipe_side") : Part.fixed("side" + y, "pipe_side", Tr.r('y', -y, mid)));
        }
        vertCaps.add(Part.fixed("capup", "pipe_cap", Tr.r('x', -270, mid)));
        vertCaps.add(Part.fixed("capdown", "pipe_cap", Tr.r('x', -90, mid)));
        c.add(new Composite("pipe(vertical caps)", vertCaps));
        // Fluid neighbours: a pipe at an underground pipe's open end, pipes at the tank's front-right connections
        // (StorageTankRenderer: body around the center block), pipes at both ends of the pump.
        c.add(new Composite("pipe_to_ground+pipe", List.of(Part.fixed("ug", "pipe_to_ground"),
                Part.fixed("core", "pipe_core", Tr.t(0, 0, -16)),
                Part.fixed("arm", "pipe_side", Tr.r('y', -180, mid), Tr.t(0, 0, -16)))));
        c.add(new Composite("storage_tank+pipes", List.of(Part.fixed("tank", "storage_tank_body"),
                Part.fixed("core_n", "pipe_core", Tr.t(16, 0, -32)),
                Part.fixed("arm_n", "pipe_side", Tr.r('y', -180, mid), Tr.t(16, 0, -32)),
                Part.fixed("core_e", "pipe_core", Tr.t(32, 0, -16)),
                Part.fixed("arm_e", "pipe_side", Tr.r('y', -270, mid), Tr.t(32, 0, -16)))));
        c.add(new Composite("pump+pipes", List.of(Part.fixed("pump", "pump"),
                Part.fixed("core_n", "pipe_core", Tr.t(0, 0, -32)),
                Part.fixed("arm_n", "pipe_side", Tr.r('y', -180, mid), Tr.t(0, 0, -32)),
                Part.fixed("core_s", "pipe_core", Tr.t(0, 0, 16)),
                Part.fixed("arm_s", "pipe_side", Tr.t(0, 0, 16)))));
        // Neighbours.
        for (String pre : List.of("", "fast_", "express_")) {
            c.add(new Composite(pre + "underground_belt_input+belt", List.of(Part.fixed("ug", pre + "underground_belt_input"),
                    Part.fixed("belt", pre + "transport_belt", Tr.t(0, 0, 16)))));
            c.add(new Composite(pre + "underground_belt_output+belt", List.of(Part.fixed("ug", pre + "underground_belt_output"),
                    Part.fixed("belt", pre + "transport_belt", Tr.t(0, 0, -16)))));
        }
        c.add(new Composite("belt+belt", List.of(Part.fixed("a", "transport_belt"), Part.fixed("b", "transport_belt", Tr.t(0, 0, -16)))));
        c.add(new Composite("splitter+belts", List.of(Part.fixed("splitter", "splitter"),
                Part.fixed("belt0", "transport_belt", Tr.t(0, 0, 16)), Part.fixed("belt1", "transport_belt", Tr.t(16, 0, 16)))));
        c.add(new Composite("inserter+belt", List.of(Part.fixed("inserter", "inserter"), Part.fixed("belt", "transport_belt", Tr.t(0, 0, -16)))));
        // Belt ramps (R25, north-facing, travel -z): flat belt at the low end, flat belt at the high end, the
        // next ramp of a chain, a flat belt beside the ramp.
        String up = "transport_belt_ascending";
        String down = "transport_belt_descending";
        c.add(new Composite("ramp_up+flat_bottom", List.of(Part.fixed("ramp", up), Part.fixed("flat", "transport_belt", Tr.t(0, 0, 16)))));
        c.add(new Composite("ramp_up+flat_top", List.of(Part.fixed("ramp", up), Part.fixed("flat", "transport_belt", Tr.t(0, 16, -16)))));
        c.add(new Composite("ramp_up+ramp_up", List.of(Part.fixed("a", up), Part.fixed("b", up, Tr.t(0, 16, -16)))));
        c.add(new Composite("ramp_up+flat_beside", List.of(Part.fixed("ramp", up), Part.fixed("flat", "transport_belt", Tr.t(16, 0, 0)))));
        c.add(new Composite("ramp_down+flat_top", List.of(Part.fixed("ramp", down), Part.fixed("flat", "transport_belt", Tr.t(0, 16, 16)))));
        c.add(new Composite("ramp_down+flat_bottom", List.of(Part.fixed("ramp", down), Part.fixed("flat", "transport_belt", Tr.t(0, 0, -16)))));
        c.add(new Composite("ramp_down+ramp_down", List.of(Part.fixed("a", down), Part.fixed("b", down, Tr.t(0, -16, -16)))));
        c.add(new Composite("ramp_down+flat_beside", List.of(Part.fixed("ramp", down), Part.fixed("flat", "transport_belt", Tr.t(16, 0, 0)))));
        return c;
    }

    static List<String> checkComposite(Composite comp) throws IOException {
        List<List<List<Solid>>> expanded = new ArrayList<>();
        for (Part p : comp.parts) expanded.add(p.expand());
        Map<String, String> found = new LinkedHashMap<>();
        for (int i = 0; i < expanded.size(); i++) {
            for (int j = i + 1; j < expanded.size(); j++) {
                for (int ia = 0; ia < expanded.get(i).size(); ia++) {
                    for (int ib = 0; ib < expanded.get(j).size(); ib++) {
                        List<V> axes = new ArrayList<>();
                        if (ia > 0 && comp.parts.get(i).spin != null) axes.add(comp.parts.get(i).spin);
                        if (ib > 0 && comp.parts.get(j).spin != null) axes.add(comp.parts.get(j).spin);
                        List<Solid> a = expanded.get(i).get(ia), b = expanded.get(j).get(ib);
                        List<Solid> all = new ArrayList<>(a);
                        all.addAll(b);
                        for (int k = 0; k < expanded.size(); k++) if (k != i && k != j) all.addAll(expanded.get(k).get(0));
                        for (Finding f : pairs(a, b, all)) {
                            if (axes.stream().allMatch(ax -> Math.abs(f.n.dot(ax)) > 1 - 1e-6)) {
                                found.putIfAbsent(f.a + " / " + f.b, comp.name + ": " + f.a + " / " + f.b + " normal " + f.n);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    // ------------------------------------------------------------------ tests

    @Test
    void detectsOverlappingCoplanarFaces() {
        Solid a = new Solid("a", new V(0, 0, 0), new V(4, 2, 4), List.of(), List.of("up", "down"));
        Solid b = new Solid("b", new V(2, 0, 2), new V(6, 2, 6), List.of(), List.of("up"));
        Solid turned = new Solid("t", new V(0, 0, 0), new V(4, 2, 4), List.of(Tr.r('y', 45, new V(2, 0, 2))), List.of("up"));
        Solid lid = new Solid("lid", new V(-2, 2, -2), new V(8, 3, 8), List.of(), List.of());
        Solid lower = new Solid("l", new V(2, 0, 2), new V(6, 1.98, 6), List.of(), List.of("up"));
        List<Solid> ab = List.of(a, b);
        List<Solid> at = List.of(a, turned);
        List<Solid> al = List.of(a, lower);
        assertEquals(1, pairs(ab, ab, ab).size(), "flush tops overlap");
        assertEquals(1, pairs(at, at, at).size(), "turned copy shares the top");
        assertEquals(0, pairs(ab, ab, List.of(a, b, lid)).size(), "a lid covers the overlap");
        assertEquals(0, pairs(al, al, al).size(), "0.02 px apart is fine");
    }

    @Test
    void noModelHasZFightingFaces() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String kind : List.of("block", "item")) {
            try (Stream<Path> files = Files.list(MODELS.resolve(kind))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    String name = kind + "/" + f.getFileName().toString().replace(".json", "");
                    List<Solid> s = solids("minetorio:" + name, "");
                    for (Finding x : pairs(s, s, s)) problems.add(name + ": " + x.a + " / " + x.b + " normal " + x.n);
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " z-fighting face pairs (python -I art/check_zfight.py):\n"
                + String.join("\n", problems));
    }

    @Test
    void partsDrawnTogetherDoNotZFight() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Composite c : composites()) problems.addAll(checkComposite(c));
        assertTrue(problems.isEmpty(), problems.size() + " z-fighting face pairs (python -I art/check_zfight.py):\n"
                + String.join("\n", problems));
    }

    @Test
    void compositeCheckSeesTheRenderersOffsets() throws IOException {
        // the lab body drawn twice in the same place must be reported
        Composite twice = new Composite("lab twice", List.of(Part.fixed("a", "lab_body"), Part.fixed("b", "lab_body")));
        assertFalse(checkComposite(twice).isEmpty());
    }
}
