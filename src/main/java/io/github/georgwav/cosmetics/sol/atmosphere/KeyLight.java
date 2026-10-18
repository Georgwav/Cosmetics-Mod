package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;

/**
 * The light that lights the world in each theme, as the low sun lights Ember's sand: where it is, its colour, how
 * strongly it lights the faces turned toward it (the rest fall into the theme's shadow colour), whether it throws
 * light shafts and rims, and the colour the fog scatters toward it. The light pass reads it every frame.
 *
 * <ul>
 *   <li>The sun themes: the sun, strongest when low; at night the moon, in the theme's night light.</li>
 *   <li>Grace: the rune is the sun (gold light and shafts through the green fog, gold rims).</li>
 *   <li>Eclipse: the burning ring, red-orange, with hot rims.</li>
 *   <li>Aurora: the ribbons, their light moving over the ground and turning from green to violet.</li>
 *   <li>Nebula: pink and violet light from high above.</li>
 *   <li>Deep Field: the galaxy's cold silver light.</li>
 *   <li>Silver Night: the huge moon.</li>
 *   <li>Monsoon: the overcast's dull light, and each lightning flash lighting the world from the bolt.</li>
 * </ul>
 */
public final class KeyLight {
    /** Direction toward the light (world space, unit length). */
    public static float x, y = 1f, z;
    /** Its colour on the faces turned toward it, and the colour the fog scatters toward it (ARGB). */
    public static int color = 0xFFFFFFFF, glow = 0xFFFFFFFF;
    /** 0..1: how strongly it lights the world (Ember's low sun is 1). */
    public static float strength;
    /** 0..1: how much of the light's source shows in the sky (the shafts and rims start there). */
    public static float visible;
    /** Multipliers on the light shafts and on the silhouette rim; the rim on faces at a grazing angle (0..1). */
    public static float shafts = 1f, rim = 1f, faceRim;
    /**
     * 0..1: a thin line of the light's glow along every skyline, not only toward the light (the prototypes' lit
     * horizon edges at night); a low sun only rims what stands against it.
     */
    public static float rimAll;
    /** 0..1: a lightning flash lighting the whole world this frame. */
    public static float flash;
    /**
     * A second light colour and how strongly the two sweep across the ground in slow bands (the aurora's green and
     * violet, the nebula's pink and violet); 0 lights the world in {@link #color} alone.
     */
    public static int color2 = 0xFFFFFFFF;
    public static float bands;
    /**
     * A second light: the moon, where the theme's own light is something else (the rune, the ring, the ribbons,
     * the galaxy, the storm). Direction toward it (world space), colour and strength (0..1).
     */
    public static float extraX, extraY = 1f, extraZ, extraStrength;
    public static int extraColor = 0xFFB8CCFF;

    private static final float[] dir = new float[3];

    private KeyLight() {
    }

