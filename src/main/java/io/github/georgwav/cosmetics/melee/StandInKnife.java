package io.github.georgwav.cosmetics.melee;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A stand-in knife, made in code, for tests and the in-game proof runs (no pack from the three games is ever in the
 * repository, CI or the jar). It's a gunpack in the TACZ layout with the standard rig: {@code root},
 * {@code gun_and_righthand} / {@code righthand} / {@code righthand_pos}, {@code mag_and_lefthand} / {@code lefthand} /
 * {@code lefthand_pos}, {@code weapon} with a handle, a blade, a {@code blade_illuminated} edge and a
 * {@code 1p_effect_1} slash, {@code camera}, and {@code view} / {@code idle_view}. Its animations: {@code static_idle},
 * {@code idle}, {@code draw}, {@code draw_1}, {@code inspect}, {@code inspect_1}, {@code melee_1}, {@code melee_2}
 * and {@code melee_3} (with camera keys and a sound).
 */
public final class StandInKnife {
    public static final String NAMESPACE = "solstandin", ID = "standin";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private StandInKnife() {
    }

    /** Writes the stand-in gunpack into {@code folder} (as {@code folder/StandIn/assets/solstandin/...}). */
    public static void write(Path folder) throws IOException {
        Path root = folder.resolve("StandIn").resolve("assets").resolve(NAMESPACE);
        JsonObject display = new JsonObject();
        display.addProperty("model", NAMESPACE + ":melee/" + ID + "_geo");
        display.addProperty("texture", NAMESPACE + ":melee/" + ID);
        display.addProperty("slot", NAMESPACE + ":melee/slot/" + ID);
        display.addProperty("animation", NAMESPACE + ":melee/" + ID);
        display.addProperty("state_machine", NAMESPACE + ":melee_default_state_machine");
        JsonObject transforms = new JsonObject();
        JsonObject third = new JsonObject();
        third.add("rotation", arr(0, 0, 0));
        third.add("translation", arr(0, 0, 0));
        third.add("scale", arr(1, 1, 1));
        transforms.add("thirdperson_righthand", third);
        display.add("transforms", transforms);
        put(root.resolve("display/melee/" + ID + ".json"), GSON.toJson(display));
        put(root.resolve("geo_models/melee/" + ID + "_geo.json"), GSON.toJson(geo()));
        put(root.resolve("animations/melee/" + ID + ".animation.json"), GSON.toJson(animations()));
        Files.createDirectories(root.resolve("textures/melee/slot"));
        Files.write(root.resolve("textures/melee/" + ID + ".png"), png(texture()));
        Files.write(root.resolve("textures/melee/slot/" + ID + ".png"), png(slot()));
        // A sound key for the animations (its content isn't played in tests; any bytes do for the mount).
        Path snd = root.resolve("tacz_sounds/melee/" + ID + "/swing.ogg");
        Files.createDirectories(snd.getParent());
        Files.write(snd, new byte[]{'O', 'g', 'g', 'S', 0});
    }

    // ------------------------------------------------------------------ the model (Bedrock coordinates, pixels)

