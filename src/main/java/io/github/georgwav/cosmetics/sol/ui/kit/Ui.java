package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The Sol screens' controls, immediate mode: each frame a screen draws its controls through this, and each one
 * leaves a hit area (and a place in the keyboard order) for that frame's clicks, drags and Enter. The controls are
 * the Sol menu prototype's: switches, segmented choices, sliders, swatches, light buttons, pills and filter chips,
 * with its sizes in design pixels.
 */
public final class Ui {
    /** A slider being dragged: told where the pointer is. */
    public interface Drag {
        void to(float x, float y);
    }

    private record Hit(float x0, float y0, float x1, float y1, Runnable click, Drag drag, int focus) {
        boolean contains(float x, float y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> last = new ArrayList<>();
    private Hit dragging;
    private int focusCount;
    /** The keyboard focus (index in this frame's order), -1 for none. */
    public int focus = -1;
    public float mx = -1000f, my = -1000f;
    private Gfx g;

    public void begin(Gfx g, float mx, float my) {
        this.g = g;
        this.mx = mx;
        this.my = my;
        last.clear();
        last.addAll(hits);
        hits.clear();
        focusCount = 0;
    }

    /** How many focusable controls the last frame had. */
    public int focusables() {
        return focusCount;
    }

    /** Whether the pointer is over a design rectangle (and inside the current clip). */
    public boolean over(float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h && g.inClip(mx, my);
    }

    private final float[] clip = new float[4];

    /** Registers a clickable rectangle (clamped to the current clip); returns its focus index. */
    public int click(float x, float y, float w, float h, Runnable action) {
        int f = focusCount++;
        add(x, y, w, h, action, null, f);
        return f;
    }

    /** A rectangle that takes clicks but isn't in the keyboard order (a backdrop, a scroll area). */
    public void area(float x, float y, float w, float h, Runnable action) {
        add(x, y, w, h, action, null, -1);
    }

    private void add(float x, float y, float w, float h, Runnable action, Drag drag, int focusIndex) {
        float x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        if (g.clipRect(clip)) {
            x0 = Math.max(x0, clip[0]);
            y0 = Math.max(y0, clip[1]);
            x1 = Math.min(x1, clip[2]);
            y1 = Math.min(y1, clip[3]);
            if (x1 <= x0 || y1 <= y0) {
                return;
            }
        }
        hits.add(new Hit(x0, y0, x1, y1, action, drag, focusIndex));
    }

    public int drag(float x, float y, float w, float h, Drag d) {
        int f = focusCount++;
        add(x, y, w, h, null, d, f);
        return f;
    }

    public boolean focused(int index) {
        return focus == index;
    }

    /** A click: the topmost hit under the pointer. */
    public boolean press(float x, float y) {
        for (int i = last.size() - 1; i >= 0; i--) {
            Hit h = last.get(i);
            if (h.contains(x, y)) {
                if (h.drag() != null) {
                    dragging = h;
                    h.drag().to(x, y);
                } else if (h.click() != null) {
                    h.click().run();
                }
                return true;
            }
        }
        return false;
    }

    public boolean dragTo(float x, float y) {
        if (dragging != null) {
            dragging.drag().to(x, y);
            return true;
        }
        return false;
    }

    public boolean release() {
        boolean was = dragging != null;
        dragging = null;
        return was;
    }

    /** Enter or Space on the focused control. */
    public boolean activate() {
        for (Hit h : last) {
            if (h.focus() == focus && focus >= 0 && h.click() != null) {
                h.click().run();
                return true;
            }
        }
        return false;
    }

    /** Tab: the next (or previous) control. */
    public void tab(boolean back) {
        int n = Math.max(1, focusCount);
        focus = focus < 0 ? (back ? n - 1 : 0) : (focus + (back ? n - 1 : 1)) % n;
    }

    private void ring(float x, float y, float w, float h, float r, Tokens tk) {
        g.stroke(x - 2f, y - 2f, w + 4f, h + 4f, r + 2f, 2f * g.d.u, tk.accent);
    }

    // ------------------------------------------------------------------ controls

    /** The small switch (30 x 18). */
    public void smallSwitch(Tokens tk, float x, float y, boolean on, Runnable toggle) {
        int f = click(x - 4f, y - 4f, 38f, 26f, toggle);
        Widgets.smallSwitch(g, tk, x, y, on);
        if (focused(f)) {
            ring(x, y, 30f, 18f, 9f, tk);
        }
    }

    /** The full switch (36 x 22), its knob following {@code knob} (0..1, sprung by the caller). */
    public void bigSwitch(Tokens tk, float x, float y, boolean on, float knob, Runnable toggle) {
        int f = click(x - 4f, y - 4f, 44f, 30f, toggle);
        Widgets.bigSwitch(g, tk, x, y, on, knob);
        if (focused(f)) {
            ring(x, y, 36f, 22f, 11f, tk);
        }
    }

    /** A segmented choice ending at {@code right}; returns its width. */
    public float segmented(Tokens tk, float right, float cy, String[] options, int selected, IntConsumer pick) {
        SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
        float w = 6f;
        for (String o : options) {
            w += g.width(f, o) + 18f + 2f;
        }
        w -= 2f;
        float x = right - w, h = 26f, y = cy - h / 2f;
        g.round(x, y, w, h, 9f, 0x0FFFFFFF);
        float bx = x + 3f;
        for (int i = 0; i < options.length; i++) {
            float bw = g.width(f, options[i]) + 18f;
            boolean on = i == selected;
            final int idx = i;
            int fi = click(bx, y + 3f, bw, h - 6f, () -> pick.accept(idx));
            if (on) {
                g.round(bx, y + 3f, bw, h - 6f, 7f, tk.accent);
            } else if (over(bx, y + 3f, bw, h - 6f)) {
                g.round(bx, y + 3f, bw, h - 6f, 7f, 0x14FFFFFF);
            }
            g.textMid(f, options[i], bx + 9f, cy, on ? tk.onAccent : 0xB8FFFFFF);
            if (focused(fi)) {
                ring(bx, y + 3f, bw, h - 6f, 7f, tk);
            }
            bx += bw + 2f;
        }
        return w;
    }

    /** A slider 140 wide ending before its value; {@code value} 0..1. */
    public void slider(Tokens tk, float right, float cy, float value, String text, Consumer<Float> set) {
        SolFont mono = g.font(SolFont.Family.MONO, 11.5f);
        float tw = Math.max(36f, g.width(mono, text));
        g.textRight(mono, text, right, cy, 0xB3FFFFFF);
        float w = 140f, x = right - tw - 8f - w;
        float v = Math.max(0f, Math.min(1f, value));
        int f = drag(x - 4f, cy - 10f, w + 8f, 20f, (px, py) -> set.accept(Math.max(0f, Math.min(1f, (px - x) / w))));
        g.round(x, cy - 2f, w, 4f, 2f, 0x29FFFFFF);
        g.round(x, cy - 2f, w * v, 4f, 2f, tk.accent);
        g.circle(x + w * v, cy, 7f, 0xFFFFFFFF);
        if (focused(f)) {
            g.ring(g.d.sx(x + w * v), g.d.sy(cy), 9f * g.d.u, 2f * g.d.u, tk.accent);
        }
    }

    /** A row of colour swatches (20 x 20) ending at {@code right}; {@code accent} first is the theme's accent. */
    public void swatches(Tokens tk, float right, float cy, int[] colors, int selected, IntConsumer pick) {
        float x = right - colors.length * 25f + 5f;
        for (int i = 0; i < colors.length; i++) {
            float sx = x + i * 25f, sy = cy - 10f;
            final int idx = i;
            int f = click(sx, sy, 20f, 20f, () -> pick.accept(idx));
            g.round(sx, sy, 20f, 20f, 6f, colors[i]);
            g.stroke(sx, sy, 20f, 20f, 6f, Math.max(1f, g.d.u), 0x40FFFFFF);
            if (i == selected) {
                g.stroke(sx - 2f, sy - 2f, 24f, 24f, 8f, 2f * g.d.u, 0xFFFFFFFF);
            }
            if (focused(f)) {
                ring(sx - 2f, sy - 2f, 24f, 24f, 8f, tk);
            }
        }
    }

    /** A light button (the prototype's .lbtn) ending at {@code right}; returns its width. */
    public float button(Tokens tk, float right, float cy, String text, boolean accent, Runnable action) {
        SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
        float w = g.width(f, text) + 20f, h = 26f, x = right - w, y = cy - h / 2f;
        int fi = click(x, y, w, h, action);
        boolean hover = over(x, y, w, h);
        g.round(x, y, w, h, 8f, accent ? tk.accent : 0x14FFFFFF);
        g.stroke(x, y, w, h, 8f, Math.max(1f, g.d.u), accent ? tk.accent : hover ? tk.rim : 0x1FFFFFFF);
        g.textMid(f, text, x + 10f, cy, accent ? tk.onAccent : 0xFFFFFFFF);
        if (focused(fi)) {
            ring(x, y, w, h, 8f, tk);
        }
        return w;
    }

    /** A pill label (the prototype's .pill) ending at {@code right}; returns its width. */
    public float pill(Tokens tk, float right, float cy, String text, boolean accent) {
        SolFont f = g.font(SolFont.Family.MONO, 11f);
        float w = g.width(f, text) + 16f, h = 18f, x = right - w;
        g.stroke(x, cy - h / 2f, w, h, h / 2f, Math.max(1f, g.d.u), accent ? tk.rim : 0x33FFFFFF);
        g.textMid(f, text, x + 8f, cy, accent ? tk.accent : 0xBFFFFFFF);
        return w;
    }

    /** A filter chip (the prototype's .fchip) starting at {@code x}; returns its width. */
    public float chip(Tokens tk, float x, float cy, String text, boolean pressed, Runnable action) {
        SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
        float w = g.width(f, text) + 22f, h = 24f, y = cy - h / 2f;
        int fi = click(x, y, w, h, action);
        boolean hover = over(x, y, w, h);
        g.round(x, y, w, h, h / 2f, pressed ? tk.accent : 0x0DFFFFFF);
        g.stroke(x, y, w, h, h / 2f, Math.max(1f, g.d.u), pressed ? tk.accent : hover ? 0x40FFFFFF : 0x1AFFFFFF);
        g.textMid(f, text, x + 11f, cy, pressed ? tk.onAccent : 0xBDFFFFFF);
        if (focused(fi)) {
            ring(x, y, w, h, h / 2f, tk);
        }
        return w;
    }

    /** A small square tool button (26 x 26) with an icon. */
    public void tool(Tokens tk, float x, float y, String icon, boolean lit, Runnable action) {
        int fi = click(x, y, 26f, 26f, action);
        g.round(x, y, 26f, 26f, 7f, over(x, y, 26f, 26f) ? 0x29FFFFFF : 0x14FFFFFF);
        Icons.draw(g, icon, x + 7f, y + 7f, 12f, lit ? tk.accent : 0xFFFFFFFF, lit ? Icons.FILL_ON : Icons.FILL, 0);
        if (focused(fi)) {
            ring(x, y, 26f, 26f, 7f, tk);
        }
    }

    /** The colour a control's label takes. */
    public static int text(boolean dim) {
        return dim ? Colors.withAlpha(0xFFFFFFFF, 0.56f) : 0xFFFFFFFF;
    }
}
