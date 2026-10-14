package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.Font;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * One frame of the hub's drawing: the 1440 x 810 design mapped onto the screen, the hub's text and its controls
 * (buttons, switches, sliders, choices), and what can be clicked or dragged this frame. Coordinates are design
 * pixels; drawing happens in screen pixels (see {@link Shapes}).
 */
final class Ui {
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0x9EFFFFFF;
    static final int TEXT_FAINT = 0x66FFFFFF;
    static final int WELL = 0xFF1C1C1F;
    static final int CARD = 0xFF111113;
    static final int LINE = 0x10FFFFFF;

    /** Something that can be clicked (and maybe dragged) this frame. */
    record Hit(float x0, float y0, float x1, float y1, Runnable action, DoubleConsumer drag) {
        boolean contains(float x, float y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    final Canvas g;
    final Font font;
    final float u;
    final float ox;
    final float oy;
    final float gs;
    final int textScale;
    final float mx;
    final float my;
    final int accent;
    final int ink;
    private final List<Hit> hits;
    /** Values that ease towards their targets across frames (a switch's knob), by key. */
    private final Map<String, Float> eased;

    Ui(Canvas g, Font font, float u, float ox, float oy, float gs, int textScale, float mx, float my, int accent,
       int ink, List<Hit> hits, Map<String, Float> eased) {
        this.g = g;
        this.font = font;
        this.u = u;
        this.ox = ox;
        this.oy = oy;
        this.gs = gs;
        this.textScale = textScale;
        this.mx = mx;
        this.my = my;
        this.accent = accent;
        this.ink = ink;
        this.hits = hits;
        this.eased = eased;
    }

    static Map<String, Float> newEasing() {
        return new HashMap<>();
    }

    float sx(float x) {
        return ox + x * u;
    }

    float sy(float y) {
        return oy + y * u;
    }

    /** Moves the value for {@code key} part of the way to {@code target} each frame; the first time, straight there. */
    float ease(String key, float target) {
        Float now = eased.get(key);
        float next = now == null ? target : now + (target - now) * 0.3f;
        if (Math.abs(next - target) < 0.002f) {
            next = target;
        }
        eased.put(key, next);
        return next;
    }

    boolean hover(float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    void click(float x, float y, float w, float h, Runnable action) {
        hits.add(new Hit(x, y, x + w, y + h, action, null));
    }

    void drag(float x, float y, float w, float h, DoubleConsumer drag) {
        hits.add(new Hit(x, y, x + w, y + h, null, drag));
    }

    // ------------------------------------------------------------------ shapes

    void roundRect(float x, float y, float w, float h, float r, int argb) {
        Shapes.fill(g, Shapes.roundRect(sx(x), sy(y), w * u, h * u, r * u), argb);
    }

    void circle(float cx, float cy, float r, int argb) {
        Shapes.fill(g, Shapes.circle(sx(cx), sy(cy), r * u), argb);
    }

    void icon(String name, float cx, float cy, float size, boolean dark) {
        int s = Math.max(1, Math.round(size * u));
        g.blit(HubScreen.icon(dark ? name + "_dark" : name), Math.round(sx(cx) - s / 2f), Math.round(sy(cy) - s / 2f), s, s, 64);
    }

    // ------------------------------------------------------------------ text

    float lineHeight() {
        return 9f * textScale / u;
    }

    float textWidth(String text) {
        return font.width(text) * textScale / u;
    }

    /** Text with its top left at (x, y), in the hub's text size ({@code scale} times it when larger). */
    void text(String text, float x, float y, int color) {
        text(text, x, y, color, textScale);
    }

    void text(String text, float x, float y, int color, int scale) {
        g.push();
        g.translate(Math.round(sx(x)), Math.round(sy(y)));
        g.scale(scale, scale);
        g.text(font, text, 0, 0, color);
        g.pop();
    }

    /** Text centred vertically on {@code cy}. */
    void textMid(String text, float x, float cy, int color) {
        text(text, x, cy - lineHeight() / 2f + 0.5f / u, color);
    }

    /** Shortened with an ellipsis to fit {@code room} design pixels. */
    String fit(String text, float room) {
        int max = Math.round(room * u / textScale);
        if (font.width(text) <= max) {
            return text;
        }
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + "…") > max) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    // ------------------------------------------------------------------ controls

    /** A pill button; returns its width. */
    float button(String label, float x, float y, float h, boolean filled, Runnable action) {
        float w = textWidth(label) + 28f;
        boolean hot = hover(x, y, w, h);
        int bg = filled ? accent : hot ? 0xFF26262A : WELL;
        roundRect(x, y, w, h, h / 2f, bg);
        textMid(label, x + 14f, y + h / 2f, filled ? ink : TEXT);
        click(x, y, w, h, action);
        return w;
    }

    /** The same button ending at {@code right}; returns its left edge. */
    float buttonRight(String label, float right, float y, float h, boolean filled, Runnable action) {
        float w = textWidth(label) + 28f;
        button(label, right - w, y, h, filled, action);
        return right - w;
    }

    /** A round icon button; {@code action} on click. */
    void iconButton(String icon, float cx, float cy, float r, boolean on, Runnable action) {
        boolean hot = hover(cx - r, cy - r, r * 2, r * 2);
        circle(cx, cy, r, on ? accent : hot ? 0xFF26262A : WELL);
        icon(icon, cx, cy, r * 0.95f, on && isInkDark());
        click(cx - r, cy - r, r * 2, r * 2, action);
    }

    boolean isInkDark() {
        return ink != 0xFFFFFFFF;
    }

    /** An on/off switch, 52 x 32, its knob sliding over. */
    void toggle(String key, float x, float y, boolean on, Runnable flip) {
        float k = ease("toggle:" + key, on ? 1f : 0f);
        roundRect(x, y, 52f, 32f, 16f, Shapes.mix(0xFF2A2A2E, accent, k));
        circle(x + 16f + 20f * k, y + 16f, 12f, Shapes.mix(0xFF8A8A90, isInkDark() ? 0xFF111114 : 0xFFFFFFFF, k));
        click(x - 4f, y - 6f, 60f, 44f, flip);
    }

    /** A slider from min to max; dragging (or clicking) it sets the value. */
    void slider(String key, float x, float y, float w, double min, double max, double value, DoubleConsumer set) {
        float t = (float) ((value - min) / (max - min));
        float k = ease("slider:" + key, Math.max(0f, Math.min(1f, t)));
        float cy = y + 16f;
        roundRect(x, cy - 3f, w, 6f, 3f, 0xFF2A2A2E);
        roundRect(x, cy - 3f, Math.max(6f, w * k), 6f, 3f, accent);
        circle(x + w * k, cy, 10f, 0xFFFFFFFF);
        drag(x - 10f, y, w + 20f, 32f, px -> {
            double f = Math.max(0.0, Math.min(1.0, (px - x) / w));
            set.accept(min + f * (max - min));
        });
    }

    /** Pills for one of a few options; returns the width. */
    float choice(String key, float x, float y, List<String> labels, int selected, IntConsumer pick) {
        float h = 36f;
        float w = 6f;
        for (String label : labels) {
            w += textWidth(label) + 24f;
        }
        roundRect(x, y, w, h, h / 2f, WELL);
        float px = x + 3f;
        for (int i = 0; i < labels.size(); i++) {
            float bw = textWidth(labels.get(i)) + 24f;
            if (i == selected) {
                roundRect(px, y + 3f, bw, h - 6f, (h - 6f) / 2f, accent);
            }
            textMid(labels.get(i), px + 12f, y + h / 2f, i == selected ? ink : 0xCCFFFFFF);
            int index = i;
            click(px, y + 3f, bw, h - 6f, () -> pick.accept(index));
            px += bw;
        }
        return w;
    }
}