    static JsonObject geo() {
        JsonArray bones = new JsonArray();
        bones.add(bone("root", null, 0, 0, 0, null));
        bones.add(bone("gun_and_righthand", "root", -10, 52, -6, null));
        // The right arm points forward and a little up; the knife sits in its fist.
        bones.add(bone("righthand", "gun_and_righthand", -12, 52, -6, rot(80, 0, 0)));
        bones.add(bone("righthand_pos", "righthand", -12, 52, -6, null));
        JsonObject weapon = bone("weapon", "gun_and_righthand", -6, 54, -18, rot(-20, -15, 0));
        JsonArray cubes = new JsonArray();
        cubes.add(cube(-6.5f, 53.5f, -20f, 1f, 1f, 4f, 0, 0));
        cubes.add(cube(-6.25f, 53.25f, -30f, 0.5f, 1.5f, 10f, 0, 8));
        weapon.add("cubes", cubes);
        bones.add(weapon);
        JsonObject edge = bone("blade_illuminated", "weapon", -6, 54, -18, null);
        JsonArray ec = new JsonArray();
        ec.add(cube(-6.2f, 54.75f, -30f, 0.4f, 0.25f, 10f, 0, 24));
        edge.add("cubes", ec);
        bones.add(edge);
        JsonObject fx = bone("1p_effect_1", "weapon", -6, 54, -18, null);
        JsonArray fc = new JsonArray();
        fc.add(cube(-14f, 50f, -32f, 16f, 8f, 0.1f, 0, 32));
        fx.add("cubes", fc);
        bones.add(fx);
        bones.add(bone("mag_and_lefthand", "root", 10, 52, -6, null));
        bones.add(bone("lefthand", "mag_and_lefthand", 16, 40, -6, rot(30, 0, 0)));
        bones.add(bone("lefthand_pos", "lefthand", 16, 40, -6, null));
        bones.add(bone("camera", null, 0, 64, 0, null));
        bones.add(bone("view", null, 0, 64, 0, null));
        bones.add(bone("idle_view", "view", 0, 64, 0, null));
        JsonObject desc = new JsonObject();
        desc.addProperty("identifier", "geometry." + ID);
        desc.addProperty("texture_width", 64);
        desc.addProperty("texture_height", 64);
        JsonObject g = new JsonObject();
        g.add("description", desc);
        g.add("bones", bones);
        JsonArray list = new JsonArray();
        list.add(g);
        JsonObject out = new JsonObject();
        out.addProperty("format_version", "1.12.0");
        out.add("minecraft:geometry", list);
        return out;
    }

    private static JsonObject bone(String name, String parent, float x, float y, float z, JsonArray rotation) {
        JsonObject b = new JsonObject();
        b.addProperty("name", name);
        if (parent != null) {
            b.addProperty("parent", parent);
        }
        b.add("pivot", arr(x, y, z));
        if (rotation != null) {
            b.add("rotation", rotation);
        }
        return b;
    }

    /** A cube whose six faces each take an 8×8 square from (u, v) along a row. */
    private static JsonObject cube(float x, float y, float z, float sx, float sy, float sz, int u, int v) {
        JsonObject c = new JsonObject();
        c.add("origin", arr(x, y, z));
        c.add("size", arr(sx, sy, sz));
        JsonObject uv = new JsonObject();
        String[] faces = {"north", "east", "south", "west", "up", "down"};
        for (int i = 0; i < faces.length; i++) {
            JsonObject f = new JsonObject();
            f.add("uv", arr(u + (i % 6) * 8f + 1f, v + 1f));
            f.add("uv_size", arr(6f, 6f));
            uv.add(faces[i], f);
        }
        c.add("uv", uv);
        return c;
    }

    // ------------------------------------------------------------------ the animations

    static JsonObject animations() {
        JsonObject anims = new JsonObject();
        anims.add("static_idle", anim(0f, false, null));
        JsonObject idle = anim(2f, true, null);
        channel(idle, "weapon", "position", keys(0f, v(0, 0, 0), 1f, v(0, 0.3f, 0), 2f, v(0, 0, 0)));
        anims.add("idle", idle);
        for (int d = 0; d < 2; d++) {
            JsonObject draw = anim(0.6f, false, null);
            channel(draw, "gun_and_righthand", "position", keys(0f, v(d == 0 ? 0 : 8, -18, 0), 0.5f, v(0, 0, 0)));
            channel(draw, "gun_and_righthand", "rotation", keys(0f, v(-60, d == 0 ? 0 : 40, 0), 0.5f, v(0, 0, 0)));
            anims.add(d == 0 ? "draw" : "draw_1", draw);
        }
        JsonObject inspect = anim(1.5f, false, null);
        JsonObject spin = new JsonObject();
        spin.add("0.0", v(0, 0, 0));
        JsonObject mid = new JsonObject();
        mid.add("pre", v(0, 0, 180));
        mid.add("post", v(0, 0, 180));
        mid.addProperty("lerp_mode", "catmullrom");
        spin.add("0.7", mid);
        spin.add("1.4", v(0, 0, 360));
        channel(inspect, "weapon", "rotation", spin);
        anims.add("inspect", inspect);
        JsonObject inspect1 = anim(1.2f, false, null);
        channel(inspect1, "weapon", "rotation", keys(0f, v(0, 0, 0), 0.6f, v(0, 180, 0), 1.1f, v(0, 360, 0)));
        anims.add("inspect_1", inspect1);
        for (int m = 1; m <= 3; m++) {
            float s = m == 2 ? -1 : 1, big = m == 3 ? 1.6f : 1f;
            JsonObject melee = anim(m == 3 ? 1.2f : 0.9f, false, NAMESPACE + ":melee/" + ID + "/swing");
            channel(melee, "gun_and_righthand", "rotation", keys(0f, v(0, 0, 0), 0.1f, v(15 * big, 30 * s * big, 0),
                    0.25f, v(-25 * big, -50 * s * big, 0), 0.8f, v(0, 0, 0)));
            channel(melee, "1p_effect_1", "scale", keys(0f, v(0, 0, 0), 0.12f, v(0, 0, 0), 0.15f, v(1, 1, 1), 0.3f, v(0, 0, 0)));
            if (m == 3) {
                channel(melee, "camera", "rotation", keys(0f, v(0, 0, 0), 0.3f, v(2, 1, 0), 0.6f, v(0, 0, 0)));
            }
            anims.add("melee_" + m, melee);
        }
        JsonObject out = new JsonObject();
        out.addProperty("format_version", "1.8.0");
        out.add("animations", anims);
        return out;
    }

