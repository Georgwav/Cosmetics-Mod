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
        return (vanilla & 0xFF000000) | (c & 0x00FFFFFF);
    }

    /** Tints the sky light's colour in place (x, y, z = red, green, blue, 0..1). */
    public static void light(Vector3f sky) {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !on(at.light.get()) || !Guard.on(Feature.LIGHT)) {
            return;
        }
        Tokens tk = ThemeState.tokens();
        float s = tk.lightStrength;
        if (s <= 0f) {
            return;
        }
        int t = tk.lightTint;
        sky.x *= 1f - s + s * Colors.r(t) / 255f;
        sky.y *= 1f - s + s * Colors.g(t) / 255f;
        sky.z *= 1f - s + s * Colors.b(t) / 255f;
    }

    /** The tinted sky light colour as an RGB int (1.21.11's attributes). */
    public static int lightRgb(int rgb) {
        Vector3f v = new Vector3f(Colors.r(rgb) / 255f, Colors.g(rgb) / 255f, Colors.b(rgb) / 255f);
        light(v);
        return (rgb & 0xFF000000) | Colors.rgb(Math.round(v.x * 255f), Math.round(v.y * 255f), Math.round(v.z * 255f)) & 0x00FFFFFF;
    }
}
