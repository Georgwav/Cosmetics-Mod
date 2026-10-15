package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.render.Tint;
import io.github.georgwav.cosmetics.sol.theme.Tokens;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A colour picker in the Sol style, with every part the Cosmetics screen's had: the saturation and brightness
 * square, the hue bar, the colour with its hex code (type one in), the quick picks and a button back to the default.
 * The colour is kept as hue, saturation and value while dragging, so dragging to black keeps the hue.
 */
public final class ColorPicker {
    public static final float HEIGHT = 124f;
    private static final float SQUARE_W = 168f, BAR = 12f;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private float hue, sat, val;
    private int shown = -1;
    private final TextField hex;

    public ColorPicker(IntSupplier getter, IntConsumer setter) {
        this.getter = getter;
        this.setter = setter;
        this.hex = new TextField(7, text -> {
            String h = text.startsWith("#") ? text.substring(1) : text;
            if (h.length() == 6) {
                try {
                    apply(Integer.parseInt(h, 16));
                } catch (NumberFormatException e) {
                    // Not a colour yet: keep typing.
                }
            }
        });
    }

    public TextField hexField() {
        return hex;
    }

    private void sync() {
        int rgb = getter.getAsInt() & 0xFFFFFF;
        if (rgb != shown) {
            float[] hsv = Tint.rgbToHsv(rgb);
            // A grey or black has no hue of its own: keep the one being dragged.
            if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
                hue = hsv[0];
            }
            if (hsv[2] > 0.001f) {
                sat = hsv[1];
            }
            val = hsv[2];
            shown = rgb;
            if (!hex.focused()) {
                hex.set(String.format(java.util.Locale.ROOT, "#%06X", rgb));
            }
        }
    }

    private void apply(int rgb) {
        shown = -1;
        setter.accept(rgb & 0xFFFFFF);
    }

    private void applyHsv() {
        int rgb = Tint.hsvToRgb(hue, sat, val);
        shown = rgb;
        setter.accept(rgb);
        if (!hex.focused()) {
            hex.set(String.format(java.util.Locale.ROOT, "#%06X", rgb));
        }
    }

    /**
     * Draws it at (x, y), {@code w} wide: the square and bar on the left, the colour, hex code, {@code presets}
     * (RGB) and {@code resetLabel} (runs {@code reset}) on the right.
     */
    public void draw(Gfx g, Ui ui, Tokens tk, float x, float y, float w, List<Integer> presets, String resetLabel,
                     Runnable reset, float dt) {
        sync();
        float sh = HEIGHT - 8f, sy = y + 4f;
        // The square: saturation left to right, brightness top to bottom.
        int cols = 42;
        for (int i = 0; i < cols; i++) {
            float x0 = x + SQUARE_W * i / cols, x1 = x + SQUARE_W * (i + 1) / cols;
            int top = 0xFF000000 | Tint.hsvToRgb(hue, (i + 0.5f) / cols, 1f);
            g.vgradient(x0, sy, x1 - x0, sh, top, 0xFF000000);
        }
        g.stroke(x, sy, SQUARE_W, sh, 4f, Math.max(1f, g.d.u), 0x33FFFFFF);
        ui.drag(x, sy, SQUARE_W, sh, (px, py) -> {
            sat = clamp01((px - x) / SQUARE_W);
            val = 1f - clamp01((py - sy) / sh);
            applyHsv();
        });
        float cx = x + sat * SQUARE_W, cy = sy + (1f - val) * sh;
        g.ring(g.d.sx(cx), g.d.sy(cy), 5f * g.d.u, 1.5f * g.d.u, 0xFFFFFFFF);
        g.ring(g.d.sx(cx), g.d.sy(cy), 6.5f * g.d.u, Math.max(1f, g.d.u), 0x80000000);
        // The hue bar, red at the top round to red at the bottom.
        float bx = x + SQUARE_W + 10f;
        for (int k = 0; k < 6; k++) {
            float y0 = sy + sh * k / 6f, y1 = sy + sh * (k + 1) / 6f;
            g.vgradient(bx, y0, BAR, y1 - y0, 0xFF000000 | Tint.hsvToRgb(k / 6f, 1f, 1f),
                    0xFF000000 | Tint.hsvToRgb(((k + 1) % 6) / 6f, 1f, 1f));
        }
        ui.drag(bx - 3f, sy, BAR + 6f, sh, (px, py) -> {
            hue = Math.min(0.9999f, clamp01((py - sy) / sh));
            applyHsv();
        });
        float hy = sy + hue * sh;
        g.round(bx - 2f, hy - 2.5f, BAR + 4f, 5f, 2f, 0xFFFFFFFF);
        // The colour, its hex code, the quick picks and the way back.
        float px = bx + BAR + 16f;
        g.round(px, sy, 40f, 26f, 7f, 0xFF000000 | shown);
        g.stroke(px, sy, 40f, 26f, 7f, Math.max(1f, g.d.u), 0x40FFFFFF);
        hex.draw(g, ui, tk, px + 48f, sy, Math.min(120f, x + w - px - 48f), 26f, null, "#RRGGBB", dt);
        float swx = px, swy = sy + 36f;
        int perRow = Math.max(1, (int) ((x + w - px + 6f) / 26f));
        for (int i = 0; i < presets.size(); i++) {
            float qx = swx + (i % perRow) * 26f, qy = swy + (i / perRow) * 26f;
            int rgb = presets.get(i);
            ui.click(qx, qy, 20f, 20f, () -> apply(rgb));
            g.round(qx, qy, 20f, 20f, 6f, 0xFF000000 | rgb);
            g.stroke(qx, qy, 20f, 20f, 6f, Math.max(1f, g.d.u), ui.over(qx, qy, 20f, 20f) ? tk.rim : 0x40FFFFFF);
            if ((rgb & 0xFFFFFF) == shown) {
                g.stroke(qx - 2f, qy - 2f, 24f, 24f, 8f, 1.5f * g.d.u, 0xFFFFFFFF);
            }
        }
        if (resetLabel != null) {
            ui.button(tk, x + w, sy + sh - 11f, resetLabel, false, reset);
        }
    }

    public boolean key(int key, boolean ctrl) {
        return hex.key(key, ctrl);
    }

    public boolean typed(String s) {
        return hex.typed(s);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
