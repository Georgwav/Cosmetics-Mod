package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import org.joml.Vector3f;

/**
 * The theme's tint on the world: the clouds (tinted, and lit by a low sun) and the light over every block (the
 * sky light's colour pulled toward the theme's light tint, so Ember's sand reads gold). The game's own brightness
 * through the day stays; only the colour moves.
 */
public final class WorldTint {
    private WorldTint() {
    }

    private static boolean on(boolean setting) {
        return setting && Atmosphere.active() && FogControl.activeSky();
    }

    /** The cloud colour (ARGB) for the game's {@code vanilla} cloud colour. */
    public static int cloud(int vanilla) {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !on(at.clouds.get()) || !Guard.on(Feature.CLOUDS)) {
            return vanilla;
        }
        Tokens tk = ThemeState.tokens();
        int c = vanilla;
        if (tk.cloudTint != 0) {
            c = Colors.mixRgb(c, Colors.multiply(c, tk.cloudTint | 0xFF000000), 0.85f);
        }
        if (tk.cloudsLitBySun) {
            float low = (1f - SkyPass.sunE) * Ease.smoothstep(-8f, 2f, SkyPass.sunAltitude);
            if (low > 0f) {
                c = Colors.mixRgb(c, tk.skyGlow, low * 0.4f);
            }
        }
        // Monsoon's overcast breaks up at night: the blocky clouds melt into the sky as its own broken cloud comes.
        float gone = nightFade();
        if (gone > 0f) {
            c = Colors.mixRgb(c, tk.skyMiddle, gone);
        }
        return (vanilla & 0xFF000000) | (c & 0x00FFFFFF);
    }

    /** How far the game's clouds have given way to the theme's night cloud (NightClouds), 0..1. */
    private static float nightFade() {
        if (!ThemeState.entry().base().night().clouds()) {
            return 0f;
        }
        return Ease.smoothstep(-3f, -12f, NightSky.SPHERE.sunAlt);
    }

    /** Whether the game's clouds should be drawn now: hidden while a theme with a clear sky is on. */
    public static boolean cloudsShown() {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !at.clouds.get() || !Atmosphere.active() || !FogControl.activeSky()) {
            return true;
        }
        return ThemeState.tokens().cloudsVisible && nightFade() < 0.97f;
    }

    /**
     * The sky light's brightness from 1.21.11 (the game's sky light factor), raised by the theme's golden-hour boost
     * so a low sun still lights the sand.
     */
    public static float lightFactor(float vanilla) {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !on(at.light.get()) || !Guard.on(Feature.LIGHT)) {
            return vanilla;
        }
        return Math.min(1f, vanilla * ThemeState.tokens().lightBoost * SkyTime.worldLight);
    }

    /**
     * Tints the sky light's colour in place (x, y, z = red, green, blue, 0..1) and brightens it by the theme's
     * golden-hour boost, so a low sun lights the blocks warm instead of greying them (the game clamps it later).
     */
    public static void light(Vector3f sky) {
        light(sky, true);
    }

    /** The tint, and the boost too unless the version brightens through its sky light factor instead. */
    public static void light(Vector3f sky, boolean withBoost) {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !on(at.light.get()) || !Guard.on(Feature.LIGHT)) {
            return;
        }
        Tokens tk = ThemeState.tokens();
        float s = tk.lightStrength;
        // A held night shown by day darkens the light too (SkyTime), with the boost.
        float boost = withBoost ? tk.lightBoost * SkyTime.worldLight : 1f;
        if (s <= 0f && Math.abs(boost - 1f) < 1e-3f) {
            return;
        }
        // The light keeps the game's brightness but takes the theme's hue (the game's own sunset light is pink;
        // Ember's is gold), then golden hour brightens it.
        int t = tk.lightTint;
        float tr = Colors.r(t) / 255f, tg = Colors.g(t) / 255f, tb = Colors.b(t) / 255f;
        float tl = Math.max(0.05f, 0.2126f * tr + 0.7152f * tg + 0.0722f * tb);
        float l = 0.2126f * sky.x + 0.7152f * sky.y + 0.0722f * sky.z;
        sky.x = (sky.x + (l * tr / tl - sky.x) * s) * boost;
        sky.y = (sky.y + (l * tg / tl - sky.y) * s) * boost;
        sky.z = (sky.z + (l * tb / tl - sky.z) * s) * boost;
    }

    private static final Vector3f scratch = new Vector3f();

    /** The tinted sky light colour as an RGB int (1.21.11's attributes). */
    public static int lightRgb(int rgb) {
        Vector3f v = scratch.set(Colors.r(rgb) / 255f, Colors.g(rgb) / 255f, Colors.b(rgb) / 255f);
        light(v, false);
        return (rgb & 0xFF000000) | Colors.rgb(Math.min(255, Math.round(v.x * 255f)), Math.min(255, Math.round(v.y * 255f)),
                Math.min(255, Math.round(v.z * 255f))) & 0x00FFFFFF;
    }
}
