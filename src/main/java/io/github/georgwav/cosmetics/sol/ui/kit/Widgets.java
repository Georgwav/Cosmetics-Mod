package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.I18n;

/**
 * The Sol UI's small shared pieces in the pixel style: chips (a pinned module's chip glows when it's on, with the
 * logo's sparkle), the ON/OFF switch, key chips, dashed outlines; and the names menus show (themes, installed mods).
 */
public final class Widgets {
    private Widgets() {
    }

    /**
     * A chip in a row (38 high, 12 padding, Silkscreen 11): a pinned module (its icon and name, glowing when it's on,
     * off-grey when not), a button, or a choice that can be the current one (on, outlined in the accent).
     */
    public static final class Chip {
        public static final float H = 38f;
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

        /** A pinned module: its icon and its name, in the on glow (with the sparkle) or the off look. */
        public static Chip pin(String label, String icon, boolean on, Runnable toggle) {
            return new Chip(label, icon, on, true, toggle);
        }

        public static Chip button(String label, Runnable action) {
            return new Chip(label, null, false, false, action);
        }

        /** A choice chip, on (accent outline and text) when it is the current one. */
        public static Chip toggle(String label, boolean current, Runnable action) {
            return new Chip(label, null, current, false, action);
        }

        public Runnable action() {
            return action;
        }

        private SolFont font(Gfx g) {
            return g.font(SolFont.Family.PIXEL_BOLD, 11f);
        }

        public float width(Gfx g) {
            float w = g.width(font(g), label);
            return icon != null ? 12f + 24f + 8f + w + 12f + (pin && on ? 10f : 0f) : 12f + w + 12f;
        }

        public void draw(Gfx g, io.github.georgwav.cosmetics.sol.theme.Tokens tk, float x, float y, boolean focused, float mx, float my) {
            PixelTokens p = PixelTokens.get();
            float w = width(g);
            boolean over = mx >= x && mx <= x + w && my >= y && my <= y + H;
            SolFont f = font(g);
            int textColour;
            if (pin && on) {
                Pix.onGlow(g, p, x, y, w, H, over);
                textColour = p.enText;
            } else if (pin) {
                Pix.slab(g, p, x, y, w, H, over ? Pix.HOVER : Pix.NORMAL);
                textColour = Pix.offText(over);
            } else {
                int st = on ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL;
                Pix.slab(g, p, x, y, w, H, st);
                textColour = Pix.text(p, st);
            }
            float tx = x + 12f;
            if (icon != null) {
                Icons.draw(g, icon, tx, y + (H - 24f) / 2f, 24f, textColour, 0f, 0);
                tx += 24f + 8f;
            }
            if (pin && on) {
                Pix.onText(g, p, f, label, tx, y + H / 2f);
                Pix.sparkle(g, p, x + w, y, (System.nanoTime() / 1_000_000L % 2_400_000L) / 1000f, SolScreen.calm());
            } else {
                g.textMid(f, label, tx, y + H / 2f, textColour);
            }
            if (focused) {
                Pix.focus(g, x, y, w, H);
            }
        }
    }

    /** The pixel switch at (x, y), {@link Ui#SWITCH_W} x {@link Ui#SWITCH_H}: an on-glow slab saying ON, or OFF. */
    public static void pixelSwitch(Gfx g, float x, float y, boolean on, boolean hover) {
        PixelTokens p = PixelTokens.get();
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 10f);
        String t = on ? "ON" : "OFF";
        float tx = x + (Ui.SWITCH_W - g.width(f, t)) / 2f, cy = y + Ui.SWITCH_H / 2f;
        if (on) {
            Pix.onGlow(g, p, x, y, Ui.SWITCH_W, Ui.SWITCH_H, hover);
            Pix.onText(g, p, f, t, tx, cy);
        } else {
            Pix.slab(g, p, x, y, Ui.SWITCH_W, Ui.SWITCH_H, hover ? Pix.HOVER : Pix.NORMAL);
            g.textMid(f, t, tx, cy, Pix.offText(hover));
        }
    }

    /** The small switch where a 30 x 18 one was (right edge and middle kept). */
    public static void smallSwitch(Gfx g, io.github.georgwav.cosmetics.sol.theme.Tokens tk, float x, float y, boolean on) {
        pixelSwitch(g, x + 30f - Ui.SWITCH_W, y + 9f - Ui.SWITCH_H / 2f, on, false);
    }

    /** The full switch where a 36 x 22 one was. */
    public static void bigSwitch(Gfx g, io.github.georgwav.cosmetics.sol.theme.Tokens tk, float x, float y, boolean on, float knob) {
        smallSwitch(g, tk, x + 6f, y + 2f, on);
    }

    /** A key chip ("CTRL K") ending at {@code right}, centred on {@code cy}. */
    public static void kbd(Gfx g, String text, float right, float cy) {
        Pix.keyChip(g, PixelTokens.get(), text, right, cy);
    }

    /** A dashed outline (the Empty preset's card, an empty slot): 6 px dashes on whole pixels, square corners. */
    public static void dashed(Gfx g, float x, float y, float w, float h, float r, int argb) {
        float dash = 6f, gap = 4f, t = 2f;
        for (float u = x; u < x + w; u += dash + gap) {
            float e = Math.min(u + dash, x + w);
            g.rect(u, y, e - u, t, argb);
            g.rect(u, y + h - t, e - u, t, argb);
        }
        for (float u = y; u < y + h; u += dash + gap) {
            float e = Math.min(u + dash, y + h);
            g.rect(x, u, t, e - u, argb);
            g.rect(x + w - t, u, t, e - u, argb);
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

    /** The theme's one line ("Cherry blossom at golden hour"), translated where the language has it. */
    public static String themeLine(ThemeRegistry.Entry e) {
        if (e == null) {
            return "";
        }
        String key = "sol.theme." + e.id() + ".line";
        return has(key) ? I18n.get(key) : e.base().line();
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
