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
 * the aurora's colours. Applied over every time of day of the theme. Immutable; {@link #NONE} changes nothing.
 */
public record Customisation(float hue, float sat, float density, Float fog, float sunSize, Integer rays,
                            Map<String, Boolean> particles, float stars, Boolean planets, Float milkyWay, int[] aurora) {
    public static final Customisation NONE = new Customisation(0f, 1f, 1f, null, 1f, null, Map.of(), 1f, null, null, null);

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
    }

    public boolean isNone() {
        return equals(NONE);
    }

    public Customisation withHue(float v) {
        return new Customisation(v, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora);
    }

    public Customisation withSat(float v) {
        return new Customisation(hue, v, density, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora);
    }

    public Customisation withDensity(float v) {
        return new Customisation(hue, sat, v, fog, sunSize, rays, particles, stars, planets, milkyWay, aurora);
    }

    public Customisation withFog(Float v) {
        return new Customisation(hue, sat, density, v, sunSize, rays, particles, stars, planets, milkyWay, aurora);
    }

    public Customisation withSunSize(float v) {
        return new Customisation(hue, sat, density, fog, v, rays, particles, stars, planets, milkyWay, aurora);
    }

    public Customisation withRays(Integer v) {
        return new Customisation(hue, sat, density, fog, sunSize, v, particles, stars, planets, milkyWay, aurora);
    }

    public Customisation withParticle(String type, boolean on) {
        Map<String, Boolean> map = new LinkedHashMap<>(particles);
        if (on) {
            map.remove(type);
        } else {
            map.put(type, false);
        }
        return new Customisation(hue, sat, density, fog, sunSize, rays, map, stars, planets, milkyWay, aurora);
    }

    public Customisation withStars(float v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, v, planets, milkyWay, aurora);
    }

    public Customisation withPlanets(Boolean v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, v, milkyWay, aurora);
    }

    public Customisation withMilkyWay(Float v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, v, aurora);
    }

    public Customisation withAurora(int[] v) {
        return new Customisation(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay, v);
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

    /** This customisation applied to one version of a theme. */
    public ThemeSpec apply(ThemeSpec s) {
        if (isNone()) {
            return s;
        }
        boolean shift = hue != 0f || sat != 1f;
        ThemeSpec.Ui ui = shift ? new ThemeSpec.Ui(f(s.ui().accent()), s.ui().panel(), s.ui().rim()) : s.ui();
        ThemeSpec.Sky sky = shift ? new ThemeSpec.Sky(f(s.sky().top()), f(s.sky().middle()), f(s.sky().horizon()),
                f(s.sky().glow()), s.sky().sunWarmth()) : s.sky();
        ThemeSpec.Fog fogSpec = s.fog();
        if (shift || fog != null) {
            float d = fog != null ? fog : fogSpec.density();
            float end = fog != null ? ThemeParser.defaultFogEnd(d) : fogSpec.end();
            float start = fog != null ? end * (0.05f + 0.25f * (1f - d)) : fogSpec.start();
            // The fog slider moves fixed fog distances too: thicker fog comes closer.
            float k = fog != null && fogSpec.density() > 0f ? (1.15f - 0.8f * d) / Math.max(0.1f, 1.15f - 0.8f * fogSpec.density()) : 1f;
            fogSpec = new ThemeSpec.Fog(shift ? f(fogSpec.color()) : fogSpec.color(), d, start, end, fogSpec.voxyThinning(),
                    fogSpec.bands(), fogSpec.near() * k, fogSpec.far() * k, fogSpec.scatter(), fogSpec.haze());
        }
        ThemeSpec.Light l0 = s.light();
        ThemeSpec.Light light = shift ? new ThemeSpec.Light(f(l0.tint()), l0.strength(), l0.boost(), f(l0.sun()),
                l0.shadow() == 0 ? 0 : f(l0.shadow())) : l0;
        ThemeSpec.Sun sun = s.sun();
        if (sun != null && (shift || sunSize != 1f || rays != null)) {
            int r = rays != null ? rays : sun.rays();
            int coreRays = rays != null ? Math.round(sun.coreRays() * (sun.rays() == 0 ? 1f : (float) r / sun.rays())) : sun.coreRays();
            sun = new ThemeSpec.Sun(sun.texture(), sun.size() * sunSize, sun.core(), shift ? f(sun.halo()) : sun.halo(),
                    sun.haloRadius(), sun.haloAlpha(), r, coreRays, sun.rayLength(), sun.plasma(), sun.corona(), sun.sunDogs(),
                    sun.pillar(), sun.shimmer(), sun.flare(), sun.glare(), sun.orbitTilt(), sun.x(), sun.y());
        }
        ThemeSpec.Moon moon = s.moon();
        if (moon != null && shift) {
            moon = new ThemeSpec.Moon(moon.size(), moon.color(), f(moon.halo()), moon.haloRadius(), moon.haloAlpha(),
                    moon.ring(), moon.dogs(), moon.x(), moon.y());
        }
        ThemeSpec.Stars st = s.stars();
        if (stars != 1f || milkyWay != null) {
            st = new ThemeSpec.Stars(Math.round(st.count() * stars), st.density() * stars, st.palette(), st.tint(),
                    milkyWay != null ? milkyWay : st.milkyWay(), st.deepSky(), st.shooting(), st.meteorNights(), st.comets(),
                    st.through());
        }
        ThemeSpec.Planets pl = s.planets();
        if (planets != null) {
            pl = new ThemeSpec.Planets(planets, pl.style(), pl.tint(), pl.daytime());
        }
        ThemeSpec.Eclipse eclipse = s.eclipse();
        if (eclipse != null && shift) {
            eclipse = new ThemeSpec.Eclipse(eclipse.x(), eclipse.y(), eclipse.size(), f(eclipse.corona()));
        }
        int[] au = s.aurora();
        if (aurora != null && aurora.length > 0 && au != null) {
            au = aurora.clone();
        } else if (au != null && shift) {
            au = au.clone();
            for (int i = 0; i < au.length; i++) {
                au[i] = f(au[i]);
            }
        }
        List<ThemeSpec.Nebula> nebula = s.nebula();
        if (shift && !nebula.isEmpty()) {
            List<ThemeSpec.Nebula> out = new ArrayList<>();
            for (ThemeSpec.Nebula n : nebula) {
                out.add(new ThemeSpec.Nebula(n.x(), n.y(), n.r(), f(n.color())));
            }
            nebula = List.copyOf(out);
        }
        ThemeSpec.Clouds clouds = s.clouds();
        if (shift && clouds.tint() != null) {
            clouds = new ThemeSpec.Clouds(f(clouds.tint()), clouds.litBySun(), clouds.visible());
        }
        List<ThemeSpec.Emitter> emit = s.particles();
        if (shift || density != 1f || !particles.isEmpty()) {
            List<ThemeSpec.Emitter> out = new ArrayList<>();
            for (ThemeSpec.Emitter e : emit) {
                int[] cols = e.colors();
                if (shift) {
                    cols = cols.clone();
                    for (int i = 0; i < cols.length; i++) {
                        cols[i] = f(cols[i]);
                    }
                }
                int count = particleOn(e.type()) ? Math.round(e.count() * density) : 0;
                out.add(new ThemeSpec.Emitter(e.type(), count, cols, e.minSize(), e.maxSize(), e.texture(), e.fall(), e.spin()));
            }
            emit = List.copyOf(out);
        }
        return new ThemeSpec(s.id(), s.name(), s.line(), s.order(), ui, sky, fogSpec, light, sun, moon, st, pl, s.galaxy(), au,
                nebula, eclipse, s.lightning(), s.sigil(), s.mist(), clouds, s.wind(), emit, s.show(), s.noRidges(),
                s.vanillaSun(), s.grade(), s.night());
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
                o.has("milkyWay") ? num(o, "milkyWay", 0.5f) : null, aurora);
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
                && java.util.Objects.equals(milkyWay, c.milkyWay) && java.util.Arrays.equals(aurora, c.aurora);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(hue, sat, density, fog, sunSize, rays, particles, stars, planets, milkyWay)
                * 31 + java.util.Arrays.hashCode(aurora);
    }
}
