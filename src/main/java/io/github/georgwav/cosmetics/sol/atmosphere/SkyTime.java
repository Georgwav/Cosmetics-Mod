package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.theme.Keyframes;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.util.Ease;

/**
 * The time of day the Sol sky shows. Usually the world's; a theme that holds its showpiece (the night themes, the
 * eclipse) is shown at its own time at every hour: its sky, sun or moon, stars and light stay as at its showpiece,
 * and the world is drawn darker when that time is darker than the world's. Only what you see changes: the world's
 * time, mobs and farms are left alone.
 */
public final class SkyTime {
    /** The world's own time of day (0 noon, 0.25 sunset, 0.5 midnight), this frame. */
    public static float real;
    /** The time the sky shows this frame. */
    public static float shown;
    /** 1, or less while a held night is shown by day: the factor the sky light on the blocks is scaled by. */
    public static float worldLight = 1f;

    private SkyTime() {
    }

    /** The time of day the sky shows for the world's {@code timeOfDay}. */
    public static float of(float timeOfDay) {
        if (!Atmosphere.active()) {
            return timeOfDay;
        }
        ThemeSpec.Show s = ThemeState.entry().base().show();
        return s.hold() ? s.heldTime() : timeOfDay;
    }

    /** The sun angle (radians) the sky is drawn with, for the game's {@code sunAngle}. */
    public static float angle(float sunAngle) {
        float tod = sunAngle / (float) (Math.PI * 2.0);
        float shownTod = of(tod - (float) Math.floor(tod));
        return shownTod * (float) (Math.PI * 2.0);
    }

    /** Once a frame, with the world's time of day; {@code intensity} (0..1) tones the darkening down. */
    public static void update(float timeOfDay, float intensity) {
        real = timeOfDay;
        shown = of(timeOfDay);
        float darker = Math.max(0f, daylight(real) - daylight(shown));
        // A held night by day looks like a night you can still play in: about as dark as the game's own night.
        worldLight = 1f - 0.9f * darker * (0.45f + 0.55f * intensity);
    }

    /** 0 at night, 1 by day: how much daylight the game gives at a time of day. */
    static float daylight(float timeOfDay) {
        return Ease.smoothstep(-6f, 12f, Keyframes.altitude(timeOfDay));
    }
}
