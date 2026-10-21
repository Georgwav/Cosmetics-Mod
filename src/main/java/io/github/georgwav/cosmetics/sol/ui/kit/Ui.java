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
 * the pixel UI's ({@link Pix}): on-glow and off switches, segmented slabs, sliders on a recess, swatches, slab
 * buttons, pills and chips, with their sizes in design pixels. A control pressed with the mouse moves down 3 px.
 */
public final class Ui {
    /** A slider being dragged: told where the pointer is. */
    public interface Drag {
        void to(float x, float y);
    }

    /** A hit's sound when clicked (UI sounds): a click, a switch going on or off, or none (a backdrop, a scroll area). */
    private static final int CLICK = 0, TURNS_ON = 1, TURNS_OFF = 2, QUIET = -1;

    private record Hit(float x0, float y0, float x1, float y1, Runnable click, Drag drag, int focus, int sound) {
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
        add(x, y, w, h, action, null, f, CLICK);
        return f;
    }

    /** A switch that's {@code on} now: a click that sounds its toggle. */
    private int toggleClick(float x, float y, float w, float h, boolean on, Runnable action) {
        int f = focusCount++;
        add(x, y, w, h, action, null, f, on ? TURNS_OFF : TURNS_ON);
        return f;
    }

    private static void sound(Hit h) {
        switch (h.sound()) {
            case CLICK -> UiSounds.click();
            case TURNS_ON -> UiSounds.toggle(true);
            case TURNS_OFF -> UiSounds.toggle(false);
            default -> {
            }
        }
    }

    /** A rectangle that takes clicks but isn't in the keyboard order (a backdrop, a scroll area). */
    public void area(float x, float y, float w, float h, Runnable action) {
        add(x, y, w, h, action, null, -1, QUIET);
    }

    private void add(float x, float y, float w, float h, Runnable action, Drag drag, int focusIndex, int sound) {
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
        hits.add(new Hit(x0, y0, x1, y1, action, drag, focusIndex, sound));
    }

    public int drag(float x, float y, float w, float h, Drag d) {
        int f = focusCount++;
        add(x, y, w, h, null, d, f, QUIET);
        return f;
    }

    public boolean focused(int index) {
        return focus == index;
    }

    /** Whether the mouse button is held (a held slab shows pressed). */
    public boolean down;

    /** Whether the pointer is over a design rectangle while the button is held. */
    public boolean pressed(float x, float y, float w, float h) {
        return down && over(x, y, w, h);
    }

