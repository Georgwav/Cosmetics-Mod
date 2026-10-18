package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.SkyFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.LogOnce;

import java.io.InputStream;
import java.util.Locale;

/**
 * The night sky's names, written into the sky itself rather than laid over it: small Sol-font letters on the sky's
 * own pixels, added like starlight, so they glow faintly in the sky's colour, fade with the dusk and the horizon, and
 * hills and trees pass in front of them. Constellations in spaced capitals at their place, bright stars and the worlds
 * beside them (after the lab's {@code drawLabels}, smaller). The sky pass collects them and writes them after the
 * stars. Render thread only; no allocation per frame (the capitalised names are kept).
 */
public final class SkyLabels {
    public static final int CONSTELLATION = 0, STAR = 1, PLANET = 2;
    private static final int MAX = 160;
    private static final float[] x = new float[MAX], y = new float[MAX], alpha = new float[MAX];
    private static final int[] kind = new int[MAX];
    private static final String[] text = new String[MAX];
    private static int count;
    private static final java.util.Map<String, String> UPPER = new java.util.HashMap<>();

    private static SkyFont font;
    private static SolTexture sheet;
    private static boolean failed;

    private SkyLabels() {
    }

    static void clear() {
        count = 0;
    }

    /** A name at screen pixel (px, py) (its star or world; a constellation's middle), shown at {@code a} (0..1). */
    static void add(float px, float py, String name, int k, float a) {
        if (count >= MAX || a <= 0.02f) {
            return;
        }
        x[count] = px;
        y[count] = py;
        text[count] = k == CONSTELLATION ? UPPER.computeIfAbsent(name, n -> n.toUpperCase(Locale.ROOT)) : name;
        kind[count] = k;
        alpha[count] = a;
        count++;
    }

    /** Writes the names collected this frame into the sky (sky pass). */
    static void draw() {
        if (count == 0 || !load()) {
            return;
        }
        // Sizes in pixels of the lab's 720-pixel-high view, a little smaller than its: the same share of any screen.
        float s = NightSky.sh / 720f;
        SkyFont.Face mono = font.face(SkyFont.MONO, 8f * s), sans = font.face(SkyFont.SANS, 8.5f * s);
        int spacing = Math.max(1, Math.round(1.4f * s));
        float iw = 1f / sheet.width, ih = 1f / sheet.height;
        WorldDraw.begin(WorldDraw.SKY_ADD, sheet);
        for (int i = 0; i < count; i++) {
            if (kind[i] == CONSTELLATION) {
                int w = mono.width(text[i], spacing);
                // Centred on its place, the capitals' middle on it.
                write(mono, text[i], Math.round(x[i] - w / 2f), Math.round(y[i] + mono.ascent * 0.35f), spacing,
                        Colors.withAlpha(0xFFBECEFF, Math.min(0.62f, alpha[i] * 0.8f)), iw, ih);
            } else {
                int col = kind[i] == PLANET ? Colors.withAlpha(0xFFFFE8C4, Math.min(0.75f, alpha[i] * 0.85f))
                        : Colors.withAlpha(0xFFDCE4FF, Math.min(0.55f, alpha[i] * 0.7f));
                write(sans, text[i], Math.round(x[i] + 6f * s), Math.round(y[i] - 4f * s), 0, col, iw, ih);
            }
        }
        WorldDraw.end();
    }

    private static void write(SkyFont.Face f, String s, int penX, int baseline, int spacing, int argb, float iw, float ih) {
        int pen = penX;
        for (int i = 0; i < s.length(); i++) {
            SkyFont.Glyph g = f.glyph(s.charAt(i));
            if (g.w() > 0 && g.h() > 0) {
                float x0 = pen + g.dx(), y0 = baseline + g.dy();
                NightSky.box(x0, y0, x0 + g.w(), y0 + g.h(), g.x() * iw, g.y() * ih, (g.x() + g.w()) * iw, (g.y() + g.h()) * ih, argb);
            }
            pen += g.advance() + spacing;
        }
    }

    /** The lettering and its picture, read once. */
    private static boolean load() {
        if (font != null && sheet != null && !sheet.released()) {
            return true;
        }
        if (failed) {
            return false;
        }
        try (InputStream d = SkyLabels.class.getResourceAsStream("/assets/cosmeticsmod/sky/sky_text.dat");
             InputStream p = SkyLabels.class.getResourceAsStream("/assets/cosmeticsmod/sky/sky_text.png")) {
            if (d == null || p == null) {
                throw new java.io.IOException("missing from the jar");
            }
            font = SkyFont.read(new java.io.BufferedInputStream(d));
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(p);
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            sheet = SolTexture.of("sky_text", w, h, false, px);
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            failed = true;
            LogOnce.warn("sky-text", "Couldn't read the sky's lettering: " + e.getMessage());
            return false;
        }
    }
}
