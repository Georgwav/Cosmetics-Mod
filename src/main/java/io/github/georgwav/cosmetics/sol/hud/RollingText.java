package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * A number that rolls instead of jumping (the brief's FPS, CPS and ping chips): in a mono font, each digit that
 * changes slides up out of its place while the new one slides in from below, over 180 ms. One per chip; it keeps
 * the text it showed last, so nothing is allocated while the value stays the same.
 */
public final class RollingText {
    private static final long ROLL_MS = 180L;
    /** One-character strings, made once. */
    private static final String[] CHARS = new String[128];

    static {
        for (int i = 0; i < CHARS.length; i++) {
            CHARS[i] = String.valueOf((char) i);
        }
    }

    private static String str(char c) {
        return c < CHARS.length ? CHARS[c] : String.valueOf(c);
    }
    private char[] shown = new char[0];
    private char[] before = new char[0];
    private long[] changed = new long[0];
    private String last = "";

    /** Draws {@code text} with its left at x and its middle at cy; returns its width. */
    public float draw(Gfx g, SolFont f, String text, float x, float cy, int argb) {
        update(text);
        float cw = g.width(f, "0");
        long now = System.nanoTime() / 1_000_000L;
        float lift = f.px / g.d.u * 0.9f;
        for (int i = 0; i < shown.length; i++) {
            float k = SolScreen.calm() ? 1f : Math.min(1f, (now - changed[i]) / (float) ROLL_MS);
            float cx = x + i * cw;
            if (k >= 1f) {
                g.textMid(f, str(shown[i]), cx, cy, argb);
                continue;
            }
            float e = 1f - (1f - k) * (1f - k);
            if (before[i] != ' ') {
                g.textMid(f, str(before[i]), cx, cy - lift * e, Colors.fade(argb, 1f - e));
            }
            g.textMid(f, str(shown[i]), cx, cy + lift * (1f - e), Colors.fade(argb, e));
        }
        return shown.length * cw;
    }

    /** The width {@code text} takes (mono: every character the same). */
    public static float width(Gfx g, SolFont f, String text) {
        return text.length() * g.width(f, "0");
    }

    private void update(String text) {
        if (text.equals(last)) {
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        int n = text.length();
        char[] next = text.toCharArray();
        char[] prev = new char[n];
        long[] when = new long[n];
        // Right-aligned: the ones digit stays the ones digit when the number grows a place.
        for (int i = 0; i < n; i++) {
            int j = i - (n - shown.length);
            char old = j >= 0 && j < shown.length ? shown[j] : ' ';
            boolean digit = Character.isDigit(next[i]) && Character.isDigit(old);
            if (old == next[i] || !digit) {
                prev[i] = old == next[i] ? old : ' ';
                when[i] = old == next[i] && j >= 0 ? changed[j] : (digit ? now : 0L);
            } else {
                prev[i] = old;
                when[i] = now;
            }
        }
        shown = next;
        before = prev;
        changed = when;
        last = text;
    }
}
