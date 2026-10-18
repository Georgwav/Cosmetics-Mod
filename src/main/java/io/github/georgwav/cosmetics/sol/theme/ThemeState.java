package io.github.georgwav.cosmetics.sol.theme;

import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Spring;

/**
 * Owns the theme on the render thread: the profile's theme and its customisation, an accent override, the album
 * theme while one is on, the 0.8 s change between themes, and the time of day blend. Publishes {@link Tokens}.
 * Every method runs on the render thread.
 */
public final class ThemeState {
    /** How long a theme change takes, in seconds (the prototypes' sweep). */
    public static final float CHANGE_SECONDS = 0.8f;

    private static String themeId = ThemeRegistry.FALLBACK;
    private static Customisation custom = Customisation.NONE;
    private static Integer accent;
    private static String albumKey;
    private static java.util.function.UnaryOperator<ThemeSpec> album;

    private static ThemeRegistry.Entry entry;
    private static int entryGeneration = -1;
    private static boolean dirty = true;

    private static final Tokens current = new Tokens();
    private static final Tokens target = new Tokens();
    private static final Tokens from = new Tokens();
    private static final float[] weights = new float[ThemeRegistry.Phase.values().length];
    private static float lastTime = Float.NaN;
    private static long changeStart = -1L;
    private static int generation;

    private ThemeState() {
    }

    public static Tokens tokens() {
        return current;
    }

    /** The tokens from before the latest theme change, for the sweep; only meaningful while {@link #change()} &lt; 1. */
    public static Tokens previous() {
        return from;
    }

    /** How far the latest theme change is, 0..1, or 1 when none is running. */
    public static float change() {
        if (changeStart < 0L) {
            return 1f;
        }
        float k = (System.nanoTime() - changeStart) / 1e9f / CHANGE_SECONDS;
        return Math.min(1f, Math.max(0f, k));
    }

    public static String themeId() {
        return themeId;
    }

    public static Customisation customisation() {
        return custom;
    }

    public static Integer accentOverride() {
        return accent;
    }

    /** The current theme with the player's customisation (and the album's colours while they're on). */
    public static ThemeRegistry.Entry entry() {
        rebuildEntry();
        return entry;
    }

    /** The current theme as loaded, without customisation. */
    public static ThemeRegistry.Entry baseEntry() {
        return ThemeRegistry.get(themeId);
    }

    public static void setTheme(String id, boolean animate) {
        String canonical = ThemeRegistry.canonical(id);
        if (canonical == null || canonical.equals(themeId)) {
            return;
        }
        themeId = canonical;
        changed(animate);
    }

    public static void setCustomisation(Customisation c) {
        Customisation next = c == null ? Customisation.NONE : c;
        if (!next.equals(custom)) {
            custom = next;
            changed(false);
        }
    }

    public static void setAccent(Integer color) {
        if (!java.util.Objects.equals(color, accent)) {
            accent = color;
            changed(false);
        }
    }

    /**
     * The album's colours (from the playing song's cover), laid over the theme while they're on, or null.
     * {@code key} names the overlay (the album), so setting the same one again does nothing.
     */
    public static void setAlbum(String key, java.util.function.UnaryOperator<ThemeSpec> overlay, boolean animate) {
        if (overlay == null) {
            key = null;
        }
        if (!java.util.Objects.equals(key, albumKey)) {
            albumKey = key;
            album = overlay;
            changed(animate);
        }
    }

    public static boolean albumActive() {
        return album != null;
    }

    private static void changed(boolean animate) {
        dirty = true;
        if (animate && !Spring.reducedMotion) {
            from.copyFrom(current);
            changeStart = System.nanoTime();
        }
    }

    private static void rebuildEntry() {
        int gen = ThemeRegistry.generation();
        if (entry != null && !dirty && gen == entryGeneration) {
            return;
        }
        ThemeRegistry.Entry base = ThemeRegistry.get(themeId);
        entry = custom.isNone() ? base : customised(base, custom);
        if (album != null) {
            entry = entry.map(album);
        }
        entryGeneration = gen;
    }

    /** {@code base} with the player's edits, each time of day against the theme's own values and its night's. */
    public static ThemeRegistry.Entry customised(ThemeRegistry.Entry base, Customisation c) {
        ThemeSpec ref = base.base(), night = base.phase(ThemeRegistry.Phase.NIGHT);
        return base.mapPhases((phase, spec) -> c.apply(spec, ref, night, phase == ThemeRegistry.Phase.NIGHT
                || phase == ThemeRegistry.Phase.BLUEHOUR || phase == ThemeRegistry.Phase.PREDAWN));
    }

    /**
     * Brings the tokens up to date: once per frame. {@code timeOfDay} is the game's time of day fraction (0 = noon),
     * or NaN when there is no world, which shows the theme's own time of day.
     */
    public static void update(float timeOfDay) {
        boolean themeChanged = dirty || entry == null || ThemeRegistry.generation() != entryGeneration;
        rebuildEntry();
        float q = Float.isNaN(timeOfDay) ? Float.NaN : Keyframes.quantise(timeOfDay);
        boolean timeChanged = !(Float.isNaN(q) ? Float.isNaN(lastTime) : q == lastTime);
        boolean changing = changeStart >= 0L;
        if (!themeChanged && !timeChanged && !changing) {
            return;
        }
        if (themeChanged || timeChanged) {
            lastTime = q;
            if (Float.isNaN(q)) {
                java.util.Arrays.fill(weights, 0f);
                weights[ThemeRegistry.Phase.of(entry.base().show().phase()).ordinal()] = 1f;
            } else {
                Keyframes.weights(Keyframes.altitude(q), Keyframes.morning(q), weights);
            }
            Keyframes.blend(entry, weights, album != null ? null : accent, target);
            dirty = false;
        }
        float k = change();
        if (changing && k < 1f) {
            current.blend(from, target, Ease.inOutCubic(k));
        } else {
            current.copyFrom(target);
            changeStart = -1L;
        }
        current.generation = ++generation;
    }
}
