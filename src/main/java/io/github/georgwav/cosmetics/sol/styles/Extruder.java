package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A flat item texture as a voxel model (B.2.4 "Extruded"): one box per run of opaque pixels in a row, merged along the
 * row, a slider's thickness deep, the front and back showing the texture and the sides taking the colours of the
 * pixels at their edge. Written as the game's own model JSON at mix time, so the game draws it itself. Only 16x16 and
 * 32x32 textures (bigger stay flat). Plain Java, so it is unit-tested.
 */
public final class Extruder {
    /** The biggest texture side extruded. */
    public static final int MAX_SIDE = 32;

    private Extruder() {
    }

    /** A made model and how many quads it has (two triangles each). */
    public record Model(JsonObject json, int quads) {
    }

    /**
     * The model for a texture, or null when it can't be extruded (bigger than 32 px, not square, or empty).
     *
     * @param texture   the texture's id as models name it ("minecraft:item/diamond_sword")
     * @param thickness depth in texture pixels
     * @param handheld  held like a tool (swords, pickaxes) rather than like an item
     */
    public static Model extrude(Pixels.Image img, String texture, float thickness, boolean handheld) {
        int w = img.w(), h = img.h();
        if (w != h || w > MAX_SIDE || w < 1) {
            return null;
        }
        float s = 16f / w, depth = Math.max(0.25f, thickness) * s, z0 = 8f - depth / 2f, z1 = 8f + depth / 2f;
        JsonArray elements = new JsonArray();
        int quads = 0;
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                if (!solid(img, x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < w && solid(img, x, y)) {
                    x++;
                }
                int end = x - 1;
                JsonObject el = new JsonObject();
                el.add("from", vec(start * s, (h - 1 - y) * s, z0));
                el.add("to", vec((end + 1) * s, (h - y) * s, z1));
                JsonObject faces = new JsonObject();
                // Front and back show the run's own pixels (the back mirrored, as the game's flat items are).
                faces.add("south", face(start * s, y * s, (end + 1) * s, (y + 1) * s, texture));
                faces.add("north", face((end + 1) * s, y * s, start * s, (y + 1) * s, texture));
                quads += 2;
                // The ends: one pixel's colour each.
                if (start == 0 || !solid(img, start - 1, y)) {
                    faces.add("west", face(start * s, y * s, (start + 1) * s, (y + 1) * s, texture));
                    quads++;
                }
                if (end == w - 1 || !solid(img, end + 1, y)) {
                    faces.add("east", face(end * s, y * s, (end + 1) * s, (y + 1) * s, texture));
                    quads++;
                }
                // Top and bottom only where some pixel of the run is at an edge.
                if (edge(img, start, end, y - 1)) {
                    faces.add("up", face(start * s, y * s, (end + 1) * s, (y + 1) * s, texture));
                    quads++;
                }
                if (edge(img, start, end, y + 1)) {
                    faces.add("down", face(start * s, y * s, (end + 1) * s, (y + 1) * s, texture));
                    quads++;
                }
                el.add("faces", faces);
                elements.add(el);
            }
        }
        if (elements.isEmpty()) {
            return null;
        }
        JsonObject model = new JsonObject();
        model.addProperty("gui_light", "front");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", texture);
        textures.addProperty("particle", texture);
        model.add("textures", textures);
        model.add("elements", elements);
        model.add("display", display(handheld));
        return new Model(model, quads);
    }

    private static boolean solid(Pixels.Image img, int x, int y) {
        return (img.argb()[y * img.w() + x] >>> 24) >= 128;
    }

    /** Whether any pixel of the run has a clear (or no) pixel on row {@code y}. */
    private static boolean edge(Pixels.Image img, int x0, int x1, int y) {
        if (y < 0 || y >= img.h()) {
            return true;
        }
        for (int x = x0; x <= x1; x++) {
            if (!solid(img, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray vec(float x, float y, float z) {
        JsonArray a = new JsonArray();
        a.add(round(x));
        a.add(round(y));
        a.add(round(z));
        return a;
    }

    private static JsonObject face(float u0, float v0, float u1, float v1, String texture) {
        JsonObject f = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(round(u0));
        uv.add(round(v0));
        uv.add(round(u1));
        uv.add(round(v1));
        f.add("uv", uv);
        f.addProperty("texture", "#layer0");
        return f;
    }

    private static float round(float v) {
        return Math.round(v * 10000f) / 10000f;
    }

    /** Where the model sits in hand, on the ground, in a frame: the game's own values for a held tool or an item. */
    private static JsonObject display(boolean handheld) {
        JsonObject d = new JsonObject();
        d.add("ground", transform(0, 0, 0, 0, 2, 0, 0.5f));
        d.add("head", transform(0, 180, 0, 0, 13, 7, 1f));
        d.add("fixed", transform(0, 180, 0, 0, 0, 0, 1f));
        if (handheld) {
            d.add("thirdperson_righthand", transform(0, -90, 55, 0, 4f, 0.5f, 0.85f));
            d.add("thirdperson_lefthand", transform(0, 90, -55, 0, 4f, 0.5f, 0.85f));
        } else {
            d.add("thirdperson_righthand", transform(0, 0, 0, 0, 3, 1, 0.55f));
            d.add("thirdperson_lefthand", transform(0, 0, 0, 0, 3, 1, 0.55f));
        }
        d.add("firstperson_righthand", transform(0, -90, 25, 1.13f, 3.2f, 1.13f, 0.68f));
        d.add("firstperson_lefthand", transform(0, 90, -25, 1.13f, 3.2f, 1.13f, 0.68f));
        return d;
    }

    private static JsonObject transform(float rx, float ry, float rz, float tx, float ty, float tz, float scale) {
        JsonObject t = new JsonObject();
        t.add("rotation", vec(rx, ry, rz));
        t.add("translation", vec(tx, ty, tz));
        t.add("scale", vec(scale, scale, scale));
        return t;
    }
}
