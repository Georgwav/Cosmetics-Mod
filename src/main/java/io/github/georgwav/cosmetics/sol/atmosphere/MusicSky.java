package io.github.georgwav.cosmetics.sol.atmosphere;

/**
 * What the music visualizers change in the sky: the band's brightness, the stars' pulse, the sun's beat, the album
 * sun. Neutral until the Music module publishes a spectrum (see {@code sol.music.Visualizers}).
 */
public final class MusicSky {
    /** Set by the music module on the render thread: level and beat (0..1), high-band energy, the album sun pixels. */
    public static float level;
    public static float beat;
    public static float high;
    public static boolean stars;
    public static boolean aurora;
    public static float bass;
    public static boolean breathe;
    public static boolean sunBeat;
    public static int[] album;
    /** Music notes join the theme's particles. */
    public static boolean notes;
    /** The horizon EQ's columns (0..1 each), or null when it's off. */
    public static float[] eq;
    /** How far the latest beat ring has spread from the sun, 0..1 (more: none showing). */
    public static float ringPhase = 2f;

    private MusicSky() {
    }

    /** Extra band alpha: +0.35·level +0.15·beat while sky breathing is on. */
    public static float bandBoost() {
        return breathe ? level * 0.35f + beat * 0.15f : 0f;
    }

    /** Star alpha multiplier for a star of size {@code size} while the star pulse is on. */
    public static float starBoost(float size) {
        if (!stars) {
            return 1f;
        }
        return Math.min(2.2f, 0.45f + high * 1.1f + beat * (size > 1f ? 0.6f : 0.25f));
    }

    /** Bass makes the aurora swing wider and brighter (0 when the visualizer is off). */
    public static float auroraBoost() {
        return aurora ? bass * 1.6f : 0f;
    }

    /** The sun grows 6% on a beat. */
    public static float sunPulse() {
        return sunBeat ? 1f + beat * 0.06f : 1f;
    }

    /**
     * The lyric line written on the night sky (null: none) and how much of it is sung, 0..1 by its letters (-1: its
     * words aren't timed).
     */
    public static void skyLine(String line, float sung) {
        SkyLyrics.feed(line, sung);
    }

    /** The beat ring's phase 0..1, or -1 when none shows. */
    public static float ring() {
        return ringPhase < 1f ? ringPhase : -1f;
    }

    /** The 16x16 album sun, or null for the plasma. */
    public static int[] albumSun() {
        return album;
    }
}
