package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The pixel UI's pieces (the prototype's CSS, drawn on whole screen pixels): Dawn's slabs with thick dark outlines,
 * their top edge lit in the preset's sun colour; recesses for fields and lists; the red close; the textured, glowing
 * "on" slab with the logo's sparkle; tabs, chips, swatches, the search field, scrollbars, focus outlines, tooltips,
 * the window with the horizon under its title bar, and the preview pane. Every size is in design pixels; outlines and
 * bevels are 3 design pixels as whole screen pixels. Drawing only: {@link Ui} adds the hit areas.
 */
public final class Pix {
    /** An outline's or a bevel's width in design pixels. */
    public static final float E = 3f;
    /** Slab states. */
    public static final int NORMAL = 0, HOVER = 1, ON = 2, PRESSED = 3;
    /** The window (A.6) and its title bar. */
    public static final float WIN_X = 40f, WIN_Y = 62f, WIN_W = 1200f, WIN_H = 642f, BAR_H = 60f;

    private Pix() {
    }

    private static float x0, y0, x1, y1;

    private static void box(Gfx g, float x, float y, float w, float h) {
        x0 = Math.round(g.d.sx(x));
        y0 = Math.round(g.d.sy(y));
        x1 = Math.round(g.d.sx(x + w));
        y1 = Math.round(g.d.sy(y + h));
    }

    /** The slab's text colour for a state. */
    public static int text(PixelTokens p, int state) {
        return state == ON ? p.acc : state == HOVER ? PixelTokens.HOVER_TEXT : PixelTokens.SLAB_TEXT;
    }

    /**
     * A slab: fill c1, a 3 px edge outline, a 3 px lit inner top and a 3 px dark inner bottom. Hovered it's c2; on
     * (or selected) it has an accent outline and a fill from the accent tint down to c1 at 85%, in three steps;
     * pressed it moves down 3 px.
     */
    public static void slab(Gfx g, PixelTokens p, float x, float y, float w, float h, int state) {
        if (state == PRESSED) {
            y += E;
        }
        box(g, x, y, w, h);
        int e = g.px(E);
        if (state == ON) {
            g.stepped(x0, y0, x1, y1, p.accTint, p.c1, 0.85f, 3);
        } else {
            g.rectPx(x0, y0, x1, y1, state == HOVER ? p.c2 : p.c1);
        }
        g.rectPx(x0 + e, y0 + e, x1 - e, Math.min(y1 - e, y0 + 2 * e), p.hi);
        g.rectPx(x0 + e, Math.max(y0 + e, y1 - 2 * e), x1 - e, y1 - e, p.lo);
        g.framePx(x0, y0, x1, y1, e, state == ON ? p.acc : p.edge);
    }

    /** A filled box on whole screen pixels (design coordinates). */
    public static void fill(Gfx g, float x, float y, float w, float h, int c) {
        box(g, x, y, w, h);
        g.rectPx(x0, y0, x1, y1, c);
    }

    /** A card that's on (a module that's enabled): the on colour's faint tint fading to c0 by 60%, in three steps. */
    public static void cardOn(Gfx g, PixelTokens p, float x, float y, float w, float h) {
        box(g, x, y, w, h);
        int e = g.px(E);
        g.stepped(x0, y0, x1, y1, Colors.mixRgb(p.c0, p.en, Colors.a(p.enA2) / 255f), p.c0, 0.6f, 3);
        g.rectPx(x0 + e, y0 + e, x1 - e, Math.min(y1 - e, y0 + 2 * e), p.hi);
        g.rectPx(x0 + e, Math.max(y0 + e, y1 - 2 * e), x1 - e, y1 - e, p.lo);
        g.framePx(x0, y0, x1, y1, e, p.edge);
    }

    /** A card: a slab in c0 (module cards, Wardrobe tiles). */
    public static void card(Gfx g, PixelTokens p, float x, float y, float w, float h) {
        box(g, x, y, w, h);
        int e = g.px(E);
        g.rectPx(x0, y0, x1, y1, p.c0);
        g.rectPx(x0 + e, y0 + e, x1 - e, Math.min(y1 - e, y0 + 2 * e), p.hi);
        g.rectPx(x0 + e, Math.max(y0 + e, y1 - 2 * e), x1 - e, y1 - e, p.lo);
        g.framePx(x0, y0, x1, y1, e, p.edge);
    }

    /** A recess (fields, lists, scroll areas, search): fill rec, a 3 px edge, black at 45% along its inner top. */
    public static void recess(Gfx g, PixelTokens p, float x, float y, float w, float h) {
        box(g, x, y, w, h);
        int e = g.px(E);
        g.rectPx(x0, y0, x1, y1, p.rec);
        g.rectPx(x0 + e, y0 + e, x1 - e, Math.min(y1 - e, y0 + 2 * e), 0x73000000);
        g.framePx(x0, y0, x1, y1, e, p.edge);
    }

