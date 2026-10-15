package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.I18n;

/**
 * The Sol UI's small shared pieces, drawn the prototypes' way: chips and pins with their switch, key caps, the
 * small switch, dashed outlines; and the names menus show (themes, installed mods).
 */
public final class Widgets {
    private Widgets() {
    }

    /** A pill in a row: a pinned module with its switch, a button, or a choice that can be the current one. */
    public static final class Chip {
        public static final float H = 30f;
        private final String label;
        private final String icon;
        private final boolean on;
        private final boolean pin;
        private final Runnable action;

        private Chip(String label, String icon, boolean on, boolean pin, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.on = on;
            this.pin = pin;
            this.action = action;
        }

        /** A pinned module: its icon, its name and its switch (the prototype's .pin). */
        public static Chip pin(String label, String icon, boolean on, Runnable toggle) {
            return new Chip(label, icon, on, true, toggle);
        }

        public static Chip button(String label, Runnable action) {
            return new Chip(label, null, false, false, action);
        }

        /** A choice chip, outlined in the accent when it is the current one (the prototype's .emo.cur). */
        public static Chip toggle(String label, boolean current, Runnable action) {
            return new Chip(label, null, current, false, action);
        }

        public Runnable action() {
            return action;
        }

        private SolFont font(Gfx g) {
            return g.font(SolFont.Family.MEDIUM, 12.5f);
        }

        public float width(Gfx g) {
            float w = g.width(font(g), label);
            return pin ? 11f + 14f + 8f + w + 8f + 30f + 7f : 12f + w + 12f;
        }

        public void draw(Gfx g, Tokens tk, float x, float y, boolean focused, float mx, float my) {
            float w = width(g);
            boolean over = mx >= x && mx <= x + w && my >= y && my <= y + H;
            g.round(x, y, w, H, H / 2f, tk.panel);
            int rim = pin ? (on ? tk.rim : 0x24FFFFFF) : on ? tk.accent : over ? tk.rim : 0x24FFFFFF;
            g.stroke(x, y, w, H, H / 2f, Math.max(1f, g.d.u), rim);
            g.highlight(x, y, w, H, H / 2f, 0x2EFFFFFF);
            SolFont f = font(g);
            if (pin) {
                Icons.draw(g, icon, x + 11f, y + (H - 14f) / 2f, 14f, on ? tk.accent : 0x8CFFFFFF, on ? Icons.FILL_ON : Icons.FILL, 0);
                g.textMid(f, label, x + 11f + 14f + 8f, y + H / 2f, 0xFFFFFFFF);
                smallSwitch(g, tk, x + w - 7f - 30f, y + (H - 18f) / 2f, on);
            } else {
                g.textMid(f, label, x + 12f, y + H / 2f, on ? tk.accent : 0xFFFFFFFF);
            }
            if (focused) {
                g.stroke(x - 3f, y - 3f, w + 6f, H + 6f, H / 2f + 3f, 2f * g.d.u, tk.accent);
            }
        }
    }

    /** The small switch (30 x 18): the accent with its knob right when on. */
    public static void smallSwitch(Gfx g, Tokens tk, float x, float y, boolean on) {
        g.round(x, y, 30f, 18f, 9f, on ? tk.accent : 0x29FFFFFF);
        float kx = on ? x + 30f - 3f - 12f : x + 3f;
        g.circle(kx + 6f, y + 9f, 6f, on ? tk.onAccent : 0xFFFFFFFF);
    }

    /** The full switch (36 x 22). */
    public static void bigSwitch(Gfx g, Tokens tk, float x, float y, boolean on, float knob) {
        g.round(x, y, 36f, 22f, 11f, on ? tk.accent : 0x29FFFFFF);
        float kx = x + 3f + 14f * knob;
        g.circle(kx + 8f, y + 11f, 8f, on ? tk.onAccent : 0xFFFFFFFF);
    }

    /** A key cap ("Ctrl K") ending at {@code right}, centred on {@code cy}. */
    public static void kbd(Gfx g, String text, float right, float cy) {
        SolFont f = g.font(SolFont.Family.MONO, 11f);
        float w = g.width(f, text) + 14f, h = 20f;
        float x = right - w;
        g.round(x, cy - h / 2f, w, h, 6f, 0x1AFFFFFF);
        g.stroke(x, cy - h / 2f, w, h, 6f, Math.max(1f, g.d.u), 0x29FFFFFF);
        g.textMid(f, text, x + 7f, cy, 0xFFFFFFFF);
    }

    /** A dashed rounded outline (the Empty preset's card, an empty slot). */
    public static void dashed(Gfx g, float x, float y, float w, float h, float r, int argb) {
        float dash = 6f, gap = 4f;
        float lw = Math.max(1f, g.d.u);
        for (float t = x + r; t < x + w - r; t += dash + gap) {
            float e = Math.min(t + dash, x + w - r);
            g.line(t, y, e, y, lw, argb);
            g.line(t, y + h, e, y + h, lw, argb);
        }
        for (float t = y + r; t < y + h - r; t += dash + gap) {
            float e = Math.min(t + dash, y + h - r);
            g.line(x, t, x, e, lw, argb);
            g.line(x + w, t, x + w, e, lw, argb);
        }
        // Quarter arcs at the corners.
        float[][] corners = {{x + r, y + r, 180f}, {x + w - r, y + r, 270f}, {x + w - r, y + h - r, 0f}, {x + r, y + h - r, 90f}};
        float[] px = new float[7], py = new float[7];
        for (float[] c : corners) {
            for (int i = 0; i < 7; i++) {
                double a = Math.toRadians(c[2] + 90.0 * i / 6.0);
                px[i] = g.d.sx(c[0] + r * (float) Math.cos(a));
                py[i] = g.d.sy(c[1] + r * (float) Math.sin(a));
            }
            g.strokePath(px, py, 7, false, lw, g.flat(argb));
        }
    }

    /** A theme's name: the language file's for a built-in theme, the file's own otherwise. */
    public static String themeName(ThemeRegistry.Entry e) {
        if (e == null) {
            return "";
        }
        String key = "sol.theme." + e.id() + ".name";
        return has(key) ? I18n.get(key) : e.base().name();
    }

    /** Whether the language has this key (26.2 removed I18n.exists; a missing key translates to itself). */
    public static boolean has(String key) {
        return !net.minecraft.network.chat.Component.translatable(key).getString().equals(key);
    }

    /** How many mods are installed besides the game, the loader, Fabric API's parts and Sol itself. */
    public static int installedMods() {
        int n = 0;
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("cosmeticsmod")
                    || id.startsWith("fabric-") || id.equals("fabric-api") || id.equals("mixinextras")
                    || mod.getContainingMod().isPresent()) {
                continue;
            }
            n++;
        }
        return n;
    }

    /** The accent at the given alpha, for glows. */
    public static int glow(Tokens tk, float alpha) {
        return Colors.withAlpha(tk.glow, alpha);
    }
}
