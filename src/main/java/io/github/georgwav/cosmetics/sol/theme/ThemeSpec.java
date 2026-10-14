package io.github.georgwav.cosmetics.sol.theme;

import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.List;

/**
 * One Sol theme, as loaded from {@code assets/<ns>/atmospheres/<id>.json}: the sky, the light, the sun, the moon,
 * the stellar sky, the theme's extras and its particles, plus the UI colours. Immutable. Sizes and positions are
 * the prototypes' units: fractions of the view's height (and width for x), in the theme's showpiece view.
 * Colours are ARGB ints.
 */
public record ThemeSpec(
        String id,
        String name,
        String line,
        int order,
        Ui ui,
        Sky sky,
        Fog fog,
        Light light,
        Sun sun,
        Moon moon,
        Stars stars,
        Planets planets,
        Galaxy galaxy,
        int[] aurora,
        List<Nebula> nebula,
        Eclipse eclipse,
        boolean lightning,
        Sigil sigil,
        Mist mist,
        Clouds clouds,
        float[] wind,
        List<Emitter> particles,
        Show show,
        boolean noRidges,
        boolean vanillaSun) {

    /** UI colours: accent, the glass panel (with its alpha, how dark the glass is) and the rim light. */
    public record Ui(int accent, int panel, int rim) {
        /** How dark this theme's glass is, 0..1 (the panel's alpha). */
        public float glass() {
            return Colors.a(panel) / 255f;
        }
    }

    /** Sky gradient top → middle → horizon, the horizon glow band, and how much it warms toward the sun. */
    public record Sky(int top, int middle, int horizon, int glow, float sunWarmth) {
    }

    /**
     * Fog colour and density (the prototype's {@code fd}), the start and end as fractions of the render distance,
     * how much it thins with Voxy/Distant Horizons far land, and how many stepped bands the far land shows.
     */
    public record Fog(int color, float density, float start, float end, float voxyThinning, int bands) {
    }

    /** The light tint over every block. */
    public record Light(int tint, float strength) {
    }

    /**
     * The Sol sun: size, core and halo colours, halo radius and alpha, its layers, rays, and where it stands in the
     * showpiece view (x, y). {@code texture} is the resource id of the 16×16 base sun.
     */
    public record Sun(String texture, float size, int core, int halo, float haloRadius, float haloAlpha, int rays,
                      int coreRays, float rayLength, boolean plasma, boolean corona, boolean sunDogs, boolean pillar,
                      boolean shimmer, boolean flare, boolean glare, float orbitTilt, float x, float y) {
    }

    /** The square moon. */
    public record Moon(float size, int color, int halo, float haloRadius, float haloAlpha, boolean ring, boolean dogs,
                       float x, float y) {
    }

    /** The stellar sky: bright-star count, faint-star density, palette, Milky Way, deep sky, sky events. */
    public record Stars(int count, float density, String palette, Integer tint, float milkyWay, boolean deepSky,
                        boolean shooting, int meteorNights, boolean comets, String through) {
    }

    /** Planets: on or off, sprite style, tint, and which show by day. */
    public record Planets(boolean enabled, String style, Integer tint, List<String> daytime) {
    }

    /** The Deep Field galaxy: centre, radius (fraction of the view's width) and tilt. */
    public record Galaxy(float x, float y, float r, float tilt) {
    }

    /** One nebula cloud: centre, radius (fraction of width), colour. */
    public record Nebula(float x, float y, float r, int color) {
    }

    /** The eclipsed sun: centre, half-size, corona colour. */
    public record Eclipse(float x, float y, float size, int corona) {
    }

    /** The Grace rune: centre, size (the upper circle's radius as a fraction of the view), colour, bloom colour. */
    public record Sigil(float x, float y, float r, int color, int halo) {
    }

    /** Grace's fog bands: colour, the warm cloud bank's colour (or null), how many bands, their strength. */
    public record Mist(int color, Integer warm, int bands, float alpha) {
    }

    /** Vanilla clouds' tint and whether a low sun lights them. */
    public record Clouds(Integer tint, boolean litBySun) {
    }

    /**
     * One particle stream: type, how many at default density, colours, size range in pixels at a 560 px tall view
     * (the prototype's units), an optional texture override, and fall and spin multipliers.
     */
    public record Emitter(String type, int count, int[] colors, float minSize, float maxSize, String texture, float fall,
                          float spin) {
    }

    /** The showpiece: which time of day the base values describe, and which way to face for it (degrees from south). */
    public record Show(String phase, float azimuth) {
    }

    public boolean hasSun() {
        return sun != null;
    }

    /** Whether the theme's showpiece is its night (the night themes). */
    public boolean nightTheme() {
        return "night".equals(show.phase());
    }
}