    /** The red close: #2a1212 with a 3 px red outline and a red X; filled red with a near-black X on hover. */
    public static void redClose(Gfx g, PixelTokens p, float x, float y, float w, float h, boolean hover) {
        box(g, x, y, w, h);
        int e = g.px(E);
        g.rectPx(x0, y0, x1, y1, hover ? PixelTokens.RED : PixelTokens.CLOSE_FILL);
        if (!hover) {
            g.rectPx(x0 + e, y0 + e, x1 - e, y0 + 2 * e, 0x0FFFFFFF);
        }
        g.framePx(x0, y0, x1, y1, e, PixelTokens.RED);
        Icons.draw(g, "x", x + w / 2f - 12f, y + h / 2f - 12f, 24f, hover ? 0xFF1A0505 : PixelTokens.RED, 0f, 0);
    }

    /**
     * Dawn's textured, glowing ENABLED slab in Sol's colours: a 3 px "on" outline; the on texture at 2x under a band of
     * the on colour that fades out by 75% of the height (in three steps); a lit inner top and a dark inner bottom;
     * outside, two stepped rings and a faint glow, all hard-edged. Hover brightens it.
     */
    public static void onGlow(Gfx g, PixelTokens p, float x, float y, float w, float h, boolean hover) {
        box(g, x, y, w, h);
        int e = g.px(E);
        // Outside: 3 px of enA1, 3 px of enA2, then a faint stepped glow out to 22 px.
        g.ringPx(x0, y0, x1, y1, 0, e, p.enA1);
        g.ringPx(x0, y0, x1, y1, e, 2 * e, p.enA2);
        int far = g.px(22f);
        if (far > 2 * e) {
            int mid = 2 * e + (far - 2 * e) / 2;
            g.ringPx(x0, y0, x1, y1, 2 * e, mid, Colors.fade(p.enA2, 0.55f));
            g.ringPx(x0, y0, x1, y1, mid, far, Colors.fade(p.enA2, 0.25f));
        }
        onFill(g, p, x0, y0, x1, y1, e);
        if (hover) {
            g.rectPx(x0, y0, x1, y1, 0x14FFFFFF);
        }
        g.framePx(x0, y0, x1, y1, e, p.en);
    }

    /** The on look's inside (texture, fading band, bevels) in a screen box inside a {@code e}-pixel outline. */
    private static void onFill(Gfx g, PixelTokens p, float bx0, float by0, float bx1, float by1, int e) {
        float ix0 = bx0 + e, iy0 = by0 + e, ix1 = bx1 - e, iy1 = by1 - e;
        g.rectPx(ix0, iy0, ix1, iy1, p.enDark);
        texture(g, p.onTexture(), ix0, iy0, ix1, iy1, g.px(2f));
        float h = iy1 - iy0;
        for (int i = 0; i < 3; i++) {
            float a = Math.round(iy0 + h * 0.75f * i / 3f), b = Math.round(iy0 + h * 0.75f * (i + 1) / 3f);
            g.rectPx(ix0, a, ix1, b, Colors.fade(p.enA1, 1f - i / 3f));
        }
        g.rectPx(ix0, iy0, ix1, Math.min(iy1, iy0 + e), p.enHi);
        g.rectPx(ix0, Math.max(iy0, iy1 - e), ix1, iy1, 0x59000000);
    }

    /** A pixel texture tiled over a screen box at {@code k} screen pixels per texel, cut at the box's edges. */
    public static void texture(Gfx g, SolTexture t, float bx0, float by0, float bx1, float by1, int k) {
        float tw = 32 * k, th = 16 * k;
        for (float ty = by0; ty < by1; ty += th) {
            for (float tx = bx0; tx < bx1; tx += tw) {
                float ex = Math.min(bx1, tx + tw), ey = Math.min(by1, ty + th);
                g.imageScreen(t, tx, ty, ex, ey, 0f, 0f, (ex - tx) / tw, (ey - ty) / th, 0xFFFFFFFF, false);
            }
        }
    }

    /** The on slab's text: on-text letters with a hard black drop and a soft (pixel) glow of the on colour behind. */
    public static void onText(Gfx g, PixelTokens p, SolFont f, String s, float x, float cy) {
        float o = 1f / g.d.u;
        int halo = Colors.fade(p.en, 0.45f);
        g.textMid(f, s, x - o, cy, halo);
        g.textMid(f, s, x + o, cy, halo);
        g.textMid(f, s, x, cy - o, halo);
        g.textMid(f, s, x, cy + 2f, 0xFF000000);
        g.textMid(f, s, x, cy, p.enText);
    }