    /** Works out the theme's light from the sky drawn this frame (SkyPass calls it after the sky). */
    static void update(ThemeSpec theme, Tokens tk, float t, float rain) {
        flash = 0f;
        faceRim = 0f;
        rimAll = 0f;
        bands = 0f;
        // The moon as the second light; the sun themes take it as their own at night (below), and drop it here.
        float[] mv = NightSky.SPHERE.moon;
        extraX = mv[0];
        extraY = mv[1];
        extraZ = mv[2];
        extraColor = NightSky.moonColor;
        extraStrength = 0.6f * Math.min(1f, NightSky.moonLight) * NightSky.dark * (1f - rain * 0.7f);
        if (theme.sigil() != null) {
            ThemeSpec.Sigil g = theme.sigil();
            Extras.directionOf(g.x(), g.y(), theme.show().azimuth(), false, dir);
            set(dir);
            color = Colors.mix(g.color(), 0xFFFFF4D8, 0.15f);
            glow = Colors.mix(g.halo(), g.color(), 0.55f);
            strength = 1f - rain * 0.4f;
            visible = 1f - rain * 0.5f;
            shafts = 1.35f;
            rim = 1.6f;
            faceRim = 0.85f;
            rimAll = 0.7f;
        } else if (theme.eclipse() != null) {
            set(SkyPass.sunX, SkyPass.sunY, SkyPass.sunZ);
            color = Colors.mix(theme.eclipse().corona(), 0xFFFFB080, 0.25f);
            glow = Colors.mix(theme.eclipse().corona(), tk.skyGlow, 0.5f);
            strength = 0.95f * SkyPass.sunVisible;
            visible = SkyPass.sunVisible;
            shafts = 1.1f;
            rim = 2f;
            faceRim = 1f;
            rimAll = 1f;
        } else if (theme.aurora() != null && theme.aurora().length > 0) {
            // The ribbons hang to the north a quarter of the way up; their light sways along them and turns
            // between their colours.
            int[] c = theme.aurora();
            float az = theme.show().azimuth() + 28f * (float) Math.sin(t * 0.06f);
            setAltAz(24f, az);
            float k = 0.5f + 0.5f * (float) Math.sin(t * 0.17f);
            int ribbon = c.length > 1 ? Colors.mix(c[0], c[1], k) : c[0];
            color = Colors.mix(ribbon, 0xFFFFFFFF, 0.2f);
            color2 = Colors.mix(c.length > 1 ? Colors.mix(c[0], c[1], 1f - k) : c[0], 0xFFFFFFFF, 0.2f);
            bands = 1f;
            glow = ribbon;
            strength = 0.9f * tk.night * (1f - rain * 0.6f);
            visible = 0.55f * tk.night;
            shafts = 0.55f;
            rim = 1.2f;
            faceRim = 0.7f;
            rimAll = 0.6f;
        } else if (!theme.nebula().isEmpty()) {
            // The nebula's light falls from high above, pink then violet.
            setAltAz(64f, theme.show().azimuth());
            float k = 0.5f + 0.5f * (float) Math.sin(t * 0.11f);
            int pink = theme.nebula().size() > 1 ? theme.nebula().get(1).color() : 0xFFFF4FA3;
            int violet = theme.nebula().get(0).color();
            color = Colors.mix(Colors.mix(pink, violet, k), 0xFFFFFFFF, 0.3f);
            color2 = Colors.mix(Colors.mix(pink, violet, 1f - k), 0xFFFFFFFF, 0.3f);
            bands = 0.7f;
            glow = Colors.mix(pink, violet, k);
            strength = 0.8f * tk.night * (1f - rain * 0.6f);
            visible = 0.5f * tk.night;
            shafts = 0.45f;
            rim = 1.1f;
            faceRim = 0.6f;
            rimAll = 0.7f;
        } else if (theme.galaxy() != null) {
            ThemeSpec.Galaxy g = theme.galaxy();
            Extras.directionOf(g.x(), g.y(), theme.show().azimuth(), true, dir);
            set(dir);
            color = 0xFFE8EEFF;
            glow = 0xFFF4F6FF;
            strength = 0.85f * tk.night * (1f - rain * 0.6f);
            visible = 0.75f * tk.night;
            shafts = 0.7f;
            rim = 1.6f;
            faceRim = 0.9f;
            rimAll = 1f;
        } else if (theme.lightning()) {
            // The overcast lights flatly from above; a flash lights the world from the bolt.
            float f = Extras.flash / 0.55f;
            float alt = Extras.boltAltitude(), az = Extras.boltAzimuth();
            if (f > 0.02f && !Float.isNaN(alt)) {
                setAltAz(Math.max(12f, alt), az);
            } else {
                set(0.2f, 1f, 0.1f);
            }
            color = Colors.mix(tk.lightSun, 0xFFE4ECFF, Math.min(1f, f));
            glow = Colors.mix(tk.skyGlow, 0xFFD8E4FF, Math.min(1f, f));
            strength = 0.18f + 0.82f * Math.min(1f, f);
            visible = 0f;
            shafts = 0f;
            rim = 1f;
            faceRim = 0.8f * Math.min(1f, f);
            rimAll = 0.9f * Math.min(1f, f);
            flash = Math.min(1f, f);
        } else {
            sunOrMoon(theme, tk, rain);
        }
        strength = Ease.clamp01(strength);
        if (bands <= 0f) {
            color2 = color;
        }
    }

