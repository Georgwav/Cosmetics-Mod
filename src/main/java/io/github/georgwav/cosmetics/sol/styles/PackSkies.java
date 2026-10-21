package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

/**
 * Packs' skies (B.2.5), read as data: OptiFine's custom sky layers ({@code optifine/sky/world0/sky<n>.properties},
 * a 3 x 2 box texture with fade times, a blend, rotation and weather) and FabricSkyboxes' skies
 * ({@code assets/fabricskyboxes/sky/*.json}: six textures or one 3 x 2 sprite, or a plain colour). Sol draws them
 * in its own sky pass. Plain Java, so it is unit-tested.
 */
public final class PackSkies {
    /** The box's faces in a 3 x 2 texture's cells (column, row): bottom, top, east / south, west, north. */
    public static final int BOTTOM = 0, TOP = 1, EAST = 2, SOUTH = 3, WEST = 4, NORTH = 5;

    /**
     * One layer.
     *
     * @param textures  the box's texture (one, in OptiFine's 3 x 2 cells) or its six faces in face order; Styles paths
     * @param colour    a plain colour sky (ARGB), or 0
     * @param fade      startFadeIn, endFadeIn, startFadeOut, endFadeOut in day ticks (0 = 6:00), or null: always on
     * @param additive  added to the sky under it (else blended by alpha)
     * @param rotate    turns with the time of day, {@code speed} days a turn, about {@code axis}
     * @param weather   "clear", "rain", "thunder" it shows in
     */
    public record Layer(List<String> textures, int colour, int[] fade, boolean additive, boolean rotate, float speed, float[] axis,
                        List<String> weather, float maxAlpha) {
        public boolean sixFaces() {
            return textures.size() == 6;
        }
    }

    private PackSkies() {
    }

