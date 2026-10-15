package io.github.georgwav.cosmetics.sol.theme;

import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The resolved theme for this frame: every colour and number the UI, the HUD and the world read. Nothing in Sol
 * keeps a colour of its own; it reads these. Written only by {@link ThemeState} on the render thread and rebuilt
 * only when an input changes, so a read is a field load. {@link #generation} goes up on every change so caches
 * (baked glass, HUD layers, textures) know when to rebuild.
 */
public final class Tokens {
    // Fixed text colours from the menu prototype (--fg, --muted, --faint, --line).
    public static final int FG = 0xFFEEF0F7;
    public static final int MUTED = 0xFFA2A8BD;
    public static final int FAINT = 0xFF6C7288;
    public static final int LINE = 0x1AFFFFFF;
    /** HUD warning and critical colours (the brief's durability bars). */
    public static final int AMBER = 0xFFFFC35C;
    public static final int RED = 0xFFFF5555;

    public int generation;
    public String themeId = "";

    // UI
    public int accent;
    public int onAccent;
    public int panel;
    public int rim;
    /** The theme's horizon glow, used by the UI for glows and the sweep line. */
    public int glow;
    /** The glow at 20% (the menu's --band). */
    public int band;
    /** How dark the glass is, 0..1. */
    public float glass;

    // Sky
    public int skyTop;
    public int skyMiddle;
    public int skyHorizon;
    public int skyGlow;
    public float sunWarmth;

    // Fog
    public int fogColor;
    public float fogDensity;
    public float fogStart;
    public float fogEnd;
    public float voxyThinning;
    public int fogBands;

    // Light and clouds
    public int lightTint;
    public float lightStrength;
    /** Cloud tint, or 0 for the game's own. */
    public int cloudTint;
    public boolean cloudsLitBySun;
    /** Whether the game's clouds show with this theme. */
    public boolean cloudsVisible = true;

    /** Fixed fog distances in blocks (0 when the theme uses fractions of the render distance). */
    public float fogNear;
    public float fogFar;
    /** Light pass: in-scattering toward the sun and ground haze. */
    public float scatter;
    public float haze;
    /** How much golden hour brightens the sky light (1 = the game's own). */
    public float lightBoost = 1f;
    /** Light pass: the colour of faces toward the sun and away from it. */
    public int lightSun = 0xFFFFFFFF;
    public int lightShadow = 0xFF404858;

    // The light pass's grade, bloom and vignette (the theme's own; they don't follow the time of day).
    public int gradeHighlights = 0xFFFFFFFF;
    public int gradeShadows = 0xFF000000;
    public float saturation = 1f;
    public float contrast = 1f;
    public int bloomTint = 0xFFFFFFFF;
    public float bloom;
    public float bloomThreshold = 0.75f;
    public float vignette;
    public String lut;

    /** Particle count multiplier for this time of day. */
    public float particleScale = 1f;
    /** 0 by day, 1 at night: how much the night-only sky (stars, galaxy, nebula, aurora) shows. */
    public float night;

    public void copyFrom(Tokens o) {
        generation = o.generation;
        themeId = o.themeId;
        accent = o.accent;
        onAccent = o.onAccent;
        panel = o.panel;
        rim = o.rim;
        glow = o.glow;
        band = o.band;
        glass = o.glass;
        skyTop = o.skyTop;
        skyMiddle = o.skyMiddle;
        skyHorizon = o.skyHorizon;
        skyGlow = o.skyGlow;
        sunWarmth = o.sunWarmth;
        fogColor = o.fogColor;
        fogDensity = o.fogDensity;
        fogStart = o.fogStart;
        fogEnd = o.fogEnd;
        voxyThinning = o.voxyThinning;
        fogBands = o.fogBands;
        lightTint = o.lightTint;
        lightStrength = o.lightStrength;
        cloudTint = o.cloudTint;
        cloudsLitBySun = o.cloudsLitBySun;
        cloudsVisible = o.cloudsVisible;
        particleScale = o.particleScale;
        night = o.night;
        fogNear = o.fogNear;
        fogFar = o.fogFar;
        scatter = o.scatter;
        haze = o.haze;
        lightBoost = o.lightBoost;
        lightSun = o.lightSun;
        lightShadow = o.lightShadow;
        gradeHighlights = o.gradeHighlights;
        gradeShadows = o.gradeShadows;
        saturation = o.saturation;
        contrast = o.contrast;
        bloomTint = o.bloomTint;
        bloom = o.bloom;
        bloomThreshold = o.bloomThreshold;
        vignette = o.vignette;
        lut = o.lut;
    }

    /** {@code a} blended toward {@code b} by {@code t}, into this. */
    public void blend(Tokens a, Tokens b, float t) {
        themeId = t < 0.5f ? a.themeId : b.themeId;
        accent = Colors.mix(a.accent, b.accent, t);
        onAccent = Colors.onColour(accent);
        panel = Colors.mix(a.panel, b.panel, t);
        rim = Colors.mix(a.rim, b.rim, t);
        glow = Colors.mix(a.glow, b.glow, t);
        band = Colors.withAlpha(glow, 0.2f);
        glass = a.glass + (b.glass - a.glass) * t;
        skyTop = Colors.mix(a.skyTop, b.skyTop, t);
        skyMiddle = Colors.mix(a.skyMiddle, b.skyMiddle, t);
        skyHorizon = Colors.mix(a.skyHorizon, b.skyHorizon, t);
        skyGlow = Colors.mix(a.skyGlow, b.skyGlow, t);
        sunWarmth = a.sunWarmth + (b.sunWarmth - a.sunWarmth) * t;
        fogColor = Colors.mix(a.fogColor, b.fogColor, t);
        fogDensity = a.fogDensity + (b.fogDensity - a.fogDensity) * t;
        fogStart = a.fogStart + (b.fogStart - a.fogStart) * t;
        fogEnd = a.fogEnd + (b.fogEnd - a.fogEnd) * t;
        voxyThinning = a.voxyThinning + (b.voxyThinning - a.voxyThinning) * t;
        fogBands = t < 0.5f ? a.fogBands : b.fogBands;
        lightTint = Colors.mix(a.lightTint, b.lightTint, t);
        lightStrength = a.lightStrength + (b.lightStrength - a.lightStrength) * t;
        cloudTint = a.cloudTint == 0 || b.cloudTint == 0 ? (t < 0.5f ? a.cloudTint : b.cloudTint)
                : Colors.mix(a.cloudTint, b.cloudTint, t);
        cloudsLitBySun = t < 0.5f ? a.cloudsLitBySun : b.cloudsLitBySun;
        cloudsVisible = t < 0.5f ? a.cloudsVisible : b.cloudsVisible;
        particleScale = a.particleScale + (b.particleScale - a.particleScale) * t;
        night = a.night + (b.night - a.night) * t;
        fogNear = lerpDistance(a.fogNear, b.fogNear, a.fogStart, b.fogStart, t);
        fogFar = lerpDistance(a.fogFar, b.fogFar, a.fogEnd, b.fogEnd, t);
        scatter = a.scatter + (b.scatter - a.scatter) * t;
        haze = a.haze + (b.haze - a.haze) * t;
        lightBoost = a.lightBoost + (b.lightBoost - a.lightBoost) * t;
        lightSun = Colors.mix(a.lightSun, b.lightSun, t);
        lightShadow = Colors.mix(a.lightShadow, b.lightShadow, t);
        gradeHighlights = Colors.mix(a.gradeHighlights, b.gradeHighlights, t);
        gradeShadows = Colors.mix(a.gradeShadows, b.gradeShadows, t);
        saturation = a.saturation + (b.saturation - a.saturation) * t;
        contrast = a.contrast + (b.contrast - a.contrast) * t;
        bloomTint = Colors.mix(a.bloomTint, b.bloomTint, t);
        bloom = a.bloom + (b.bloom - a.bloom) * t;
        bloomThreshold = a.bloomThreshold + (b.bloomThreshold - a.bloomThreshold) * t;
        vignette = a.vignette + (b.vignette - a.vignette) * t;
        lut = t < 0.5f ? a.lut : b.lut;
    }

    /**
     * Blends fixed fog distances; when only one side has them, the other side's fractions stand in at a typical
     * 12-chunk render distance so the change still glides.
     */
    private static float lerpDistance(float a, float b, float fracA, float fracB, float t) {
        if (a <= 0f && b <= 0f) {
            return 0f;
        }
        float av = a > 0f ? a : fracA * 192f;
        float bv = b > 0f ? b : fracB * 192f;
        return av + (bv - av) * t;
    }

    /** Fills the UI fields from a theme version, with an optional accent override (a profile's, or the album's). */
    void ui(ThemeSpec s, Integer accentOverride) {
        accent = accentOverride != null ? accentOverride : s.ui().accent();
        onAccent = Colors.onColour(accent);
        panel = s.ui().panel();
        rim = s.ui().rim();
        glass = s.ui().glass();
    }
}
