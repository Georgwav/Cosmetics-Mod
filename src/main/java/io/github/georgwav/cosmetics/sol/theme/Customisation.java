package io.github.georgwav.cosmetics.sol.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's edits to one theme, saved in the profile: hue and saturation (the brief's {@code customTheme()}),
 * particle density, fog density, sun size and rays, particle types switched off, stars, planets, the Milky Way and
 * the aurora's colours, which of the night sky's layers it shows ({@link #LAYERS}, all on unless switched off), a
 * colour of its own for any part of it ({@link #COLOURS}: the sky, fog, light, sun, moon, clouds, particles, the
 * night) and how much of each of its effects ({@link #AMOUNTS}: light, glow, bloom, wind...). Applied over every
 * time of day of the theme. Immutable; {@link #NONE} changes nothing.
 */
public record Customisation(float hue, float sat, float density, Float fog, float sunSize, Integer rays,
                            Map<String, Boolean> particles, float stars, Boolean planets, Float milkyWay, int[] aurora,
                            Map<String, Boolean> layers, Map<String, Integer> colours, Map<String, Float> amounts, String moonLook) {
    public static final Customisation NONE = new Customisation(0f, 1f, 1f, null, 1f, null, Map.of(), 1f, null, null, null, Map.of(),
            Map.of(), Map.of(), null);
    /**
     * The parts of a theme that can take a colour of their own ({@link #colours}). Each is recoloured the way the
     * theme's own changes through the day: exactly the picked colour where the theme has its own (its showpiece),
     * and as much lighter or darker at the other times as the theme is. The night ones only colour the night.
     */
    public static final List<String> COLOURS = List.of("accent", "panel", "sky_top", "sky_middle", "sky_horizon", "sky_glow", "fog",
            "light", "sunlight", "sun_core", "sun_halo", "clouds", "particles", "aurora_1", "aurora_2", "nebula", "corona",
            "night_top", "night_middle", "night_horizon", "moon", "moon_halo", "moonlight", "airglow", "stars", "planets");
    /** The parts of a theme that can be more or less of themselves ({@link #amounts}, 1 = the theme's own). */
    public static final Map<String, float[]> AMOUNTS = amountRanges();
    /** The night sky's layers (the Night Sky lab's), each on unless a theme's edits switch it off. */
    public static final java.util.List<String> LAYERS = java.util.List.of("stars", "milky_way", "nebulae", "planets",
            "wild_worlds", "moonlight", "meteors", "showers", "comet", "ships", "airglow", "names");

    public Customisation {
        hue = clamp(hue, -180f, 180f);
        sat = clamp(sat, 0f, 2f);
        density = clamp(density, 0f, 3f);
        fog = fog == null ? null : clamp(fog, 0f, 1f);
        sunSize = clamp(sunSize, 0.5f, 2.5f);
        rays = rays == null ? null : Math.max(0, Math.min(32, rays));
        particles = particles == null ? Map.of() : Map.copyOf(particles);
        stars = clamp(stars, 0f, 3f);
        milkyWay = milkyWay == null ? null : clamp(milkyWay, 0f, 1f);
        layers = layers == null ? Map.of() : Map.copyOf(layers);
        Map<String, Integer> cs = new LinkedHashMap<>();
        if (colours != null) {
            colours.forEach((k, v) -> {
                if (COLOURS.contains(k) && v != null) {
                    cs.put(k, 0xFF000000 | v);
                }
            });
        }
        colours = Map.copyOf(cs);
        Map<String, Float> as = new LinkedHashMap<>();
        if (amounts != null) {
            amounts.forEach((k, v) -> {
                float[] range = AMOUNTS.get(k);
                if (range != null && v != null && !Float.isNaN(v)) {
                    float c = clamp(v, range[0], range[1]);
                    if (c != 1f) {
                        as.put(k, c);
                    }
                }
            });
        }
        amounts = Map.copyOf(as);
    }

    private static Map<String, float[]> amountRanges() {
        Map<String, float[]> m = new LinkedHashMap<>();
        m.put("light", new float[]{0f, 2f});
        m.put("sun_glow", new float[]{0f, 2f});
        m.put("ray_length", new float[]{0.25f, 2f});
        m.put("moon_size", new float[]{0.5f, 2.5f});
        m.put("moon_glow", new float[]{0f, 2f});
        m.put("particle_size", new float[]{0.5f, 2.5f});
        m.put("particle_speed", new float[]{0f, 3f});
        m.put("wind", new float[]{0f, 3f});
        m.put("bloom", new float[]{0f, 2.5f});
        m.put("contrast", new float[]{0.5f, 1.5f});
        m.put("colourful", new float[]{0f, 2f});
        m.put("vignette", new float[]{0f, 2f});
        return java.util.Collections.unmodifiableMap(m);
    }

    public boolean isNone() {
        return equals(NONE);
    }

    public Customisation withHue(float v) {
        return new Customisation(v, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withSat(float v) {
        return new Customisation(hue, v, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withDensity(float v) {
        return new Customisation(hue, sat, v, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withFog(Float v) {
        return new Customisation(hue, sat, density, v, sunSize, rays, particles, stars, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withSunSize(float v) {
        return new Customisation(hue, sat, density, fog, v, rays, particles, stars, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withRays(Integer v) {
        return new Customisation(hue, sat, density, fog, sunSize, v, particles, stars, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withParticle(String type, boolean on) {
        Map<String, Boolean> map = new LinkedHashMap<>(particles);
        if (on) {
            map.remove(type);
        } else {
            map.put(type, false);
        }
        return new Customisation(hue, sat, density, fog, sunSize, rays, map, stars, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withStars(float v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, v, planets, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withPlanets(Boolean v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, v, milkyWay, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withMilkyWay(Float v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, v, aurora, layers, colours, amounts, moonLook);
    }

    public Customisation withAurora(int[] v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, v, layers, colours, amounts, moonLook);
    }

    public Customisation withLayer(String layer, boolean on) {
        Map<String, Boolean> map = new LinkedHashMap<>(layers);
        if (on) {
            map.remove(layer);
        } else {
            map.put(layer, false);
        }
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, map, colours, amounts, moonLook);
    }

    public Customisation withLayers(Map<String, Boolean> v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, v, colours, amounts, moonLook);
    }

    /** A part's own colour ({@link #COLOURS}), or null to take it back to the theme's. */
    public Customisation withColour(String part, Integer colour) {
        Map<String, Integer> map = new LinkedHashMap<>(colours);
        if (colour == null) {
            map.remove(part);
        } else {
            map.put(part, colour);
        }
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, layers, map, amounts, moonLook);
    }

    /** How much of a part ({@link #AMOUNTS}); 1 is the theme's own. */
    public Customisation withAmount(String part, float v) {
        Map<String, Float> map = new LinkedHashMap<>(amounts);
        map.put(part, v);
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, layers, colours, map, moonLook);
    }

    /** The night's moon look (a {@code moon_looks} file's id), or null for the theme's. */
    public Customisation withMoonLook(String look) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora, layers, colours, amounts,
                look);
    }

    /** A part's own colour, or null while it has the theme's. */
    public Integer colour(String part) {
        return colours.get(part);
    }

    /** How much of a part, 1 for the theme's own. */
    public float amount(String part) {
        return amounts.getOrDefault(part, 1f);
    }

    /** Whether the theme shows a night sky layer ({@link #LAYERS}). */
    public boolean layerOn(String layer) {
        return layers.getOrDefault(layer, true);
    }

    public boolean particleOn(String type) {
        return particles.getOrDefault(type, true);
    }

    /** The hue shift that makes the theme's horizon glow take {@code color}'s hue (the brief's "Match my gear"). */
    public static float matchHue(int themeGlow, int color) {
        float want = Colors.toHsl(color)[0];
        float have = Colors.toHsl(themeGlow)[0];
        float dh = Math.round(((want - have) * 360f) / 5f) * 5f;
        dh = ((dh + 540f) % 360f + 360f) % 360f - 180f;
        return dh;
    }

    /** This customisation applied to one version of a theme, which is its own reference (the tests). */
    public ThemeSpec apply(ThemeSpec s) {
        return apply(s, s, s, false);
    }

    /**
     * This customisation applied to one time of day of a theme ({@code s}). {@code ref} is the theme's own values
     * (where a picked colour is exact), {@code nightRef} its night, and {@code night} whether {@code s} is a night
     * time of day (the night's colours apply there instead of the day's).
     */
    public ThemeSpec apply(ThemeSpec s, ThemeSpec ref, ThemeSpec nightRef, boolean night) {
        if (isNone()) {
            return s;
        }
        boolean shift = hue != 0f || sat != 1f;
        ThemeSpec.Ui ui = new ThemeSpec.Ui(col("accent", s.ui().accent(), ref.ui().accent()),
                colours.containsKey("panel") ? col("panel", s.ui().panel(), ref.ui().panel()) : s.ui().panel(), s.ui().rim());
        ThemeSpec.Sky k = s.sky(), kr = ref.sky(), kn = nightRef.sky();
        ThemeSpec.Sky sky = new ThemeSpec.Sky(sky("top", k.top(), kr.top(), kn.top(), night), sky("middle", k.middle(), kr.middle(),
                kn.middle(), night), sky("horizon", k.horizon(), kr.horizon(), kn.horizon(), night), col("sky_glow", k.glow(), kr.glow()),
                k.sunWarmth());
        ThemeSpec.Fog fogSpec = s.fog();
        int fogColour = col("fog", fogSpec.color(), ref.fog().color());
        if (fog != null) {
            float d = fog;
            float end = ThemeParser.defaultFogEnd(d);
            float start = end * (0.05f + 0.25f * (1f - d));
            // The fog slider moves fixed fog distances too: thicker fog comes closer.
            float kf = fogSpec.density() > 0f ? (1.15f - 0.8f * d) / Math.max(0.1f, 1.15f - 0.8f * fogSpec.density()) : 1f;
            fogSpec = new ThemeSpec.Fog(fogColour, d, start, end, fogSpec.voxyThinning(), fogSpec.bands(), fogSpec.near() * kf,
                    fogSpec.far() * kf, fogSpec.scatter(), fogSpec.haze());
        } else if (fogColour != fogSpec.color()) {
            fogSpec = new ThemeSpec.Fog(fogColour, fogSpec.density(), fogSpec.start(), fogSpec.end(), fogSpec.voxyThinning(),
                    fogSpec.bands(), fogSpec.near(), fogSpec.far(), fogSpec.scatter(), fogSpec.haze());
        }
        ThemeSpec.Light l0 = s.light(), lr = ref.light();
        ThemeSpec.Light light = new ThemeSpec.Light(col("light", l0.tint(), lr.tint()), Math.min(1f, l0.strength() * amount("light")),
                l0.boost(), col("sunlight", l0.sun(), lr.sun()), l0.shadow() == 0 ? 0 : f(l0.shadow()));
        ThemeSpec.Sun sun = s.sun();
        if (sun != null) {
            ThemeSpec.Sun sr = ref.sun() != null ? ref.sun() : sun;
            int r = rays != null ? rays : sun.rays();
            int coreRays = rays != null ? Math.round(sun.coreRays() * (sun.rays() == 0 ? 1f : (float) r / sun.rays())) : sun.coreRays();
            int core = colours.containsKey("sun_core") ? Colors.recolour(sun.core(), sr.core(), colours.get("sun_core")) : sun.core();
            sun = new ThemeSpec.Sun(sun.texture(), sun.size() * sunSize, core, col("sun_halo", sun.halo(), sr.halo()),
                    sun.haloRadius() * spread(amount("sun_glow")), Math.min(1f, sun.haloAlpha() * amount("sun_glow")), r, coreRays, sun.rayLength() * amount("ray_length"), sun.plasma(),
                    sun.corona(), sun.sunDogs(), sun.pillar(), sun.shimmer(), sun.flare(), sun.glare(), sun.orbitTilt(), sun.x(), sun.y());
        }
        ThemeSpec.Moon moon = s.moon();
        if (moon != null) {
            ThemeSpec.Moon mr = ref.moon() != null ? ref.moon() : moon;
            int mc = colours.containsKey("moon") ? Colors.recolour(moon.color(), mr.color(), colours.get("moon")) : moon.color();
            moon = new ThemeSpec.Moon(moon.size() * amount("moon_size"), mc, col("moon_halo", moon.halo(), mr.halo()),
                    moon.haloRadius() * spread(amount("moon_glow")), Math.min(1f, moon.haloAlpha() * amount("moon_glow")), moon.ring(), moon.dogs(), moon.x(), moon.y());
        }
        ThemeSpec.Stars st = s.stars();
        if (stars != 1f || milkyWay != null || colours.containsKey("stars")) {
            st = new ThemeSpec.Stars(Math.round(st.count() * stars), st.density() * stars, st.palette(),
                    colours.containsKey("stars") ? colours.get("stars") : st.tint(), milkyWay != null ? milkyWay : st.milkyWay(),
                    st.deepSky(), st.shooting(), st.meteorNights(), st.comets(), st.through());
        }
        ThemeSpec.Planets pl = s.planets();
        if (planets != null || colours.containsKey("planets")) {
            pl = new ThemeSpec.Planets(planets != null ? planets : pl.enabled(), pl.style(),
                    colours.containsKey("planets") ? colours.get("planets") : pl.tint(), pl.daytime());
        }
        ThemeSpec.Eclipse eclipse = s.eclipse();
        if (eclipse != null) {
            int ref0 = ref.eclipse() != null ? ref.eclipse().corona() : eclipse.corona();
            eclipse = new ThemeSpec.Eclipse(eclipse.x(), eclipse.y(), eclipse.size(), col("corona", eclipse.corona(), ref0));
        }
        int[] au = s.aurora();
        if (au != null) {
            au = aurora != null && aurora.length > 0 ? aurora.clone() : au.clone();
            if (aurora == null) {
                for (int i = 0; i < au.length; i++) {
                    au[i] = f(au[i]);
                }
            }
            for (int i = 0; i < Math.min(2, au.length); i++) {
                Integer c = colours.get("aurora_" + (i + 1));
                if (c != null) {
                    au[i] = c;
                }
            }
        }
        List<ThemeSpec.Nebula> nebula = s.nebula();
        if (!nebula.isEmpty() && (shift || colours.containsKey("nebula"))) {
            int ref0 = ref.nebula().isEmpty() ? nebula.get(0).color() : ref.nebula().get(0).color();
            List<ThemeSpec.Nebula> out = new ArrayList<>();
            for (ThemeSpec.Nebula n : nebula) {
                out.add(new ThemeSpec.Nebula(n.x(), n.y(), n.r(), col("nebula", n.color(), ref0)));
            }
            nebula = List.copyOf(out);
        }
        ThemeSpec.Clouds clouds = s.clouds();
        Integer cloudTint = clouds.tint();
        if (colours.containsKey("clouds")) {
            Integer refTint = ref.clouds().tint();
            cloudTint = cloudTint == null || refTint == null ? colours.get("clouds") : Colors.recolour(cloudTint, refTint, colours.get("clouds"));
        } else if (cloudTint != null) {
            cloudTint = f(cloudTint);
        }
        if (!java.util.Objects.equals(cloudTint, clouds.tint())) {
            clouds = new ThemeSpec.Clouds(cloudTint, clouds.litBySun(), clouds.visible());
        }
        List<ThemeSpec.Emitter> emit = s.particles();
        float size = amount("particle_size"), speed = amount("particle_speed");
        if (shift || density != 1f || !particles.isEmpty() || colours.containsKey("particles") || size != 1f || speed != 1f) {
            int ref0 = !ref.particles().isEmpty() && ref.particles().get(0).colors().length > 0 ? ref.particles().get(0).colors()[0] : 0;
            List<ThemeSpec.Emitter> out = new ArrayList<>();
            for (ThemeSpec.Emitter e : emit) {
                int[] cols = e.colors().clone();
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = col("particles", cols[i], ref0 == 0 ? cols[i] : ref0);
                }
                int count = particleOn(e.type()) ? Math.round(e.count() * density) : 0;
                out.add(new ThemeSpec.Emitter(e.type(), count, cols, e.minSize() * size, e.maxSize() * size, e.texture(), e.fall() * speed,
                        e.spin() * speed));
            }
            emit = List.copyOf(out);
        }
        float[] wind = s.wind();
        if (wind != null && amount("wind") != 1f) {
            wind = wind.clone();
            for (int i = 0; i < wind.length; i++) {
                wind[i] *= amount("wind");
            }
        }
        ThemeSpec.Grade grade = s.grade();
        if (grade != null && (shift || amount("bloom") != 1f || amount("contrast") != 1f || amount("colourful") != 1f
                || amount("vignette") != 1f)) {
            grade = new ThemeSpec.Grade(f(grade.highlights()), f(grade.shadows()), grade.saturation() * amount("colourful"),
                    grade.contrast() * amount("contrast"), f(grade.bloomTint()), grade.bloom() * amount("bloom"), grade.threshold(),
                    grade.vignette() * amount("vignette"), grade.lut());
        }
        ThemeSpec.Night nt = s.night();
        if (nt != null) {
            int moonlight = colours.containsKey("moonlight") ? colours.get("moonlight") : f(nt.light());
            int airglow = colours.containsKey("airglow") ? colours.get("airglow") : f(nt.airglow());
            int top = nt.top(), middle = nt.middle(), horizon = nt.horizon();
            if (nt.hasSky()) {
                top = colours.getOrDefault("night_top", f(top));
                middle = middle == 0 ? 0 : colours.getOrDefault("night_middle", f(middle));
                horizon = horizon == 0 ? 0 : colours.getOrDefault("night_horizon", f(horizon));
            }
            nt = new ThemeSpec.Night(moonLook != null ? moonLook : nt.moon(), moonlight, top, middle, horizon, airglow, nt.clouds(), nt.haze() == 0 ? 0 : f(nt.haze()));
        }
        return new ThemeSpec(s.id(), s.name(), s.line(), s.order(), ui, sky, fogSpec, light, sun, moon, st, pl, s.galaxy(), au,
                nebula, eclipse, s.lightning(), s.sigil(), s.mist(), clouds, wind, emit, s.show(), s.noRidges(),
                s.vanillaSun(), grade, nt);
    }

    /** A glow turned up past its own: as bright as it can be, then wider (so the top of the slider still does something). */
    private static float spread(float k) {
        return k > 1f ? 1f + (k - 1f) * 0.6f : 1f;
    }

    /** A colour of the theme's: recoloured where the player picked one for its part, else turned with the hue. */
    private int col(String part, int x, int ref) {
        Integer c = colours.get(part);
        // A palette (particles, nebulae) keeps its spread of hues round the picked one.
        return c != null ? Colors.recolour(x, ref, c, part.equals("particles") || part.equals("nebula")) : f(x);
    }

    /** One of the sky's three colours: the night's pick at night (from the night's own), else the day's. */
    private int sky(String which, int x, int ref, int nightRef, boolean night) {
        Integer n = colours.get("night_" + which);
        if (night && n != null) {
            return Colors.recolour(x, nightRef, n);
        }
        return col("sky_" + which, x, ref);
    }

    private int f(int color) {
        return Colors.shiftHue(color, hue, sat);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (hue != 0f) o.addProperty("hue", hue);
        if (sat != 1f) o.addProperty("sat", sat);
        if (density != 1f) o.addProperty("density", density);
        if (fog != null) o.addProperty("fog", fog);
        if (sunSize != 1f) o.addProperty("sunSize", sunSize);
        if (rays != null) o.addProperty("rays", rays);
        if (!particles.isEmpty()) {
            JsonObject p = new JsonObject();
            particles.forEach(p::addProperty);
            o.add("particles", p);
        }
        if (stars != 1f) o.addProperty("stars", stars);
        if (planets != null) o.addProperty("planets", planets);
        if (milkyWay != null) o.addProperty("milkyWay", milkyWay);
        if (aurora != null) {
            JsonArray a = new JsonArray();
            for (int c : aurora) {
                a.add(Colors.hex(c));
            }
            o.add("aurora", a);
        }
        if (!layers.isEmpty()) {
            JsonObject l = new JsonObject();
            layers.forEach(l::addProperty);
            JsonObject night = new JsonObject();
            night.add("layers", l);
            o.add("night", night);
        }
        if (!colours.isEmpty()) {
            JsonObject c = new JsonObject();
            colours.forEach((k, v) -> c.addProperty(k, Colors.hex(v)));
            o.add("colours", c);
        }
        if (!amounts.isEmpty()) {
            JsonObject a = new JsonObject();
            amounts.forEach(a::addProperty);
            o.add("amounts", a);
        }
        if (moonLook != null) {
            o.addProperty("moonLook", moonLook);
        }
        return o;
    }

    /** Reads a saved customisation; bad or missing values keep their defaults. */
    public static Customisation fromJson(JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return NONE;
        }
        JsonObject o = e.getAsJsonObject();
        Map<String, Boolean> parts = new LinkedHashMap<>();
        if (o.has("particles") && o.get("particles").isJsonObject()) {
            for (Map.Entry<String, JsonElement> p : o.getAsJsonObject("particles").entrySet()) {
                if (p.getValue().isJsonPrimitive() && p.getValue().getAsJsonPrimitive().isBoolean() && !p.getValue().getAsBoolean()) {
                    parts.put(p.getKey(), false);
                }
            }
        }
        int[] aurora = null;
        if (o.has("aurora") && o.get("aurora").isJsonArray()) {
            List<Integer> list = new ArrayList<>();
            for (JsonElement c : o.getAsJsonArray("aurora")) {
                Integer parsed = c.isJsonPrimitive() ? Colors.tryParse(c.getAsString()) : null;
                if (parsed != null) {
                    list.add(parsed);
                }
            }
            aurora = list.isEmpty() ? null : list.stream().mapToInt(Integer::intValue).toArray();
        }
        return new Customisation(num(o, "hue", 0f), num(o, "sat", 1f), num(o, "density", 1f),
                o.has("fog") ? num(o, "fog", 0.5f) : null, num(o, "sunSize", 1f),
                o.has("rays") ? (int) num(o, "rays", 14f) : null, parts, num(o, "stars", 1f),
                o.has("planets") && o.get("planets").isJsonPrimitive() ? o.get("planets").getAsBoolean() : null,
                o.has("milkyWay") ? num(o, "milkyWay", 0.5f) : null, aurora, offs(o), colours(o), amounts(o),
                o.has("moonLook") && o.get("moonLook").isJsonPrimitive() ? o.get("moonLook").getAsString() : null);
    }

    /** The parts' own colours ({@code "colours": {"sky_top": "#3a1c71"}}); unknown parts and bad colours are left out. */
    private static Map<String, Integer> colours(JsonObject o) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (o.has("colours") && o.get("colours").isJsonObject()) {
            for (Map.Entry<String, JsonElement> c : o.getAsJsonObject("colours").entrySet()) {
                Integer parsed = COLOURS.contains(c.getKey()) && c.getValue().isJsonPrimitive() ? Colors.tryParse(c.getValue().getAsString()) : null;
                if (parsed != null) {
                    out.put(c.getKey(), parsed);
                }
            }
        }
        return out;
    }

    /** How much of each part ({@code "amounts": {"bloom": 1.4}}). */
    private static Map<String, Float> amounts(JsonObject o) {
        Map<String, Float> out = new LinkedHashMap<>();
        if (o.has("amounts") && o.get("amounts").isJsonObject()) {
            JsonObject a = o.getAsJsonObject("amounts");
            for (String key : a.keySet()) {
                if (AMOUNTS.containsKey(key)) {
                    out.put(key, num(a, key, 1f));
                }
            }
        }
        return out;
    }

    /** The night sky layers switched off ({@code "night": {"layers": {"meteors": false}}}). */
    private static Map<String, Boolean> offs(JsonObject o) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        if (o.has("night") && o.get("night").isJsonObject() && o.getAsJsonObject("night").has("layers")
                && o.getAsJsonObject("night").get("layers").isJsonObject()) {
            for (Map.Entry<String, JsonElement> l : o.getAsJsonObject("night").getAsJsonObject("layers").entrySet()) {
                if (LAYERS.contains(l.getKey()) && l.getValue().isJsonPrimitive() && l.getValue().getAsJsonPrimitive().isBoolean()
                        && !l.getValue().getAsBoolean()) {
                    out.put(l.getKey(), false);
                }
            }
        }
        return out;
    }

    private static float num(JsonObject o, String key, float fallback) {
        try {
            float v = o.has(key) ? o.get(key).getAsFloat() : fallback;
            return Float.isFinite(v) ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Customisation c && hue == c.hue && sat == c.sat && density == c.density
                && java.util.Objects.equals(fog, c.fog) && sunSize == c.sunSize && java.util.Objects.equals(rays, c.rays)
                && particles.equals(c.particles) && stars == c.stars && java.util.Objects.equals(planets, c.planets)
                && java.util.Objects.equals(milkyWay, c.milkyWay) && java.util.Arrays.equals(aurora, c.aurora)
                && layers.equals(c.layers) && colours.equals(c.colours) && amounts.equals(c.amounts)
                && java.util.Objects.equals(moonLook, c.moonLook);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, layers, colours, amounts, moonLook)
                * 31 + java.util.Arrays.hashCode(aurora);
    }
}
