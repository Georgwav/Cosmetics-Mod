package io.github.georgwav.cosmetics.sol.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads theme files. Every field is optional: what a file leaves out comes from the base (the built-in defaults,
 * or the theme itself for a keyframe), unknown fields are ignored, and a value of the wrong type or out of range
 * falls back to the base and is reported through the warning callback (the registry logs it once per file).
 * Nothing here throws on bad input.
 */
public final class ThemeParser {
    /** Particle types the atmosphere knows how to move and draw. */
    public static final Set<String> PARTICLE_TYPES = Set.of("petal", "blossom", "snow", "flake", "ember", "dust",
            "firefly", "sparkle", "rain", "leaf", "ash", "lens", "note");
    public static final Set<String> PHASES = Set.of("dawn", "day", "dusk", "night");
    public static final Set<String> STAR_PALETTES = Set.of("natural", "mono", "nebula", "teal", "silver", "gold", "warm");
    public static final Set<String> THROUGH = Set.of("clear", "cloudGaps", "fog");

    /** The defaults every theme file is read over: a plain day sky, like the Empty preset. */
    public static final ThemeSpec DEFAULTS = new ThemeSpec("default", "Theme", "", 1000,
            new ThemeSpec.Ui(0xFF9AA3B5, 0x9E10121A, 0x66FFFFFF),
            new ThemeSpec.Sky(0xFF5F8FE8, 0xFF78A7FF, 0xFFB9D1FF, 0xFFD4E3FF, 0.3f),
            new ThemeSpec.Fog(0xFFC0D8FF, 0.2f, 0.25f, 1.0f, 0f, 0, 0f, 0f, 0.3f, 0.2f),
            new ThemeSpec.Light(0xFFFFFFFF, 0f, 1f, 0xFFFFF4E0, 0),
            new ThemeSpec.Sun("cosmeticsmod:atmosphere/sun", 0.03f, 0xFFFFF3A8, 0xFFFFF2C0, 0.2f, 0.3f, 14, 9, 1f, true, true,
                    true, true, true, true, true, 0f, 0.74f, 0.24f),
            new ThemeSpec.Moon(0.02f, 0xFFE8F0FF, 0xFFC8D4FF, 0.12f, 0.25f, false, false, 0.2f, 0.25f),
            new ThemeSpec.Stars(0, 1f, "natural", null, 0.5f, true, false, 7, true, "clear"),
            new ThemeSpec.Planets(true, "natural", null, List.of("venus")),
            null, null, List.of(), null, false, null, null,
            new ThemeSpec.Clouds(null, true, true),
            new float[]{0.2f, 0f, 0.05f},
            List.of(),
            new ThemeSpec.Show("day", 0f),
            false, false,
            new ThemeSpec.Grade(0xFFFFF4E6, 0xFF1E2230, 1f, 1f, 0xFFFFFFFF, 0.35f, 0.72f, 0.18f, null));

    private ThemeParser() {
    }