    /**
     * The logo's plus sparkle in the top right of an on slab, 7 px in: a 3 x 3 white centre with 3 px arms and a small
     * glow. It twinkles in steps over a 2.4 s loop (full, then 25% for 7% of it), and stays still with calm motion.
     */
    public static void sparkle(Gfx g, PixelTokens p, float right, float top, float time, boolean calm) {
        float phase = calm ? 0f : (time % 2.4f) / 2.4f;
        float a = phase >= 0.63f && phase < 0.71f ? 0.25f : 1f;
        int u = g.px(E);
        float cx = Math.round(g.d.sx(right - 7f)) - u, cy = Math.round(g.d.sy(top + 7f)) + u;
        int white = Colors.fade(0xFFFFFFFF, a), glow = Colors.fade(Colors.withAlpha(p.en, 0.35f), a);
        g.rectPx(cx - 2 * u, cy - 2 * u, cx + 3 * u, cy + 3 * u, glow);
        g.rectPx(cx, cy, cx + u, cy + u, white);
        g.rectPx(cx - u, cy, cx, cy + u, white);
        g.rectPx(cx + u, cy, cx + 2 * u, cy + u, white);
        g.rectPx(cx, cy - u, cx + u, cy, white);
        g.rectPx(cx, cy + u, cx + u, cy + 2 * u, white);
    }

    /** The off slab: a plain slab with grey text that lightens on hover. */
    public static int offText(boolean hover) {
        return hover ? 0xFFE6E1E8 : PixelTokens.OFF_TEXT;
    }

    /** Keyboard focus: a 3 px white outline, 2 px out. */
    public static void focus(Gfx g, float x, float y, float w, float h) {
        box(g, x - 2f, y - 2f, w + 4f, h + 4f);
        int e = g.px(E);
        g.framePx(x0 - e, y0 - e, x1 + e, y1 + e, e, 0xFFFFFFFF);
    }

    /** A swatch (40 x 32 by default): the colour, a 3 px edge and a lit top; chosen, a 3 px white outline 2 px out. */
    public static void swatch(Gfx g, PixelTokens p, float x, float y, float w, float h, int colour, boolean chosen) {
        box(g, x, y, w, h);
        int e = g.px(E);
        g.rectPx(x0, y0, x1, y1, 0xFF000000 | colour);
        g.rectPx(x0 + e, y0 + e, x1 - e, y0 + 2 * e, 0x40FFFFFF);
        g.framePx(x0, y0, x1, y1, e, p.edge);
        if (chosen) {
            focus(g, x, y, w, h);
        }
    }

    /** A square scrollbar 6 px wide: an accent thumb on a #151517 track. {@code from} and {@code to} 0..1 of the track. */
    public static void scrollbar(Gfx g, PixelTokens p, float x, float y, float h, float from, float to) {
        g.rect(x, y, 6f, h, 0xFF151517);
        g.rect(x, y + h * from, 6f, Math.max(8f, h * (to - from)), p.acc);
    }

    /** A tooltip: a recess holding a line of Geist 12, its top left at (x, y); returns its width. */
    public static float tooltip(Gfx g, PixelTokens p, float x, float y, String s) {
        SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
        float w = g.width(f, s) + 20f, h = 28f;
        x = Math.max(g.d.left() + 4f, Math.min(x, g.d.right() - w - 4f));
        recess(g, p, x, y, w, h);
        g.textMid(f, s, x + 10f, y + h / 2f, PixelTokens.INK);
        return w;
    }

    /** A key chip ("CTRL K"): Silkscreen 10 on c1 with a 2 px edge, ending at {@code right}; returns its width. */
    public static float keyChip(Gfx g, PixelTokens p, String keys, float right, float cy) {
        SolFont f = g.font(SolFont.Family.PIXEL, 10f);
        float w = g.width(f, keys) + 12f, h = 20f, x = right - w;
        box(g, x, cy - h / 2f, w, h);
        g.rectPx(x0, y0, x1, y1, p.c1);
        g.framePx(x0, y0, x1, y1, g.px(2f), p.edge);
        g.textMid(f, keys, x + 6f, cy, PixelTokens.INK2);
        return w;
    }