    /** The sun, strongest low in the sky; below the horizon the moon takes over in the theme's night light. */
    private static void sunOrMoon(ThemeSpec theme, Tokens tk, float rain) {
        float alt = SkyPass.sunAltitude;
        float golden = Ease.smoothstep(-6f, 2f, alt) * (1f - 0.65f * Ease.smoothstep(12f, 40f, alt));
        float sunShare = Ease.smoothstep(-8f, -1f, alt);
        boolean moonTheme = theme.moon() != null && theme.nightTheme();
        if (sunShare >= 0.5f && theme.sun() != null && !moonTheme) {
            set(SkyPass.sunX, SkyPass.sunY, SkyPass.sunZ);
            color = tk.lightSun;
            glow = tk.skyGlow;
            strength = golden;
            visible = SkyPass.sunVisible;
            shafts = 0.55f + 0.45f * (1f - SkyPass.sunE);
            rim = 1f;
            faceRim = 0.45f * golden;
            return;
        }
        // The moon: where it is on the sky sphere, as bright as its phase and tonight's look make it (a new moon
        // leaves the night dark), in the colour of its light.
        extraStrength = 0f;
        float[] mv = NightSky.SPHERE.moon;
        set(mv[0], mv[1], mv[2]);
        float moonAlt = NightSky.SPHERE.moonAlt;
        float up = Ease.smoothstep(-4f, 6f, moonAlt);
        float ml = Math.min(1f, NightSky.moonLight);
        color = Colors.mix(tk.lightTint, NightSky.moonColor, 0.75f);
        glow = Colors.mix(NightSky.moonColor, tk.skyGlow, 0.3f);
        strength = (moonTheme ? 0.95f : 0.8f) * ml * Math.max(tk.night, moonTheme ? 1f : 0f) * (1f - rain * 0.7f);
        visible = NightSky.hasMoon ? up * (1f - rain * 0.8f) * (0.4f + 0.6f * ml) : 0f;
        shafts = moonTheme ? 0.8f : 0.4f;
        rim = moonTheme ? 1.4f : 0.8f;
        faceRim = moonTheme ? 0.75f : 0.35f;
        rimAll = (moonTheme ? 0.85f : 0.35f) * up;
    }

    private static void setAltAz(float altitude, float azimuth) {
        double al = Math.toRadians(altitude), az = Math.toRadians(azimuth);
        float h = (float) Math.cos(al);
        set((float) (-Math.sin(az) * h), (float) Math.sin(al), (float) (Math.cos(az) * h));
    }

    private static void set(float[] d) {
        set(d[0], d[1], d[2]);
    }

    private static void set(float dx, float dy, float dz) {
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-5f) {
            x = 0f;
            y = 1f;
            z = 0f;
            return;
        }
        x = dx / l;
        y = dy / l;
        z = dz / l;
    }

    /** No Sol sky this frame: no theme light. */
    static void none() {
        extraStrength = 0f;
        bands = 0f;
        strength = 0f;
        visible = 0f;
        flash = 0f;
        faceRim = 0f;
    }

    /** For the record: which light is on (the scenario's log). */
    public static String describe() {
        return String.format(java.util.Locale.ROOT, "dir(%.2f,%.2f,%.2f) strength %.2f visible %.2f", x, y, z, strength,
                visible) + (FrameInfo.inWorld ? "" : " (no world)");
    }
}