    /** A click: the topmost hit under the pointer. */
    public boolean press(float x, float y) {
        down = true;
        for (int i = last.size() - 1; i >= 0; i--) {
            Hit h = last.get(i);
            if (h.contains(x, y)) {
                if (h.drag() != null) {
                    dragging = h;
                    h.drag().to(x, y);
                } else if (h.click() != null) {
                    h.click().run();
                    sound(h);
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
        down = false;
        boolean was = dragging != null;
        dragging = null;
        return was;
    }

    /** Enter or Space on the focused control. */
    public boolean activate() {
        for (Hit h : last) {
            if (h.focus() == focus && focus >= 0 && h.click() != null) {
                h.click().run();
                sound(h);
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
        Pix.focus(g, x, y, w, h);
    }

    private int state(float x, float y, float w, float h, boolean on) {
        return on ? Pix.ON : pressed(x, y, w, h) ? Pix.PRESSED : over(x, y, w, h) ? Pix.HOVER : Pix.NORMAL;
    }

    // ------------------------------------------------------------------ controls

    /** The pixel switch's size: a small on-glow or off slab saying ON or OFF. */
    public static final float SWITCH_W = 52f, SWITCH_H = 24f;

    /**
     * The small switch, where the old 30 x 18 one was (its right edge and middle): an on-glow slab saying ON, or an
     * off slab saying OFF.
     */
    public void smallSwitch(Tokens tk, float x, float y, boolean on, Runnable toggle) {
        float sx = x + 30f - SWITCH_W, sy = y + 9f - SWITCH_H / 2f;
        int f = toggleClick(sx - 2f, sy - 2f, SWITCH_W + 4f, SWITCH_H + 4f, on, toggle);
        Widgets.pixelSwitch(g, sx, sy, on, over(sx, sy, SWITCH_W, SWITCH_H));
        if (focused(f)) {
            Pix.focus(g, sx, sy, SWITCH_W, SWITCH_H);
        }
    }

    /** The full switch, where the old 36 x 22 one was ({@code knob} is no longer needed: the switch doesn't slide). */
    public void bigSwitch(Tokens tk, float x, float y, boolean on, float knob, Runnable toggle) {
        smallSwitch(tk, x + 6f, y + 2f, on, toggle);
    }

    /** A segmented choice ending at {@code right}: a row of slabs, the chosen one on; returns its width. */
    public float segmented(Tokens tk, float right, float cy, String[] options, int selected, IntConsumer pick) {
        PixelTokens p = PixelTokens.get();
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        float w = 0f;
        for (String o : options) {
            w += g.width(f, o) + 20f + 4f;
        }
        w -= 4f;
        float x = right - w, h = 28f, y = cy - h / 2f;
        float bx = x;
        for (int i = 0; i < options.length; i++) {
            float bw = g.width(f, options[i]) + 20f;
            boolean on = i == selected;
            final int idx = i;
            int fi = click(bx, y, bw, h, () -> pick.accept(idx));
            int st = state(bx, y, bw, h, on);
            Pix.slab(g, p, bx, y, bw, h, st);
            g.textMid(f, options[i], bx + 10f, cy + (st == Pix.PRESSED ? Pix.E : 0f), Pix.text(p, st));
            if (focused(fi)) {
                ring(bx, y, bw, h, 0f, tk);
            }
            bx += bw + 4f;
        }
        return w;
    }

    /** The width {@link #segmented} takes for these options. */
    public float segmentedWidth(String[] options) {
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        float w = 0f;
        for (String o : options) {
            w += g.width(f, o) + 20f + 4f;
        }
        return w - 4f;
    }

    /** The width {@link #chip} takes for this text. */
    public float chipWidth(String text) {
        return g.width(g.font(SolFont.Family.PIXEL_BOLD, 11f), text) + 24f;
    }

    /** The width {@link #button} takes for this text. */
    public float buttonWidth(String text) {
        return g.width(g.font(SolFont.Family.PIXEL_BOLD, 12f), text) + 24f;
    }

    /** A slider 140 wide ending before its value: an accent fill in a recess track and a slab knob; {@code value} 0..1. */
    public void slider(Tokens tk, float right, float cy, float value, String text, Consumer<Float> set) {
        PixelTokens p = PixelTokens.get();
        SolFont mono = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        float tw = Math.max(40f, g.width(mono, text));
        g.textRight(mono, text, right, cy, PixelTokens.INK2);
        float w = 140f, x = right - tw - 10f - w;
        float v = Math.max(0f, Math.min(1f, value));
        int f = drag(x - 4f, cy - 12f, w + 8f, 24f, (px, py) -> set.accept(Math.max(0f, Math.min(1f, (px - x) / w))));
        Pix.recess(g, p, x, cy - 6f, w, 12f);
        g.rect(x + 3f, cy - 3f, Math.max(0f, (w - 6f) * v), 6f, p.acc);
        float kx = x + w * v - 6f;
        Pix.slab(g, p, kx, cy - 11f, 12f, 22f, over(kx, cy - 11f, 12f, 22f) || focused(f) ? Pix.HOVER : Pix.NORMAL);
        if (focused(f)) {
            ring(kx, cy - 11f, 12f, 22f, 0f, tk);
        }
    }

    /** A row of colour swatches (24 x 20) ending at {@code right}; the chosen one outlined in white. */
    public void swatches(Tokens tk, float right, float cy, int[] colors, int selected, IntConsumer pick) {
        PixelTokens p = PixelTokens.get();
        float sw = 24f, sh = 20f, gap = 8f;
        float x = right - colors.length * (sw + gap) + gap;
        for (int i = 0; i < colors.length; i++) {
            float sx = x + i * (sw + gap), sy = cy - sh / 2f;
            final int idx = i;
            int f = click(sx, sy, sw, sh, () -> pick.accept(idx));
            Pix.swatch(g, p, sx, sy, sw, sh, colors[i], i == selected);
            if (focused(f) && i != selected) {
                ring(sx, sy, sw, sh, 0f, tk);
            }
        }
    }

    /** A slab button (30 high, Silkscreen 12) ending at {@code right}; {@code accent} draws it on. Returns its width. */
    public float button(Tokens tk, float right, float cy, String text, boolean accent, Runnable action) {
        PixelTokens p = PixelTokens.get();
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 12f);
        float w = g.width(f, text) + 24f, h = 30f, x = right - w, y = cy - h / 2f;
        int fi = click(x, y, w, h, action);
        int st = state(x, y, w, h, accent);
        Pix.slab(g, p, x, y, w, h, st);
        g.textMid(f, text, x + 12f, cy + (st == Pix.PRESSED ? Pix.E : 0f), Pix.text(p, st));
        if (focused(fi)) {
            ring(x, y, w, h, 0f, tk);
        }
        return w;
    }

    /**
     * A button that opens a list under it: as {@link #button}, its choice then a pixel caret pointing down (drawn on
     * whole screen pixels). Returns its width.
     */
    public float dropButton(Tokens tk, float right, float cy, String text, boolean open, Runnable action) {
        PixelTokens p = PixelTokens.get();
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 12f);
        int k = Math.max(1, Math.round(g.d.u * 1.25f));
        float caret = 7f * k / g.d.u;
        float w = g.width(f, text) + 24f + 8f + caret, h = 30f, x = right - w, y = cy - h / 2f;
        int fi = click(x, y, w, h, action);
        int st = state(x, y, w, h, open);
        float dy = st == Pix.PRESSED ? Pix.E : 0f;
        Pix.slab(g, p, x, y, w, h, st);
        int c = Pix.text(p, st);
        g.textMid(f, text, x + 12f, cy + dy, c);
        float sx = Math.round(g.d.sx(right - 12f - caret)), sy = Math.round(g.d.sy(cy + dy) - 2f * k);
        for (int r = 0; r < 4; r++) {
            g.rectPx(sx + r * k, sy + r * k, sx + (7 - r) * k, sy + (r + 1) * k, c);
        }
        if (focused(fi)) {
            ring(x, y, w, h, 0f, tk);
        }
        return w;
    }

    /**
     * The kit's button (A.6): a slab 42 high, 16 padding, its icon (2x) then a 9 px gap, Silkscreen 13; on draws it in
     * the accent, ghost in ink2. Starts at {@code x}; returns its width.
     */
    public float bigButton(float x, float y, String icon, String text, boolean on, boolean ghost, Runnable action) {
        PixelTokens p = PixelTokens.get();
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 13f);
        float w = 32f + (icon != null ? 24f + 9f : 0f) + g.width(f, text), h = 42f;
        int fi = click(x, y, w, h, action);
        int st = state(x, y, w, h, on);
        float dy = st == Pix.PRESSED ? Pix.E : 0f;
        Pix.slab(g, p, x, y, w, h, st);
        int c = ghost && st != Pix.ON && st != Pix.HOVER ? PixelTokens.INK2 : Pix.text(p, st);
        float tx = x + 16f;
        if (icon != null) {
            Icons.draw(g, icon, tx, y + h / 2f - 12f + dy, 24f, c, 0f, 0);
            tx += 24f + 9f;
        }
        g.textMid(f, text, tx, y + h / 2f + dy, c);
        if (focused(fi)) {
            ring(x, y, w, h, 0f, null);
        }
        return w;
    }

    /** Width of {@link #bigButton}. */
    public float bigButtonWidth(String icon, String text) {
        return 32f + (icon != null ? 24f + 9f : 0f) + g.width(g.font(SolFont.Family.PIXEL_BOLD, 13f), text);
    }

    /** A pill label ending at {@code right} (Silkscreen 10 in a thin frame); returns its width. */
    public float pill(Tokens tk, float right, float cy, String text, boolean accent) {
        PixelTokens p = PixelTokens.get();
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 10f);
        float w = g.width(f, text) + 14f, h = 20f, x = right - w;
        g.stroke(x, cy - h / 2f, w, h, 0f, g.px(2f), accent ? p.acc : p.edge);
        g.textMid(f, text, x + 7f, cy, accent ? p.acc : PixelTokens.INK2);
        return w;
    }

    /** A filter chip (a slab, Silkscreen 11, 28 high) starting at {@code x}; returns its width. */
    public float chip(Tokens tk, float x, float cy, String text, boolean pressed, Runnable action) {
        PixelTokens p = PixelTokens.get();
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        float w = g.width(f, text) + 24f, h = 28f, y = cy - h / 2f;
        int fi = click(x, y, w, h, action);
        int st = state(x, y, w, h, pressed);
        Pix.slab(g, p, x, y, w, h, st);
        g.textMid(f, text, x + 12f, cy + (st == Pix.PRESSED ? Pix.E : 0f), Pix.text(p, st));
        if (focused(fi)) {
            ring(x, y, w, h, 0f, tk);
        }
        return w;
    }

    /** A small square tool slab (26 x 26) with an icon; lit draws it on. */
    public void tool(Tokens tk, float x, float y, String icon, boolean lit, Runnable action) {
        PixelTokens p = PixelTokens.get();
        int fi = click(x, y, 26f, 26f, action);
        int st = state(x, y, 26f, 26f, lit);
        Pix.slab(g, p, x, y, 26f, 26f, st);
        Icons.draw(g, icon, x + 1f, y + 1f + (st == Pix.PRESSED ? Pix.E : 0f), 24f, Pix.text(p, st), 0f, 0);
        if (focused(fi)) {
            ring(x, y, 26f, 26f, 0f, tk);
        }
    }

    /** The colour a control's label takes. */
    public static int text(boolean dim) {
        return dim ? PixelTokens.INK3 : PixelTokens.INK;
    }
}