    /**
     * Every sky layer a pack has, in drawing order: OptiFine's sky0, sky1... (until one is missing), then
     * FabricSkyboxes'. {@code text} reads a pack file as text (null when it's not there); {@code has} says whether
     * the pack has a file.
     */
    public static List<Layer> read(Function<String, String> text, Function<String, Boolean> has, List<String> files) {
        List<Layer> out = new ArrayList<>();
        for (int n = 0; n < 64; n++) {
            String dir = "optifine/sky/world0/";
            String props = text.apply(dir + "sky" + n + ".properties");
            if (props == null) {
                if (n == 0) {
                    continue; // sky0 is often left out; sky1 starts the list
                }
                break;
            }
            Layer l = optifine(props, dir, n);
            if (l != null && has.apply(l.textures().get(0))) {
                out.add(l);
            }
        }
        for (String f : files) {
            if (f.startsWith("fabricskyboxes:sky/") && f.endsWith(".json")) {
                String json = text.apply(f);
                Layer l = json == null ? null : fabric(json);
                if (l != null) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    /** One OptiFine layer from its properties. */
    static Layer optifine(String text, String dir, int n) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        String src = p.getProperty("source", "./sky" + n + ".png").trim();
        String texture = resolve(src, dir);
        int[] fade = null;
        String sfi = p.getProperty("startFadeIn"), efi = p.getProperty("endFadeIn"), efo = p.getProperty("endFadeOut");
        if (sfi != null && efi != null && efo != null) {
            int a = ticks(sfi), b = ticks(efi), d = ticks(efo);
            String sfo = p.getProperty("startFadeOut");
            // startFadeOut follows from the others: the fade out takes as long as the fade in.
            int c = sfo != null ? ticks(sfo) : Math.floorMod(d - Math.floorMod(b - a, 24000), 24000);
            if (a >= 0 && b >= 0 && c >= 0 && d >= 0) {
                fade = new int[]{a, b, c, d};
            }
        }
        String blend = p.getProperty("blend", "add").trim().toLowerCase(Locale.ROOT);
        boolean rotate = !"false".equalsIgnoreCase(p.getProperty("rotate", "true").trim());
        float speed = parse(p.getProperty("speed"), 1f);
        float[] axis = {0f, 0f, 1f};
        String ax = p.getProperty("axis");
        if (ax != null) {
            String[] parts = ax.trim().split("\\s+");
            if (parts.length == 3) {
                axis = new float[]{parse(parts[0], 0f), parse(parts[1], 0f), parse(parts[2], 1f)};
            }
        }
        List<String> weather = List.of(p.getProperty("weather", "clear").trim().toLowerCase(Locale.ROOT).split("\\s+"));
        return new Layer(List.of(texture), 0, fade, !blend.equals("alpha") && !blend.equals("replace"), rotate, speed, axis, weather, 1f);
    }

    /** A source path as OptiFine resolves it: ./ (beside the properties), ~/ (under optifine/), ns:path, or under assets/minecraft. */
    static String resolve(String src, String dir) {
        if (src.startsWith("./")) {
            return dir + src.substring(2);
        }
        if (src.startsWith("~/")) {
            return "optifine/" + src.substring(2);
        }
        if (src.startsWith("minecraft:")) {
            return src.substring("minecraft:".length());
        }
        return src;
    }

    /** "hh:mm" as day ticks (0 is 6:00), or -1. */
    static int ticks(String hhmm) {
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) {
            return -1;
        }
        try {
            int h = Integer.parseInt(parts[0]), m = Integer.parseInt(parts[1]);
            return Math.floorMod((h - 6) * 1000 + Math.round(m * 1000f / 60f), 24000);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** How bright a layer is at a day time (0..24000): its fade in, full, fade out, nothing. */
    public static float brightness(int[] fade, long dayTime) {
        if (fade == null) {
            return 1f;
        }
        int t = (int) Math.floorMod(dayTime, 24000L);
        int a = fade[0], b = fade[1], c = fade[2], d = fade[3];
        if (inside(t, a, b)) {
            return span(a, t) / (float) Math.max(1, span(a, b));
        }
        if (inside(t, b, c)) {
            return 1f;
        }
        if (inside(t, c, d)) {
            return 1f - span(c, t) / (float) Math.max(1, span(c, d));
        }
        return 0f;
    }

    private static int span(int from, int to) {
        return Math.floorMod(to - from, 24000);
    }

    private static boolean inside(int t, int from, int to) {
        return span(from, t) <= span(from, to) && span(from, to) > 0 || t == from && from == to;
    }

    /** A FabricSkyboxes sky: square-textured (six faces), single-sprite (OptiFine's 3 x 2) or monocolor. */
    static Layer fabric(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String type = str(o, "type", "");
            JsonObject props = o.has("properties") && o.get("properties").isJsonObject() ? o.getAsJsonObject("properties") : new JsonObject();
            int[] fade = null;
            if (props.has("fade") && props.get("fade").isJsonObject()) {
                JsonObject f = props.getAsJsonObject("fade");
                if (!f.has("alwaysOn") || !f.get("alwaysOn").getAsBoolean()) {
                    // FabricSkyboxes counts its ticks from the game's time 0 (6:00), as the game does.
                    fade = new int[]{num(f, "startFadeIn"), num(f, "endFadeIn"), num(f, "startFadeOut"), num(f, "endFadeOut")};
                    for (int i = 0; i < 4; i++) {
                        fade[i] = Math.floorMod(fade[i], 24000);
                    }
                }
            }
            float maxAlpha = props.has("maxAlpha") ? props.get("maxAlpha").getAsFloat() : 1f;
            boolean rotate = props.has("shouldRotate") && props.get("shouldRotate").getAsBoolean();
            boolean additive = o.has("blend") && o.get("blend").isJsonObject() && str(o.getAsJsonObject("blend"), "type", "").equals("add");
            List<String> weather = List.of("clear", "rain", "thunder");
            if (type.contains("monocolor") || type.contains("mono-color")) {
                JsonObject c = o.has("color") ? o.getAsJsonObject("color") : new JsonObject();
                int argb = Math.round(clamp(fl(c, "alpha", 1f)) * 255f) << 24 | Math.round(clamp(fl(c, "red", 0f)) * 255f) << 16
                        | Math.round(clamp(fl(c, "green", 0f)) * 255f) << 8 | Math.round(clamp(fl(c, "blue", 0f)) * 255f);
                return new Layer(List.of(), argb, fade, additive, rotate, 1f, new float[]{0f, 0f, 1f}, weather, maxAlpha);
            }
            if (type.contains("single-sprite")) {
                return new Layer(List.of(path(str(o, "texture", ""))), 0, fade, additive, rotate, 1f, new float[]{0f, 0f, 1f}, weather, maxAlpha);
            }
            if (o.has("textures") && o.get("textures").isJsonObject()) {
                JsonObject t = o.getAsJsonObject("textures");
                List<String> faces = new ArrayList<>();
                for (String face : new String[]{"bottom", "top", "east", "south", "west", "north"}) {
                    faces.add(path(str(t, face, "")));
                }
                return new Layer(faces, 0, fade, additive, rotate, 1f, new float[]{0f, 0f, 1f}, weather, maxAlpha);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** A namespaced texture id as a Styles path ("minecraft:textures/x.png" is "textures/x.png"). */
    static String path(String id) {
        if (id.startsWith("minecraft:")) {
            return id.substring("minecraft:".length());
        }
        return id;
    }

    private static String str(JsonObject o, String k, String fallback) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static int num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
    }

    private static float fl(JsonObject o, String k, float fallback) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsFloat() : fallback;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float parse(String s, float fallback) {
        try {
            return s == null ? fallback : Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * A box face's corner as a direction (x east, y up, z south), from its place on the face (s, t each 0..1, as the
     * texture's u and v): the faces as OptiFine lays them out, the picture read from inside the box.
     */
    public static void corner(int face, float s, float t, float[] out) {
        float x = 2f * s - 1f, z = 2f * t - 1f;
        switch (face) {
            case BOTTOM -> set(out, z, -1f, -x);
            case TOP -> set(out, -z, 1f, -x);
            case EAST -> set(out, 1f, -z, x);
            case SOUTH -> set(out, -x, -z, 1f);
            case WEST -> set(out, -1f, -z, -x);
            default -> set(out, x, -z, -1f);
        }
    }

    private static void set(float[] out, float x, float y, float z) {
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }
}