    /** Reads a theme file's text; a file that isn't JSON at all gives the base, with a warning. */
    public static ThemeSpec parse(String id, String text, ThemeSpec base, Consumer<String> warn) {
        JsonObject json;
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                warn.accept("not a JSON object");
                return withId(base, id);
            }
            json = root.getAsJsonObject();
        } catch (RuntimeException e) {
            warn.accept("not valid JSON (" + e.getMessage() + ")");
            return withId(base, id);
        }
        return parse(id, json, base, warn);
    }

    private static ThemeSpec withId(ThemeSpec s, String id) {
        return new ThemeSpec(id, s.name(), s.line(), s.order(), s.ui(), s.sky(), s.fog(), s.light(), s.sun(), s.moon(),
                s.stars(), s.planets(), s.galaxy(), s.aurora(), s.nebula(), s.eclipse(), s.lightning(), s.sigil(),
                s.mist(), s.clouds(), s.wind(), s.particles(), s.show(), s.noRidges(), s.vanillaSun(), s.grade());
    }

    public static ThemeSpec parse(String id, JsonObject json, ThemeSpec base, Consumer<String> warn) {
        Reader r = new Reader(json, warn);
        String name = r.string("name", base.name());
        String line = r.string("line", base.line());
        int order = r.integer("order", base.order(), -100000, 100000);

        Reader ui = r.object("ui");
        int accent = ui.color("accent", base.ui().accent());
        int panel = ui.color("panel", base.ui().panel());
        if (ui.has("panelAlpha")) {
            panel = Colors.withAlpha(panel, ui.number("panelAlpha", Colors.a(panel) / 255f, 0f, 1f));
        }
        int rim = ui.color("rim", base.ui().rim());
        if (ui.has("rimAlpha")) {
            rim = Colors.withAlpha(rim, ui.number("rimAlpha", Colors.a(rim) / 255f, 0f, 1f));
        }

        Reader sky = r.object("sky");
        ThemeSpec.Sky skyOut = new ThemeSpec.Sky(sky.color("top", base.sky().top()), sky.color("middle", base.sky().middle()),
                sky.color("horizon", base.sky().horizon()), sky.color("glow", base.sky().glow()),
                sky.number("sunWarmth", base.sky().sunWarmth(), 0f, 1f));

        Reader fog = r.object("fog");
        float density = fog.number("density", base.fog().density(), 0f, 1f);
        // Start and end follow the density unless the file sets them: the prototype's fogged terrain layers.
        float end = fog.has("end") ? fog.number("end", 1f, 0.05f, 2f)
                : fog.has("density") ? defaultFogEnd(density) : base.fog().end();
        float start = fog.has("start") ? fog.number("start", 0f, 0f, 2f)
                : fog.has("density") || fog.has("end") ? end * (0.05f + 0.25f * (1f - density)) : base.fog().start();
        if (start >= end) {
            warn.accept("fog.start must be below fog.end");
            start = end * 0.5f;
        }
        float near = fog.number("near", base.fog().near(), 0f, 4096f);
        float far = fog.number("far", base.fog().far(), 0f, 4096f);
        if (far > 0f && near >= far) {
            warn.accept("fog.near must be below fog.far");
            near = far * 0.25f;
        }
        ThemeSpec.Fog fogOut = new ThemeSpec.Fog(fog.color("color", base.fog().color()), density, start, end,
                fog.number("voxyThinning", base.fog().voxyThinning(), 0f, 1f), fog.integer("bands", base.fog().bands(), 0, 12),
                near, far, fog.number("scatter", base.fog().scatter(), 0f, 2f), fog.number("haze", base.fog().haze(), 0f, 2f));

        Reader light = r.object("light");
        int tint = light.color("tint", base.light().tint());
        ThemeSpec.Light lightOut = new ThemeSpec.Light(tint, light.number("strength", base.light().strength(), 0f, 1f),
                light.number("boost", base.light().boost(), 0.25f, 3f),
                light.color("sun", light.has("tint") && !light.has("sun") ? Colors.mix(0xFFFFFFFF, tint, 0.75f) : base.light().sun()),
                light.has("shadow") ? light.color("shadow", 0) : base.light().shadow());

        Reader gr = r.object("grade");
        ThemeSpec.Grade gb = base.grade();
        ThemeSpec.Grade gradeOut = new ThemeSpec.Grade(gr.color("highlights", gb.highlights()), gr.color("shadows", gb.shadows()),
                gr.number("saturation", gb.saturation(), 0f, 2.5f), gr.number("contrast", gb.contrast(), 0.5f, 2f),
                gr.color("bloomTint", gb.bloomTint()), gr.number("bloom", gb.bloom(), 0f, 3f),
                gr.number("threshold", gb.threshold(), 0f, 1f), gr.number("vignette", gb.vignette(), 0f, 1f),
                gr.string("lut", gb.lut()));

        ThemeSpec.Sun sunOut = base.sun();
        if (r.isNull("sun") || r.isFalse("sun")) {
            sunOut = null;
        } else if (r.has("sun")) {
            Reader s = r.object("sun");
            ThemeSpec.Sun b = base.sun() != null ? base.sun() : DEFAULTS.sun();
            sunOut = new ThemeSpec.Sun(s.string("texture", b.texture()), s.number("size", b.size(), 0.002f, 0.3f),
                    s.color("core", s.color("c", b.core())), s.color("halo", b.halo()),
                    s.number("haloRadius", b.haloRadius(), 0f, 2f), s.number("haloAlpha", b.haloAlpha(), 0f, 1f),
                    s.integer("rays", b.rays(), 0, 64), s.integer("coreRays", b.coreRays(), 0, 64),
                    s.number("rayLength", b.rayLength(), 0f, 3f), s.bool("plasma", b.plasma()), s.bool("corona", b.corona()),
                    s.bool("sunDogs", b.sunDogs()), s.bool("pillar", b.pillar()), s.bool("shimmer", b.shimmer()),
                    s.bool("flare", b.flare()), s.bool("glare", b.glare()), s.number("orbitTilt", b.orbitTilt(), -60f, 60f),
                    s.number("x", b.x(), -1f, 2f), s.number("y", b.y(), -1f, 2f));
        }

        ThemeSpec.Moon moonOut = base.moon();
        if (r.isNull("moon") || r.isFalse("moon")) {
            moonOut = null;
        } else if (r.has("moon")) {
            Reader m = r.object("moon");
            ThemeSpec.Moon b = base.moon() != null ? base.moon() : DEFAULTS.moon();
            moonOut = new ThemeSpec.Moon(m.number("size", b.size(), 0.002f, 0.3f), m.color("color", m.color("c", b.color())),
                    m.color("halo", b.halo()), m.number("haloRadius", b.haloRadius(), 0f, 2f),
                    m.number("haloAlpha", b.haloAlpha(), 0f, 1f), m.bool("ring", b.ring()), m.bool("dogs", b.dogs()),
                    m.number("x", b.x(), -1f, 2f), m.number("y", b.y(), -1f, 2f));
        }

        Reader st = r.object("stars");
        ThemeSpec.Stars bs = base.stars();
        String palette = st.choice("palette", bs.palette(), STAR_PALETTES);
        ThemeSpec.Stars starsOut = new ThemeSpec.Stars(st.integer("count", bs.count(), 0, 2000),
                st.number("density", bs.density(), 0f, 4f), palette, st.optionalColor("tint", bs.tint()),
                st.number("milkyWay", bs.milkyWay(), 0f, 1f), st.bool("deepSky", bs.deepSky()),
                st.bool("shooting", bs.shooting()), st.integer("meteorNights", bs.meteorNights(), 0, 365),
                st.bool("comets", bs.comets()), st.choice("through", bs.through(), THROUGH));

        Reader pl = r.object("planets");
        ThemeSpec.Planets bp = base.planets();
        ThemeSpec.Planets planetsOut = new ThemeSpec.Planets(pl.bool("enabled", bp.enabled()),
                pl.string("style", bp.style()), pl.optionalColor("tint", bp.tint()), pl.strings("daytime", bp.daytime()));

        ThemeSpec.Galaxy galaxyOut = base.galaxy();
        if (r.isNull("galaxy") || r.isFalse("galaxy")) {
            galaxyOut = null;
        } else if (r.has("galaxy")) {
            Reader g = r.object("galaxy");
            ThemeSpec.Galaxy b = base.galaxy() != null ? base.galaxy() : new ThemeSpec.Galaxy(0.66f, 0.26f, 0.34f, 0.42f);
            galaxyOut = new ThemeSpec.Galaxy(g.number("x", b.x(), -1f, 2f), g.number("y", b.y(), -1f, 2f),
                    g.number("r", b.r(), 0.01f, 1f), g.number("tilt", b.tilt(), 0.05f, 1f));
        }

        int[] auroraOut = base.aurora();
        if (r.isNull("aurora") || r.isFalse("aurora")) {
            auroraOut = null;
        } else if (r.has("aurora")) {
            int[] colors = r.colors("aurora");
            auroraOut = colors.length >= 1 ? colors : auroraOut;
        }

        List<ThemeSpec.Nebula> nebulaOut = base.nebula();
        if (r.isNull("nebula") || r.isFalse("nebula")) {
            nebulaOut = List.of();
        } else if (r.has("nebula")) {
            nebulaOut = new ArrayList<>();
            JsonArray arr = r.array("nebula");
            for (JsonElement e : arr) {
                if (e.isJsonObject()) {
                    Reader n = new Reader(e.getAsJsonObject(), warn);
                    nebulaOut.add(new ThemeSpec.Nebula(n.number("x", 0.5f, -1f, 2f), n.number("y", 0.3f, -1f, 2f),
                            n.number("r", 0.3f, 0.01f, 1.5f), n.color("color", 0xFF7B2CFF)));
                } else if (e.isJsonArray() && e.getAsJsonArray().size() == 4) {
                    // The prototype's [x, y, r, "#colour"] form.
                    JsonArray a = e.getAsJsonArray();
                    try {
                        nebulaOut.add(new ThemeSpec.Nebula(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(),
                                Colors.parse(a.get(3).getAsString(), 0xFF7B2CFF)));
                    } catch (RuntimeException ex) {
                        warn.accept("nebula entry " + a + " isn't [x, y, r, colour]");
                    }
                } else {
                    warn.accept("nebula entries are objects {x, y, r, color}");
                }
            }
            nebulaOut = List.copyOf(nebulaOut);
        }

        ThemeSpec.Eclipse eclipseOut = base.eclipse();
        if (r.isNull("eclipse") || r.isFalse("eclipse")) {
            eclipseOut = null;
        } else if (r.has("eclipse")) {
            Reader e = r.object("eclipse");
            ThemeSpec.Eclipse b = base.eclipse() != null ? base.eclipse() : new ThemeSpec.Eclipse(0.5f, 0.34f, 0.055f, 0xFFFF6A3A);
            eclipseOut = new ThemeSpec.Eclipse(e.number("x", b.x(), -1f, 2f), e.number("y", b.y(), -1f, 2f),
                    e.number("size", e.number("s", b.size(), 0.005f, 0.3f), 0.005f, 0.3f), e.color("corona", b.corona()));
        }

        ThemeSpec.Sigil sigilOut = base.sigil();
        if (r.isNull("sigil") || r.isFalse("sigil")) {
            sigilOut = null;
        } else if (r.has("sigil")) {
            Reader s = r.object("sigil");
            ThemeSpec.Sigil b = base.sigil() != null ? base.sigil() : new ThemeSpec.Sigil(0.56f, 0.305f, 0.104f, 0xFFFFD27A, 0xFFE47A26);
            sigilOut = new ThemeSpec.Sigil(s.number("x", b.x(), -1f, 2f), s.number("y", b.y(), -1f, 2f),
                    s.number("r", b.r(), 0.01f, 0.5f), s.color("color", s.color("c", b.color())), s.color("halo", b.halo()));
        }

        ThemeSpec.Mist mistOut = base.mist();
        if (r.isNull("mist") || r.isFalse("mist")) {
            mistOut = null;
        } else if (r.has("mist")) {
            Reader m = r.object("mist");
            ThemeSpec.Mist b = base.mist() != null ? base.mist() : new ThemeSpec.Mist(0xFF285C56, null, 4, 1f);
            mistOut = new ThemeSpec.Mist(m.color("color", m.color("c", b.color())), m.optionalColor("warm", b.warm()),
                    m.integer("bands", m.integer("n", b.bands(), 1, 16), 1, 16), m.number("alpha", m.number("a", b.alpha(), 0f, 2f), 0f, 2f));
        }

        Reader cl = r.object("clouds");
        ThemeSpec.Clouds cloudsOut = new ThemeSpec.Clouds(cl.optionalColor("tint", base.clouds().tint()),
                cl.bool("litBySun", base.clouds().litBySun()), cl.bool("visible", base.clouds().visible()));

        float[] wind = r.has("wind") ? r.floats("wind", base.wind(), 3, -4f, 4f) : base.wind();

        List<ThemeSpec.Emitter> particles = base.particles();
        if (r.has("particles")) {
            particles = new ArrayList<>();
            JsonArray arr = r.array("particles");
            for (JsonElement e : arr) {
                ThemeSpec.Emitter emitter = emitter(e, warn);
                if (emitter != null) {
                    particles.add(emitter);
                }
            }
            particles = List.copyOf(particles);
        }

        Reader show = r.object("show");
        ThemeSpec.Show showOut = new ThemeSpec.Show(show.choice("phase", base.show().phase(), PHASES),
                show.number("azimuth", base.show().azimuth(), -360f, 360f), show.bool("hold", base.show().hold()),
                show.number("time", base.show().time(), -1f, 1f));
        Reader flags = r.object("flags");

        return new ThemeSpec(id, name, line, order, new ThemeSpec.Ui(accent, panel, rim), skyOut, fogOut, lightOut, sunOut,
                moonOut, starsOut, planetsOut, galaxyOut, auroraOut, nebulaOut, eclipseOut,
                r.bool("lightning", base.lightning()), sigilOut, mistOut, cloudsOut, wind, particles, showOut,
                flags.bool("noRidges", base.noRidges()), flags.bool("vanillaSun", base.vanillaSun()), gradeOut);
    }

    /** The fog's end (fraction of render distance) for a density: thicker fog ends closer. */
    public static float defaultFogEnd(float density) {
        return Math.max(0.25f, Math.min(1.05f, 1.15f - 0.8f * density));
    }

    /** One particle stream: an object, or the prototype's ["type", count, ["#colours"], [min, max]] array. */
    static ThemeSpec.Emitter emitter(JsonElement e, Consumer<String> warn) {
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            try {
                String type = a.get(0).getAsString();
                if (!PARTICLE_TYPES.contains(type)) {
                    warn.accept("unknown particle type \"" + type + "\"");
                    return null;
                }
                int count = Math.max(0, Math.min(5000, a.get(1).getAsInt()));
                List<Integer> cols = new ArrayList<>();
                for (JsonElement c : a.get(2).getAsJsonArray()) {
                    Integer parsed = Colors.tryParse(c.getAsString());
                    if (parsed != null) {
                        cols.add(parsed);
                    }
                }
                JsonArray size = a.get(3).getAsJsonArray();
                return new ThemeSpec.Emitter(type, count, cols.isEmpty() ? new int[]{0xFFFFFFFF}
                        : cols.stream().mapToInt(Integer::intValue).toArray(),
                        size.get(0).getAsFloat(), size.get(1).getAsFloat(), null, 1f, 1f);
            } catch (RuntimeException ex) {
                warn.accept("particle entry " + a + " isn't [type, count, [colours], [min, max]]");
                return null;
            }
        }
        if (!e.isJsonObject()) {
            warn.accept("particle entries are objects");
            return null;
        }
        Reader p = new Reader(e.getAsJsonObject(), warn);
        String type = p.string("type", "");
        if (!PARTICLE_TYPES.contains(type)) {
            warn.accept("unknown particle type \"" + type + "\"");
            return null;
        }
        int[] colors = p.has("colors") ? p.colors("colors") : new int[0];
        if (colors.length == 0) {
            colors = new int[]{0xFFFFFFFF};
        }
        float[] size = p.floats("size", new float[]{1.5f, 3f}, 2, 0.1f, 200f);
        return new ThemeSpec.Emitter(type, p.integer("count", 40, 0, 5000), colors, Math.min(size[0], size[1]),
                Math.max(size[0], size[1]), p.has("texture") ? p.string("texture", null) : null,
                p.number("fall", 1f, 0f, 10f), p.number("spin", 1f, 0f, 10f));
    }

    /** Merges {@code over} into a copy of {@code base}: objects merge key by key, anything else replaces. */
    public static JsonObject merge(JsonObject base, JsonObject over) {
        JsonObject out = base.deepCopy();
        for (Map.Entry<String, JsonElement> e : over.entrySet()) {
            JsonElement mine = out.get(e.getKey());
            if (mine != null && mine.isJsonObject() && e.getValue().isJsonObject()) {
                out.add(e.getKey(), merge(mine.getAsJsonObject(), e.getValue().getAsJsonObject()));
            } else {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        return out;
    }

    /** Typed, forgiving reads from one JSON object. */
    static final class Reader {
        private final JsonObject json;
        private final Consumer<String> warn;

        Reader(JsonObject json, Consumer<String> warn) {
            this.json = json;
            this.warn = warn;
        }

        boolean has(String key) {
            return json != null && json.has(key) && !json.get(key).isJsonNull();
        }

        boolean isNull(String key) {
            return json != null && json.has(key) && json.get(key).isJsonNull();
        }

        boolean isFalse(String key) {
            JsonElement e = json != null ? json.get(key) : null;
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && !e.getAsBoolean();
        }

        Reader object(String key) {
            if (has(key) && json.get(key).isJsonObject()) {
                return new Reader(json.getAsJsonObject(key), warn);
            }
            if (has(key) && !(json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isBoolean())) {
                warn.accept(key + " should be an object");
            }
            return new Reader(null, warn);
        }

        JsonArray array(String key) {
            if (has(key) && json.get(key).isJsonArray()) {
                return json.getAsJsonArray(key);
            }
            warn.accept(key + " should be a list");
            return new JsonArray();
        }

        String string(String key, String fallback) {
            if (!has(key)) {
                return fallback;
            }
            JsonElement e = json.get(key);
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                return e.getAsString();
            }
            warn.accept(key + " should be text");
            return fallback;
        }

        String choice(String key, String fallback, Set<String> allowed) {
            String v = string(key, fallback);
            if (allowed.contains(v)) {
                return v;
            }
            warn.accept(key + " \"" + v + "\" isn't one of " + allowed);
            return fallback;
        }

        float number(String key, float fallback, float min, float max) {
            if (!has(key)) {
                return fallback;
            }
            JsonElement e = json.get(key);
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                float v = e.getAsFloat();
                if (Float.isFinite(v) && v >= min && v <= max) {
                    return v;
                }
                warn.accept(key + " " + v + " is outside " + min + ".." + max);
                return fallback;
            }
            warn.accept(key + " should be a number");
            return fallback;
        }

        int integer(String key, int fallback, int min, int max) {
            float v = number(key, fallback, min, max);
            return Math.round(v);
        }

        boolean bool(String key, boolean fallback) {
            if (!has(key)) {
                return fallback;
            }
            JsonElement e = json.get(key);
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
                return e.getAsBoolean();
            }
            warn.accept(key + " should be true or false");
            return fallback;
        }

        int color(String key, int fallback) {
            if (!has(key)) {
                return fallback;
            }
            JsonElement e = json.get(key);
            Integer c = e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? Colors.tryParse(e.getAsString()) : null;
            if (c == null) {
                warn.accept(key + " " + e + " isn't a colour");
                return fallback;
            }
            return c;
        }

        Integer optionalColor(String key, Integer fallback) {
            if (isNull(key)) {
                return null;
            }
            return has(key) ? Integer.valueOf(color(key, fallback == null ? 0xFFFFFFFF : fallback)) : fallback;
        }

        int[] colors(String key) {
            List<Integer> out = new ArrayList<>();
            for (JsonElement e : array(key)) {
                Integer c = e.isJsonPrimitive() ? Colors.tryParse(e.getAsString()) : null;
                if (c == null) {
                    warn.accept(key + ": " + e + " isn't a colour");
                } else {
                    out.add(c);
                }
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }

        float[] floats(String key, float[] fallback, int size, float min, float max) {
            JsonArray a = array(key);
            if (a.size() != size) {
                warn.accept(key + " should hold " + size + " numbers");
                return fallback;
            }
            float[] out = new float[size];
            for (int i = 0; i < size; i++) {
                JsonElement e = a.get(i);
                if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !Float.isFinite(e.getAsFloat())
                        || e.getAsFloat() < min || e.getAsFloat() > max) {
                    warn.accept(key + " holds a bad number " + e);
                    return fallback;
                }
                out[i] = e.getAsFloat();
            }
            return out;
        }

        List<String> strings(String key, List<String> fallback) {
            if (!has(key)) {
                return fallback;
            }
            List<String> out = new ArrayList<>();
            for (JsonElement e : array(key)) {
                if (e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                }
            }
            return List.copyOf(out);
        }
    }
}
