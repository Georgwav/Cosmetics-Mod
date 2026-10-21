package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The HUD modules' kit (units are GUI pixels, half the prototype's CSS pixels): the pixel UI's slabs at GUI scale
 * (the slab colour at the module's backing darkness, a one-GUI-pixel edge and a lit top, no glass and no soft glow),
 * boxes, outlines and stepped on-glow rings on whole pixels, the accent dot, durability-style bars going from the
 * accent to amber to red, and the states' colours. Numbers and labels are in Silkscreen.
 */
public final class HudKit {
    /** The prototype's .hc radius (12 px) and the chips' (999 px, round). */
    public static final float CARD_R = 6f;
    /** Text sizes: the prototype's 12 px body, 10.5 px small. */
    public static final float TEXT = 6.5f;
    public static final float SMALL = 5.5f;
    /** The labels' type. */
    public static final SolFont.Family LABEL = SolFont.Family.PIXEL;

    private HudKit() {
    }

    /** How much of the slab the HUD module being drawn wants (its backing darkness; 0 for none). */
    public static float glass = 1f;

    /** A HUD module's card: a {@link #slab} at the module's backing darkness ({@code r} is kept for the callers). */
    public static void card(Gfx g, Tokens tk, float x, float y, float w, float h, float r) {
        if (glass > 0f) {
            slab(g, tk, x, y, w, h, glass);
        }
    }

    /**
     * The pixel UI's slab at GUI scale, {@code dark} times its usual darkness: the slab colour, a lit top edge and a
     * one-GUI-pixel edge, all on whole screen pixels; no glass and no soft glow.
     */
    public static void slab(Gfx g, Tokens tk, float x, float y, float w, float h, float dark) {
        PixelTokens p = PixelTokens.get();
        float a = Math.min(1f, Math.max(0.62f, tk.glass) * dark);
        int e = e(g);
        float x0 = Math.round(g.d.sx(x)), y0 = Math.round(g.d.sy(y)), x1 = Math.round(g.d.sx(x + w)), y1 = Math.round(g.d.sy(y + h));
        g.rectPx(x0, y0, x1, y1, Colors.withAlpha(p.c0, a));
        g.rectPx(x0 + e, y0 + e, x1 - e, Math.min(y1 - e, y0 + 2 * e), Colors.fade(p.hi, Math.min(1f, dark)));
        g.framePx(x0, y0, x1, y1, e, Colors.withAlpha(p.edge, Math.min(1f, a + 0.2f)));
    }

    /** One GUI pixel in screen pixels. */
    public static int e(Gfx g) {
        return Math.max(1, Math.round(g.d.u));
    }

    /** A box on whole screen pixels. */
    public static void box(Gfx g, float x, float y, float w, float h, int argb) {
        float x0 = Math.round(g.d.sx(x)), y0 = Math.round(g.d.sy(y)), x1 = Math.round(g.d.sx(x + w)), y1 = Math.round(g.d.sy(y + h));
        if (x1 > x0 && y1 > y0) {
            g.rectPx(x0, y0, x1, y1, argb);
        }
    }

    /** A one-GUI-pixel outline on whole screen pixels. */
    public static void frame(Gfx g, float x, float y, float w, float h, int argb) {
        float x0 = Math.round(g.d.sx(x)), y0 = Math.round(g.d.sy(y)), x1 = Math.round(g.d.sx(x + w)), y1 = Math.round(g.d.sy(y + h));
        g.framePx(x0, y0, x1, y1, e(g), argb);
    }

    /**
     * The on glow at GUI scale: a one-GUI-pixel outline in {@code argb} and two stepped rings outside it, fading out
     * (the kit's on-glow, never a blur).
     */
    public static void rings(Gfx g, float x, float y, float w, float h, int argb) {
        int e = e(g);
        float x0 = Math.round(g.d.sx(x)), y0 = Math.round(g.d.sy(y)), x1 = Math.round(g.d.sx(x + w)), y1 = Math.round(g.d.sy(y + h));
        g.framePx(x0 - 2 * e, y0 - 2 * e, x1 + 2 * e, y1 + 2 * e, e, Colors.fade(argb, 0.15f));
        g.framePx(x0 - e, y0 - e, x1 + e, y1 + e, e, Colors.fade(argb, 0.4f));
        g.framePx(x0, y0, x1, y1, e, argb);
    }

    /**
     * A stepped glow behind something: three boxes, each a GUI pixel bigger than the last, at a third of the alpha
     * each, so it brightens in hard steps toward the middle.
     */
    public static void glow(Gfx g, float x, float y, float w, float h, int argb) {
        int c = Colors.fade(argb, 1f / 3f);
        for (int i = 3; i >= 1; i--) {
            box(g, x - i, y - i, w + 2f * i, h + 2f * i, c);
        }
    }

    /** The accent square (the prototype's .hstats i), on whole GUI pixels. */
    public static void dot(Gfx g, int color, float x, float cy) {
        box(g, x, cy - 1.5f, 3f, 3f, color);
    }

    /** The colour for a level from 0 (bad) to 1 (good): the accent, amber below {@code warn}, red below {@code crit}. */
    public static int state(Tokens tk, float k, float warn, float crit) {
        return k <= crit ? Tokens.RED : k <= warn ? Tokens.AMBER : tk.accent;
    }

    /** A thin bar (the prototype's .dur): a faint track, the fill in {@code color}, on whole pixels. */
    public static void bar(Gfx g, float x, float y, float w, float h, float k, int color) {
        box(g, x, y, w, h, 0x24FFFFFF);
        if (k > 0f) {
            box(g, x, y, Math.max(h, w * Math.min(1f, k)), h, color);
        }
    }

    /** A slow pulse (1.4 s) for things about to run out; steady with calm motion. */
    public static float pulse() {
        if (SolScreen.calm()) {
            return 1f;
        }
        double t = (System.nanoTime() / 1_000_000L % 1400L) / 1400.0;
        return 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.cos(t * Math.PI * 2.0));
    }

    /**
     * The width of {@code text} in GUI units for a HUD module drawn at {@code scale}, outside a draw (its size for
     * the HUD editor and its anchoring): the font picked as the kit picks it at that size.
     */
    public static float measure(SolFont.Family family, float size, String text, float scale) {
        float px = (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale() * scale;
        return SolFont.of(family, size * px).width(text) / px;
    }

    public static SolFont text(Gfx g) {
        return g.font(SolFont.Family.MEDIUM, TEXT);
    }

    /** Labels (FPS, the heading, the compass's points): Silkscreen at the small size. */
    public static SolFont small(Gfx g) {
        return g.font(LABEL, SMALL);
    }

    /** Numbers: Silkscreen at the text's size. */
    public static SolFont mono(Gfx g) {
        return g.font(SolFont.Family.PIXEL_BOLD, TEXT);
    }
}
