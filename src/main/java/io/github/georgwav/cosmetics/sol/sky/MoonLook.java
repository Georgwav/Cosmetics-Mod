package io.github.georgwav.cosmetics.sol.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A moon look (the Night Sky lab's {@code LOOKS}), read from {@code assets/<ns>/moon_looks/<id>.json} in the shape
 * Enhanced Celestials gives its lunar events: how often it comes ({@code chance} on a night it may, at most once in
 * {@code minNightsBetween}, only in {@code validPhases}; a chance of 0 means only a theme that names it shows it), and
 * how it looks: the moon's tint, size and glow, its light's colour and strength, the map it wears ({@code body}),
 * earthshine, how much of the Milky Way comes back, and its effects (a harvest rise, a 22° halo, a corona, a total
 * eclipse, a tinted night, no moon at all). {@code elongation} is its angle from the sun (its phase) on a theme that
 * holds its showpiece night; elsewhere the moon keeps the game's phase. Purely visual: the world never changes. Pure
 * Java; immutable.
 */
public record MoonLook(String id, String name, float chance, int minNightsBetween, boolean[] phases, float[] tint, float size,
                       float glow, float light, Integer lightColor, String body, float earthshine, float milkyWay,
                       boolean harvest, boolean halo, String corona, boolean eclipse, String skyTint, boolean hidden,
                       float elongation) {
    /** The game's moon phases in order (0 is full). */
    public static final String[] PHASES = {"full_moon", "waning_gibbous", "third_quarter", "waning_crescent", "new_moon",
            "waxing_crescent", "first_quarter", "waxing_gibbous"};

    public static final MoonLook SILVER = new MoonLook("silver", "Silver", 0f, 1, all(), new float[]{.97f, .98f, 1.02f}, 1f, 1f,
            1f, null, "moon", .45f, 1f, false, false, "none", false, "none", false, 128f);

    private static boolean[] all() {
        boolean[] p = new boolean[8];
        java.util.Arrays.fill(p, true);
        return p;
    }

    /** Whether this look may show in a phase (0 full to 7). */
    public boolean allows(int phase) {
        return phases[Math.floorMod(phase, 8)];
    }

    /** Reads a look file; bad values fall back to the silver moon's and are reported through {@code warn}. */
    public static MoonLook parse(String id, String text, Consumer<String> warn) {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(text);
            o = e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ex) {
            warn.accept("not JSON: " + ex.getMessage());
            o = new JsonObject();
        }
        MoonLook d = SILVER;
        boolean[] phases = all();
        if (o.has("validPhases") && o.get("validPhases").isJsonArray()) {
            phases = new boolean[8];
            for (JsonElement p : o.getAsJsonArray("validPhases")) {
                String name = p.isJsonPrimitive() ? p.getAsString().toLowerCase(Locale.ROOT) : "";
                int at = java.util.Arrays.asList(PHASES).indexOf(name);
                if (at < 0) {
                    warn.accept("validPhases: unknown phase " + p);
                } else {
                    phases[at] = true;
                }
            }
        }
        float[] tint = d.tint();
        if (o.has("tint") && o.get("tint").isJsonArray() && o.getAsJsonArray("tint").size() == 3) {
            JsonArray a = o.getAsJsonArray("tint");
            tint = new float[]{clamp(a.get(0).getAsFloat(), 0f, 2f), clamp(a.get(1).getAsFloat(), 0f, 2f), clamp(a.get(2).getAsFloat(), 0f, 2f)};
        }
        Integer lightColor = null;
        if (o.has("lightColor") && o.get("lightColor").isJsonPrimitive()) {
            lightColor = io.github.georgwav.cosmetics.sol.util.Colors.tryParse(o.get("lightColor").getAsString());
            if (lightColor == null) {
                warn.accept("lightColor: " + o.get("lightColor") + " isn't a colour");
            }
        }
        String body = string(o, "body", d.body());
        if (!BodyMaps.has(body)) {
            warn.accept("body: no map " + body);
            body = d.body();
        }
        return new MoonLook(id, string(o, "name", id), number(o, "chance", 0f, 0f, 1f), (int) number(o, "minNightsBetween", 1f, 1f, 1000f),
                phases, tint, number(o, "size", d.size(), 0.2f, 4f), number(o, "glow", d.glow(), 0f, 3f),
                number(o, "light", d.light(), 0f, 2f), lightColor, body, number(o, "earthshine", d.earthshine(), 0f, 2f),
                number(o, "milkyWay", d.milkyWay(), 0f, 2f), bool(o, "harvest"), bool(o, "halo"), string(o, "corona", "none"),
                bool(o, "eclipse"), string(o, "skyTint", "none"), bool(o, "hidden"), number(o, "elongation", d.elongation(), 0f, 360f));
    }

    private static float number(JsonObject o, String key, float fallback, float min, float max) {
        try {
            return o.has(key) ? clamp(o.get(key).getAsFloat(), min, max) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    private static float clamp(float v, float a, float b) {
        return v < a ? a : Math.min(b, v);
    }
}
