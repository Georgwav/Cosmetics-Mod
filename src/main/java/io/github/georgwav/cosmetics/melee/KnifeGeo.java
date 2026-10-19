package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A knife's model from a gunpack's Bedrock geometry ({@code geo_models/melee/*_geo.json}), built the way TACZ and
 * Blockbench show it, since the packs were tuned there.
 *
 * <p>Everything is in "knife space": Bedrock's axes with X mirrored (Blockbench's own space), in pixels, Y up, the
 * camera looking toward -Z. A bone's bind rotation {@code [x, y, z]} becomes {@code (-x, -y, z)} radians, applied Z,
 * then Y, then X onto a point ({@link #rotation}).
 *
 * <p>Each cube face gets its picture by Georg's face table (Bedrock coordinates, a 2×3×4 cube at the origin; the
 * rectangle's corners named by u, left = the smaller, and v, top = the smaller):
 * <pre>
 *   up    (y=3) left-top (0,3,4)  right-top (2,3,4)  left-bottom (0,3,0)  right-bottom (2,3,0)
 *   down  (y=0) left-top (0,0,0)  right-top (2,0,0)  left-bottom (0,0,4)  right-bottom (2,0,4)
 *   east  (x=0) left-top (0,3,4)  right-top (0,3,0)  left-bottom (0,0,4)  right-bottom (0,0,0)
 *   west  (x=2) left-top (2,3,0)  right-top (2,3,4)  left-bottom (2,0,0)  right-bottom (2,0,4)
 *   north (z=0) left-top (0,3,0)  right-top (2,3,0)  left-bottom (0,0,0)  right-bottom (2,0,0)
 *   south (z=4) left-top (2,3,4)  right-top (0,3,4)  left-bottom (2,0,4)  right-bottom (0,0,4)
 * </pre>
 * A face's rectangle is {@code uv} to {@code uv + uv_size} (a negative size flips it), divided by the size the file
 * declares ({@code texture_width}, {@code texture_height}), not the picture's. {@code uv_rotation} is ignored, as TACZ
 * does.
 *
 * <p>Which bones draw how: a bone named {@code *illuminated*} (or under one) draws at full light; {@code 1p_effect*},
 * {@code effect*} and {@code glint*} (or under one) are slash trails and glints, shown only while an animation scales
 * them up; {@code *_pos} bones are where your arms go, their own cubes (the animators' placeholder arm) never drawn;
 * the camera and view markers draw nothing. Pure Java.
 */
public final class KnifeGeo {
    /** Bones that only mark a place. */
    private static final java.util.Set<String> MARKERS = java.util.Set.of("camera", "view", "idle_view", "iron_view", "refit_view",
            "positioning", "ground", "fixed", "thirdperson_hand", "constraint");

    /** Floats per quad: four corners of (x, y, z, u, v), then the normal (x, y, z). */
    public static final int QUAD = 4 * 5 + 3;

    public final int textureWidth, textureHeight;
    public final Bone[] bones;
    private final Map<String, Integer> index = new HashMap<>();

    /** One bone: where it turns, its bind rotation, what it is, and its faces relative to its pivot. */
    public static final class Bone {
        public final String name;
        /** Its parent's index in {@link #bones}, or -1; parents come before children. */
        public final int parent;
        /** Pivot in knife space (pixels). */
        public final float px, py, pz;
        /** Bind rotation, radians, in knife space's convention (see the class notes). */
        public final float rx, ry, rz;
        /** Draws at full light. */
        public final boolean lit;
        /** A slash trail or glint: shown only while an animation scales it above 0. */
        public final boolean effect;
        /** A slash trail ({@code 1p_effect*}, {@code effect*}, or under one), as against a glint: marks a strike's impact. */
        public final boolean slash;
        /** Where an arm goes: 1 the right, -1 the left, 0 neither. */
        public final int arm;
        /** Only marks a place. */
        public final boolean marker;
        /** {@link #QUAD} floats a face, corners relative to the pivot. Empty for bones that draw nothing. */
        public final float[] quads;
        /** Each cube's volume and centre relative to the pivot (for finding the grip), four floats a cube. */
        public final float[] cubes;

        Bone(String name, int parent, float[] pivot, float[] rotation, boolean lit, boolean effect, boolean slash, int arm,
             boolean marker, float[] quads, float[] cubes) {
            this.name = name;
            this.parent = parent;
            this.px = pivot[0];
            this.py = pivot[1];
            this.pz = pivot[2];
            this.rx = rotation[0];
            this.ry = rotation[1];
            this.rz = rotation[2];
            this.lit = lit;
            this.effect = effect;
            this.slash = slash;
            this.arm = arm;
            this.marker = marker;
            this.quads = quads;
            this.cubes = cubes;
        }
    }

    private KnifeGeo(int textureWidth, int textureHeight, Bone[] bones) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.bones = bones;
        for (int i = 0; i < bones.length; i++) {
            index.putIfAbsent(bones[i].name, i);
        }
    }

    /** A bone's index, or -1. */
    public int bone(String name) {
        Integer i = index.get(name);
        return i == null ? -1 : i;
    }

    /** Reads a {@code .geo.json} (its first geometry). */
    public static KnifeGeo parse(JsonObject json, String where) {
        JsonArray geometries = json.has("minecraft:geometry") ? json.getAsJsonArray("minecraft:geometry") : null;
        if (geometries == null || geometries.isEmpty()) {
            throw new JsonParseException(where + ": no minecraft:geometry");
        }
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description") : new JsonObject();
        int texW = description.has("texture_width") ? description.get("texture_width").getAsInt() : 64;
        int texH = description.has("texture_height") ? description.get("texture_height").getAsInt() : 64;
        if (texW <= 0 || texH <= 0) {
            throw new JsonParseException(where + ": texture_width and texture_height must be positive");
        }
        JsonArray bonesJson = geometry.has("bones") ? geometry.getAsJsonArray("bones") : new JsonArray();
        // Parents before children, whatever order the file lists them in.
        List<JsonObject> raw = new ArrayList<>();
        for (JsonElement e : bonesJson) {
            raw.add(e.getAsJsonObject());
        }
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonObject b : raw) {
            byName.putIfAbsent(name(b), b);
        }
        List<JsonObject> ordered = new ArrayList<>();
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (JsonObject b : raw) {
            place(b, byName, placed, ordered, 0);
        }
        Map<String, Integer> at = new HashMap<>();
        Bone[] bones = new Bone[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            JsonObject b = ordered.get(i);
            String name = name(b);
            String parentName = b.has("parent") ? b.get("parent").getAsString() : null;
            int parent = parentName != null && at.containsKey(parentName) ? at.get(parentName) : -1;
            float[] pivot = mirror(floats(b.get("pivot"), 3, new float[3]));
            float[] rot = floats(b.get("rotation"), 3, new float[3]);
            float[] rotation = rotation(rot);
            boolean lit = name.toLowerCase(Locale.ROOT).contains("illuminated") || parent >= 0 && bones[parent].lit;
            boolean effect = isEffect(name) || parent >= 0 && bones[parent].effect;
            boolean slash = isSlash(name) || parent >= 0 && bones[parent].slash;
            String lower = name.toLowerCase(Locale.ROOT);
            int arm = lower.endsWith("_pos") ? lower.contains("left") ? -1 : 1 : 0;
            boolean marker = MARKERS.contains(lower);
            List<float[]> quads = new ArrayList<>();
            List<float[]> cubes = new ArrayList<>();
            if (arm == 0 && !marker && b.has("cubes")) {
                for (JsonElement c : b.getAsJsonArray("cubes")) {
                    cube(c.getAsJsonObject(), pivot, texW, texH, quads, cubes, where + "." + name);
                }
            }
            float[] q = new float[quads.size() * QUAD];
            for (int k = 0; k < quads.size(); k++) {
                System.arraycopy(quads.get(k), 0, q, k * QUAD, QUAD);
            }
            float[] cc = new float[cubes.size() * 4];
            for (int k = 0; k < cubes.size(); k++) {
                System.arraycopy(cubes.get(k), 0, cc, k * 4, 4);
            }
            bones[i] = new Bone(name, parent, pivot, rotation, lit, effect, slash, arm, marker, q, cc);
            at.putIfAbsent(name, i);
        }
        return new KnifeGeo(texW, texH, bones);
    }

    private static void place(JsonObject b, Map<String, JsonObject> byName, java.util.Set<String> placed, List<JsonObject> out, int depth) {
        String name = name(b);
        if (placed.contains(name) || depth > 256) {
            return;
        }
        if (b.has("parent")) {
            JsonObject parent = byName.get(b.get("parent").getAsString());
            if (parent != null && parent != b) {
                place(parent, byName, placed, out, depth + 1);
            }
        }
        if (placed.add(name)) {
            out.add(b);
        }
    }

    private static String name(JsonObject b) {
        return b.has("name") ? b.get("name").getAsString() : "";
    }

    /** {@code 1p_effect*}, {@code effect*} or {@code glint*}. */
    static boolean isEffect(String name) {
        return isSlash(name) || name.toLowerCase(Locale.ROOT).startsWith("glint");
    }

    /** {@code 1p_effect*} or {@code effect*}: a slash trail. */
    static boolean isSlash(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("1p_effect") || n.startsWith("effect");
    }

    /** A Bedrock rotation {@code [x, y, z]} in degrees as knife space's radians. */
    static float[] rotation(float[] degrees) {
        float k = (float) (Math.PI / 180.0);
        return new float[]{-degrees[0] * k, -degrees[1] * k, degrees[2] * k};
    }

    /** A Bedrock point in knife space. */
    static float[] mirror(float[] p) {
        return new float[]{-p[0], p[1], p[2]};
    }

    // ------------------------------------------------------------------ cubes

    private static final String[] FACES = {"up", "down", "east", "west", "north", "south"};

    /**
     * One cube's faces: corners by the face table in Bedrock coordinates, mirrored into knife space, turned about the
     * cube's pivot, made relative to the bone's pivot.
     */
    private static void cube(JsonObject c, float[] bonePivot, int texW, int texH, List<float[]> quads, List<float[]> cubes, String where) {
        float[] origin = floats(c.get("origin"), 3, new float[3]);
        float[] size = floats(c.get("size"), 3, new float[3]);
        float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0f;
        float x0 = origin[0] - inflate, y0 = origin[1] - inflate, z0 = origin[2] - inflate;
        float x1 = origin[0] + size[0] + inflate, y1 = origin[1] + size[1] + inflate, z1 = origin[2] + size[2] + inflate;
        float[][] rects = new float[6][];
        JsonElement uv = c.get("uv");
        if (uv != null && uv.isJsonObject()) {
            JsonObject faces = uv.getAsJsonObject();
            for (int f = 0; f < 6; f++) {
                JsonObject face = faces.has(FACES[f]) ? faces.getAsJsonObject(FACES[f]) : null;
                if (face == null || !face.has("uv")) {
                    continue;
                }
                float[] start = floats(face.get("uv"), 2, new float[2]);
                float[] extent = floats(face.get("uv_size"), 2, new float[2]);
                rects[f] = new float[]{start[0], start[1], extent[0], extent[1]};
            }
        } else {
            // Box UV (Blockbench's Bedrock layout): east, north, west, south along the middle strip, up and down above.
            float[] at = uv != null && uv.isJsonArray() ? floats(uv, 2, new float[2]) : new float[2];
            float dx = (float) Math.floor(size[0]), dy = (float) Math.floor(size[1]), dz = (float) Math.floor(size[2]);
            float u = at[0], v = at[1];
            rects[0] = new float[]{u + dz, v, dx, dz};
            rects[1] = new float[]{u + dz + dx, v + dz, dx, -dz};
            rects[2] = new float[]{u, v + dz, dz, dy};
            rects[3] = new float[]{u + dz + dx, v + dz, dz, dy};
            rects[4] = new float[]{u + dz, v + dz, dx, dy};
            rects[5] = new float[]{u + dz * 2 + dx, v + dz, dx, dy};
        }
        // The cube's own turn about its pivot (knife space).
        float[] rot = c.has("rotation") ? rotation(floats(c.get("rotation"), 3, new float[3])) : null;
        float[] piv = mirror(floats(c.get("pivot"), 3, new float[3]));
        float[] m = rot != null ? rotation(rot[0], rot[1], rot[2], new float[9]) : null;
        float vol = Math.max(Math.abs(size[0] * size[1] * size[2]), 0.01f);
        float[] ctr = {-(origin[0] + size[0] / 2f), origin[1] + size[1] / 2f, origin[2] + size[2] / 2f};
        if (m != null) {
            ctr = turn(m, piv, ctr);
        }
        cubes.add(new float[]{vol, ctr[0] - bonePivot[0], ctr[1] - bonePivot[1], ctr[2] - bonePivot[2]});
        for (int f = 0; f < 6; f++) {
            if (rects[f] == null) {
                continue;
            }
            float[][] corners = corners(f, x0, y0, z0, x1, y1, z1);
            float[] r = rects[f];
            float u0 = r[0] / texW, v0 = r[1] / texH, u1 = (r[0] + r[2]) / texW, v1 = (r[1] + r[3]) / texH;
            float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            // Corners in the order left-top, right-top, right-bottom, left-bottom (a loop round the face).
            float[] q = new float[QUAD];
            float[][] p = new float[4][];
            int[] order = {0, 1, 3, 2};
            for (int k = 0; k < 4; k++) {
                float[] pt = mirror(corners[order[k]]);
                if (m != null) {
                    pt = turn(m, piv, pt);
                }
                p[k] = pt;
                q[k * 5] = pt[0] - bonePivot[0];
                q[k * 5 + 1] = pt[1] - bonePivot[1];
                q[k * 5 + 2] = pt[2] - bonePivot[2];
                q[k * 5 + 3] = uvs[k][0];
                q[k * 5 + 4] = uvs[k][1];
            }
            float[] n = normal(f);
            float[] nm = mirror(n);
            if (m != null) {
                nm = new float[]{m[0] * nm[0] + m[1] * nm[1] + m[2] * nm[2], m[3] * nm[0] + m[4] * nm[1] + m[5] * nm[2],
                        m[6] * nm[0] + m[7] * nm[1] + m[8] * nm[2]};
            }
            q[20] = nm[0];
            q[21] = nm[1];
            q[22] = nm[2];
            quads.add(q);
        }
    }

    /**
     * A face's corners in Bedrock coordinates, by the face table: left-top, right-top, left-bottom, right-bottom.
     * Faces: 0 up, 1 down, 2 east, 3 west, 4 north, 5 south.
     */
    static float[][] corners(int face, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (face) {
            case 0 -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x0, y1, z0}, {x1, y1, z0}};
            case 1 -> new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x0, y0, z1}, {x1, y0, z1}};
            case 2 -> new float[][]{{x0, y1, z1}, {x0, y1, z0}, {x0, y0, z1}, {x0, y0, z0}};
            case 3 -> new float[][]{{x1, y1, z0}, {x1, y1, z1}, {x1, y0, z0}, {x1, y0, z1}};
            case 4 -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            default -> new float[][]{{x1, y1, z1}, {x0, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
        };
    }

    /** A face's outward normal in Bedrock coordinates. */
    private static float[] normal(int face) {
        return switch (face) {
            case 0 -> new float[]{0, 1, 0};
            case 1 -> new float[]{0, -1, 0};
            case 2 -> new float[]{-1, 0, 0};
            case 3 -> new float[]{1, 0, 0};
            case 4 -> new float[]{0, 0, -1};
            default -> new float[]{0, 0, 1};
        };
    }

    /** The rotation matrix (row-major 3×3) for knife-space angles: Z, then Y, then X onto a point (M = Rz·Ry·Rx). */
    public static float[] rotation(float x, float y, float z, float[] out) {
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x), cy = (float) Math.cos(y), sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        out[0] = cz * cy;
        out[1] = cz * sy * sx - sz * cx;
        out[2] = cz * sy * cx + sz * sx;
        out[3] = sz * cy;
        out[4] = sz * sy * sx + cz * cx;
        out[5] = sz * sy * cx - cz * sx;
        out[6] = -sy;
        out[7] = cy * sx;
        out[8] = cy * cx;
        return out;
    }

    private static float[] turn(float[] m, float[] pivot, float[] p) {
        float dx = p[0] - pivot[0], dy = p[1] - pivot[1], dz = p[2] - pivot[2];
        return new float[]{pivot[0] + m[0] * dx + m[1] * dy + m[2] * dz, pivot[1] + m[3] * dx + m[4] * dy + m[5] * dz,
                pivot[2] + m[6] * dx + m[7] * dy + m[8] * dz};
    }

    private static float[] floats(JsonElement e, int n, float[] fallback) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < n) {
            return fallback;
        }
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            JsonElement v = e.getAsJsonArray().get(i);
            out[i] = v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsFloat() : 0f;
        }
        return out;
    }
}
