package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The HUD modules' kit, from the brief's HUD stage (units are GUI pixels, half the prototype's CSS pixels): glass
 * chips and cards (the theme's panel at its glass darkness, a faint white rim, a lit top edge and a soft glow), the
 * accent dot, durability-style bars going from the accent to amber to red, and the states' colours.
 */
public final class HudKit {
    /** The prototype's .hc radius (12 px) and the chips' (999 px, round). */
    public static final float CARD_R = 6f;
    /** Text sizes: the prototype's 12 px body, 10.5 px small. */
    public static final float TEXT = 6.5f;
    public static final float SMALL = 5.5f;

    private HudKit() {
    }

    /** A glass card (the prototype's .hc). */
    public static void card(Gfx g, Tokens tk, float x, float y, float w, float h, float r) {
        g.glow(x, y, w, h, r, 5f, Colors.withAlpha(tk.glow, 0.14f));
        g.round(x, y, w, h, r, Colors.withAlpha(tk.panel, Math.max(0.55f, tk.glass)));
        g.stroke(x, y, w, h, r, 1f / g.d.u, 0x24FFFFFF);
        g.highlight(x, y, w, h, r, 0x47FFFFFF);
    }

    /** The accent square with its glow (the prototype's .hstats i). */
    public static void dot(Gfx g, int color, float x, float cy) {
        g.glowSprite(x + 1.75f, cy, 4.5f, 4.5f, Colors.withAlpha(color, 0.55f));
        g.rect(x, cy - 1.75f, 3.5f, 3.5f, color);
    }

    /** The colour for a level from 0 (bad) to 1 (good): the accent, amber below {@code warn}, red below {@code crit}. */
    public static int state(Tokens tk, float k, float warn, float crit) {
        return k <= crit ? Tokens.RED : k <= warn ? Tokens.AMBER : tk.accent;
    }

    /** A thin bar (the prototype's .dur): a faint track, the fill in {@code color} with its glow. */
    public static void bar(Gfx g, float x, float y, float w, float h, float k, int color) {
        g.round(x, y, w, h, h / 2f, 0x24FFFFFF);
        if (k > 0f) {
            float fw = Math.max(h, w * Math.min(1f, k));
            g.glow(x, y, fw, h, h / 2f, 3f, Colors.withAlpha(color, 0.35f));
            g.round(x, y, fw, h, h / 2f, color);
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

    public static SolFont small(Gfx g) {
        return g.font(SolFont.Family.MEDIUM, SMALL);
    }

    public static SolFont mono(Gfx g) {
        return g.font(SolFont.Family.MONO, TEXT);
    }
}