    private static JsonObject anim(float length, boolean loop, String sound) {
        JsonObject a = new JsonObject();
        if (length > 0f) {
            a.addProperty("animation_length", length);
        }
        if (loop) {
            a.addProperty("loop", true);
        }
        a.add("bones", new JsonObject());
        if (sound != null) {
            JsonObject effects = new JsonObject();
            JsonObject e = new JsonObject();
            e.addProperty("effect", sound);
            effects.add("0.0", e);
            a.add("sound_effects", effects);
        }
        return a;
    }

    private static void channel(JsonObject anim, String bone, String kind, JsonObject keys) {
        JsonObject bones = anim.getAsJsonObject("bones");
        if (!bones.has(bone)) {
            bones.add(bone, new JsonObject());
        }
        bones.getAsJsonObject(bone).add(kind, keys);
    }

    private static JsonObject keys(Object... timeValue) {
        JsonObject k = new JsonObject();
        for (int i = 0; i < timeValue.length; i += 2) {
            k.add(String.valueOf(timeValue[i]), (JsonArray) timeValue[i + 1]);
        }
        return k;
    }

    private static JsonArray v(float x, float y, float z) {
        return arr(x, y, z);
    }

    private static JsonArray rot(float x, float y, float z) {
        return arr(x, y, z);
    }

    private static JsonArray arr(float... v) {
        JsonArray a = new JsonArray();
        for (float f : v) {
            a.add(f);
        }
        return a;
    }

    // ------------------------------------------------------------------ pictures

    /** 64×64: a dark handle row, a steel blade row, a glowing edge row, a slash row. */
    private static BufferedImage texture() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[] rows = {0xFF2A2A30, 0xFFB8C2CC, 0xFFFFFFFF, 0xFF7FE8FF, 0x00000000, 0xFF7FE8FF};
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int band = Math.min(rows.length - 1, y / 8);
                int c = rows[band];
                if (band == 1 && (x + y) % 5 == 0) {
                    c = 0xFFD6DEE6;
                }
                if (band == 4 || band == 5) {
                    // The slash: bright in the middle, fading to its edges.
                    float a = Math.max(0f, 1f - Math.abs((x % 8) - 3.5f) / 4f);
                    c = ((int) (a * 200) << 24) | 0x7FE8FF;
                }
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    private static BufferedImage slot() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int i = 4; i < 28; i++) {
            img.setRGB(i, 31 - i, 0xFFB8C2CC);
            img.setRGB(Math.min(31, i + 1), 31 - i, 0xFF7FE8FF);
        }
        for (int i = 2; i < 8; i++) {
            img.setRGB(i, 29 - i + 2, 0xFF2A2A30);
        }
        return img;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void put(Path to, String text) throws IOException {
        Files.createDirectories(to.getParent());
        Files.writeString(to, text);
    }
}
