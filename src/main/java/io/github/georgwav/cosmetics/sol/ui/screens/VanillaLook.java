package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Design;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

/**
 * Sol UI's look on the game's own screens: glass buttons, glass tooltips, the pause screen's veil and the death
 * screen's dusk, and the loading screen's square sun rising as the progress bar. Each is drawn in the game's GUI
 * pixels, guarded; with Sol UI off (or after an error) the game draws its own.
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

    /** A glass button where the game draws its button sprite. */
    public static boolean button(Canvas c, int x, int y, int w, int h, boolean hover, boolean active) {
        buttons++;
        return draw(c, g -> {
            Tokens tk = ThemeState.tokens();
            float r = Math.min(h / 2f, 6f);
            if (hover && active) {
                g.glow(x, y, w, h, r, 4f, Colors.withAlpha(tk.accent, 0.32f));
            }
            g.round(x, y, w, h, r, active ? Colors.withAlpha(tk.panel, Math.max(0.68f, tk.glass)) : 0x8C0A0A0E);
            g.stroke(x, y, w, h, r, 1f / g.d.u, hover && active ? tk.accent : active ? 0x38FFFFFF : 0x1AFFFFFF);
            g.highlight(x, y, w, h, r, active ? 0x30FFFFFF : 0x10FFFFFF);
        });
    }

    /** A glass tooltip around a tooltip's text box (x, y, w, h), at depth {@code z} where the game lifts it. */
    public static boolean tooltip(Canvas c, int x, int y, int w, int h, int z) {
        tooltips++;
        if (z != 0) {
            c.push();
            c.lift(z);
        }
        boolean ok = draw(c, g -> {
            Tokens tk = ThemeState.tokens();
            float bx = x - 4f, by = y - 4f, bw = w + 8f, bh = h + 8f, r = 5f;
            g.glow(bx, by, bw, bh, r, 6f, Colors.withAlpha(tk.glow, 0.18f));
            g.round(bx, by, bw, bh, r, Colors.withAlpha(Colors.mix(tk.panel, 0xFF000000, 0.35f), 0.94f));
            g.stroke(bx, by, bw, bh, r, 1f / g.d.u, Colors.withAlpha(tk.rim, 0.55f));
            g.highlight(bx, by, bw, bh, r, 0x26FFFFFF);
        });
        if (z != 0) {
            c.pop();
        }
        return ok;
    }

    /** A list's chosen row: a soft glass of the accent with its rim. */
    public static boolean selection(Canvas c, int x, int y, int w, int h) {
        return draw(c, g -> {
            Tokens tk = ThemeState.tokens();
            g.round(x, y, w, h, 6f, Colors.withAlpha(tk.accent, 0.16f));
            g.stroke(x, y, w, h, 6f, 1f / g.d.u, Colors.withAlpha(tk.accent, 0.7f));
        });
    }

    /** The pause screen's veil over the game: the theme's band at the top fading into a dark floor. */
    public static boolean pauseVeil(Canvas c, int w, int h) {
        veils++;
        return draw(c, g -> {
            Tokens tk = ThemeState.tokens();
            g.vgradient(0f, 0f, w, h * 0.45f, Colors.withAlpha(tk.glow, 0.16f), 0x00000000);
            g.vgradient(0f, h * 0.45f, w, h * 0.55f, 0x00000000, 0x8C050508);
        });
    }

    /** The death screen's dusk (in place of the game's red wash): the theme's dark sky with an ember glow below. */
    public static boolean deathVeil(Canvas c, int w, int h) {
        veils++;
        return draw(c, g -> {
            Tokens tk = ThemeState.tokens();
            int top = Colors.withAlpha(Colors.mix(tk.skyTop, 0xFF000000, 0.55f), 0.78f);
            int bottom = Colors.withAlpha(Colors.mix(Tokens.RED, tk.skyGlow, 0.35f), 0.5f);
            g.vgradient(0f, 0f, w, h, top, bottom);
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
