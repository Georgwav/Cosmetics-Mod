package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Design;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

/**
 * Sol UI's look on the game's own screens, in the pixel UI's style at GUI scale: buttons as Dawn's slabs (a one-pixel
 * edge, a lit top and a dark bottom; the accent's outline while pointed at), tooltips as recesses, the chosen row of a
 * list in the accent's tint, the pause screen's flat dim and the death screen's dusk, and the loading screen's square
 * sun rising as the progress bar. Each is drawn in the game's GUI pixels, guarded; with Sol UI off (or after an
 * error) the game draws its own.
 */
public final class VanillaLook {
    private static final Design D = new Design();
    private static final Gfx G = new Gfx(D);
    /** How many glass buttons, tooltips and veils were drawn (the scenario's check). */
    public static int buttons, tooltips, veils, loading;

    private VanillaLook() {
    }

    public static boolean on() {
        return SolConfig.get().solUi && Guard.on(Feature.SCREENS);
    }

    private static Gfx begin(Canvas c) {
        Minecraft mc = Minecraft.getInstance();
        D.gui(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), mc.getWindow().getGuiScale());
        c.push();
        c.scale(1f / D.gs, 1f / D.gs);
        G.begin(c);
        return G;
    }

    private static void end(Canvas c) {
        G.end();
        c.pop();
    }

    /** Draws {@code body} guarded; false when it failed (the caller then lets the game draw). */
    private static boolean draw(Canvas c, java.util.function.Consumer<Gfx> body) {
        return Guard.run(Feature.SCREENS, () -> {
            Gfx g = begin(c);
            try {
                body.accept(g);
            } finally {
                end(c);
            }
        });
    }

    /** A slab where the game draws its button sprite: c1 (c2 pointed at), recessed when it can't be pressed. */
    public static boolean button(Canvas c, int x, int y, int w, int h, boolean hover, boolean active) {
        buttons++;
        return draw(c, g -> {
            PixelTokens p = PixelTokens.get();
            if (!active) {
                box(g, x, y, w, h, p.rec, 0x73000000, 0, p.edge);
            } else {
                box(g, x, y, w, h, hover ? p.c2 : p.c1, p.hi, p.lo, hover ? p.acc : p.edge);
            }
        });
    }

    /**
     * A box on whole screen pixels: {@code fill}, a lit top row and a dark bottom row (0 for none) inside a one-GUI-pixel
     * outline.
     */
    private static void box(Gfx g, float x, float y, float w, float h, int fill, int top, int bottom, int edge) {
        int e = Math.max(1, Math.round(g.d.u));
        float x0 = Math.round(g.d.sx(x)), y0 = Math.round(g.d.sy(y)), x1 = Math.round(g.d.sx(x + w)), y1 = Math.round(g.d.sy(y + h));
        g.rectPx(x0, y0, x1, y1, fill);
        if (top != 0) {
            g.rectPx(x0 + e, y0 + e, x1 - e, Math.min(y1 - e, y0 + 2 * e), top);
        }
        if (bottom != 0) {
            g.rectPx(x0 + e, Math.max(y0 + e, y1 - 2 * e), x1 - e, y1 - e, bottom);
        }
        g.framePx(x0, y0, x1, y1, e, edge);
    }

    /** A glass tooltip around a tooltip's text box (x, y, w, h), at depth {@code z} where the game lifts it. */
    public static boolean tooltip(Canvas c, int x, int y, int w, int h, int z) {
        tooltips++;
        if (z != 0) {
            c.push();
            c.lift(z);
        }
        boolean ok = draw(c, g -> {
            PixelTokens p = PixelTokens.get();
            box(g, x - 4f, y - 4f, w + 8f, h + 8f, Colors.withAlpha(p.rec, 0.96f), 0x73000000, 0, p.edge);
        });
        if (z != 0) {
            c.pop();
        }
        return ok;
    }

    /** A list's chosen row: the accent's tint with its outline. */
    public static boolean selection(Canvas c, int x, int y, int w, int h) {
        return draw(c, g -> {
            PixelTokens p = PixelTokens.get();
            box(g, x, y, w, h, Colors.withAlpha(p.accTint, 0.85f), 0, 0, p.acc);
        });
    }

    /** The pause screen's veil over the game: the pixel UI's flat dim (black at 24%). */
    public static boolean pauseVeil(Canvas c, int w, int h) {
        veils++;
        return draw(c, g -> g.rect(0f, 0f, w, h, 0x3D000000));
    }

    /** The death screen's dusk (in place of the game's red wash): the theme's dark sky with an ember glow below. */
    public static boolean deathVeil(Canvas c, int w, int h) {
        veils++;
        return draw(c, g -> {
            Tokens tk = ThemeState.tokens();
            // The theme's dark sky over the top two thirds, an ember band under it, in hard steps.
            int top = Colors.withAlpha(Colors.mix(tk.skyTop, 0xFF000000, 0.55f), 0.78f);
            int bottom = Colors.withAlpha(Colors.mix(Tokens.RED, tk.skyGlow, 0.35f), 0.5f);
            for (int i = 0; i < 4; i++) {
                g.rect(0f, h * i / 4f, w, h / 4f + 1f, Colors.mix(top, bottom, i / 3f));
            }
        });
    }

    /** The loading background colour with the game's alpha (in place of the red or dark Mojang wash). */
    public static int loadingBackground(int vanilla) {
        return (vanilla & 0xFF000000) | 0x07070B;
    }

    /**
     * The loading screen's mark: the square sun rising out of the horizon line as the load goes on (0..1), glowing in
     * the theme's accent, its reflection under the line. Plain fills only: it also runs before resources are in.
     */
    public static void loading(Canvas c, int w, int h, float progress, float alpha) {
        loading++;
        if (alpha <= 0.01f) {
            return;
        }
        Guard.run(Feature.SCREENS, () -> {
            int accent = ThemeRegistry.all().isEmpty() ? 0xFFFFB347 : ThemeState.tokens().accent;
            int core = 0xFFFFF3C4;
            int cx = w / 2, hy = (int) (h * 0.6f);
            int s = Math.max(8, Math.min(w, h) / 22);
            float p = Math.max(0f, Math.min(1f, progress));
            // The sun's centre: hidden under the line at 0, a sun's height over it at 1.
            int sy = Math.round(hy + s * 0.6f - p * (s * 2.1f));
            int[] grow = {s * 3, s * 2, s + s / 2, s + s / 4};
            float[] glowA = {0.05f, 0.08f, 0.14f, 0.24f};
            for (int i = 0; i < grow.length; i++) {
                square(c, cx, sy, grow[i], hy, Colors.withAlpha(accent, glowA[i] * alpha));
            }
            square(c, cx, sy, s, hy, Colors.withAlpha(Colors.mix(accent, core, 0.55f), alpha));
            square(c, cx, sy, Math.max(2, s / 2), hy, Colors.withAlpha(core, alpha));
            // The horizon: brightest under the sun, fading out to the sides.
            int half = Math.max(60, w / 5);
            for (int i = 0; i < 12; i++) {
                int x0 = cx - half + half * i / 12, x1 = cx + half - half * i / 12;
                c.fill(x0, hy, x1, hy + 1, Colors.withAlpha(accent, 0.07f * alpha));
            }
            // The reflection: short bars under the line, shorter as they go down.
            float reflect = 0.25f + 0.5f * p;
            for (int i = 0; i < 4; i++) {
                int len = (int) (s * (1.2f - i * 0.25f) * reflect);
                int y = hy + 4 + i * 4;
                c.fill(cx - len, y, cx + len, y + 1, Colors.withAlpha(accent, (0.35f - i * 0.07f) * alpha * p));
            }
        });
    }

    /** A square of side {@code size} centred at (cx, cy), only where it's above {@code horizon}. */
    private static void square(Canvas c, int cx, int cy, int size, int horizon, int argb) {
        int x0 = cx - size / 2, y0 = cy - size / 2, x1 = x0 + size, y1 = Math.min(horizon, y0 + size);
        if (y1 > y0) {
            c.fill(x0, y0, x1, y1, argb);
        }
    }
}