    /**
     * The search field: a recess with the search icon, the text (Geist 15) or the ink3 placeholder, and a key chip;
     * outlined in the accent while it has focus.
     */
    public static void search(Gfx g, PixelTokens p, float x, float y, float w, float h, String text, String placeholder,
                              String keys, boolean focused, float caret) {
        recess(g, p, x, y, w, h);
        if (focused) {
            g.stroke(x, y, w, h, 0f, g.px(E), p.acc);
        }
        Icons.draw(g, "search", x + 10f, y + h / 2f - 12f, 24f, PixelTokens.INK3, 0f, 0);
        float tx = x + 42f, right = x + w - 8f;
        if (keys != null) {
            right -= keyChip(g, p, keys, x + w - 8f, y + h / 2f) + 8f;
        }
        SolFont f = g.font(SolFont.Family.MEDIUM, 15f);
        boolean empty = text == null || text.isEmpty();
        String shown = empty ? g.fit(f, placeholder, right - tx) : f.fitEnd(text, (right - tx) * g.d.u);
        g.textMid(f, shown, tx, y + h / 2f, empty ? PixelTokens.INK3 : PixelTokens.INK);
        if (focused && caret >= 0f) {
            float cx = tx + (empty ? 0f : g.width(f, shown)) + 1f;
            g.rect(cx, y + h / 2f - 9f, 2f, 18f, PixelTokens.INK);
        }
    }

    /**
     * The window (the full menu and every page): 1200 x 642 at (40, 62), c0 at 97%, a 3 px edge and a lit top; a 60 px
     * title bar in a darker c0 with a 3 px edge under it, and Sol's horizon along that edge.
     */
    public static void window(Gfx g, PixelTokens p) {
        box(g, WIN_X, WIN_Y, WIN_W, WIN_H);
        int e = g.px(E);
        g.rectPx(x0, y0, x1, y1, Colors.withAlpha(p.c0, 0.97f));
        g.rectPx(x0 + e, y0 + e, x1 - e, y0 + 2 * e, p.hi);
        float barBottom = Math.round(g.d.sy(WIN_Y + BAR_H));
        g.rectPx(x0 + e, y0 + e, x1 - e, barBottom, Colors.mixRgb(p.c0, 0xFF000000, 0.18f));
        g.rectPx(x0 + e, barBottom, x1 - e, barBottom + e, p.edge);
        horizon(g, p, x0 + e, barBottom, x1 - e, e);
        g.framePx(x0, y0, x1, y1, e, p.edge);
    }

    /**
     * Sol's horizon: a line {@code t} screen pixels tall at 85%, from nothing at both ends through the glow at 20% and
     * 80% to white in the middle, in hard steps (screen box x0..x1 at y).
     */
    public static void horizon(Gfx g, PixelTokens p, float hx0, float y, float hx1, int t) {
        int steps = 20;
        float w = hx1 - hx0;
        for (int i = 0; i < steps; i++) {
            float a = Math.round(hx0 + w * i / steps), b = Math.round(hx0 + w * (i + 1) / steps);
            float f = (i + 0.5f) / steps, m = Math.abs(f - 0.5f) * 2f;
            int c;
            if (m < 0.6f) {
                c = Colors.mixRgb(0xFFFFFFFF, p.glow, m / 0.6f);
                c = Colors.withAlpha(c, 0.85f);
            } else {
                c = Colors.withAlpha(p.glow, 0.85f * (1f - (m - 0.6f) / 0.4f));
            }
            g.rectPx(a, y, b, y + t, c);
        }
    }

    /**
     * The preview pane's frame: black with a 3 px edge. The caller draws the sky (at half resolution) and the figure
     * inside, then {@link #caption}.
     */
    public static void pane(Gfx g, PixelTokens p, float x, float y, float w, float h) {
        box(g, x, y, w, h);
        g.rectPx(x0, y0, x1, y1, 0xFF000000);
    }

    public static void paneEdge(Gfx g, PixelTokens p, float x, float y, float w, float h) {
        box(g, x, y, w, h);
        g.framePx(x0, y0, x1, y1, g.px(E), p.edge);
    }

    /** The preview pane's caption slab at its bottom: the title in Silkscreen, a line in Geist. */
    public static void caption(Gfx g, PixelTokens p, float x, float y, float w, float h, String title, String line) {
        float cx = x + 12f, cw = w - 24f, ch = line == null || line.isEmpty() ? 40f : 58f, cy = y + h - 12f - ch;
        box(g, cx, cy, cw, ch);
        int e = g.px(E);
        g.rectPx(x0, y0, x1, y1, 0xEB19191C);
        g.framePx(x0, y0, x1, y1, e, p.edge);
        SolFont tf = g.font(SolFont.Family.PIXEL_BOLD, 13f), lf = g.font(SolFont.Family.MEDIUM, 12.5f);
        g.textMid(tf, g.fit(tf, title, cw - 24f), cx + 12f, cy + 18f, PixelTokens.INK);
        if (ch > 40f) {
            g.textMid(lf, g.fit(lf, line, cw - 24f), cx + 12f, cy + 39f, PixelTokens.INK2);
        }
    }
}
