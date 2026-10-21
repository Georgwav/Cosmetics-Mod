package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;

/**
 * The pixel UI's colours (the prototype's palette): Dawn's slabs in greys pulled toward the preset's sky, lit by its
 * sun, the accent for what's chosen and the "on" colours for what's switched on (the theme's accent, or green when
 * Settings › Sol UI › On glows says so). Worked out again whenever the preset, the time of day's colours, the accent
 * or that setting changes, so reading one is a field load. Every mix is a linear sRGB mix, as in the prototype.
 */
public final class PixelTokens {
    public static final int INK = 0xFFF1EDE6, INK2 = 0xFFB3AEB6, INK3 = 0xFF7C7781, RED = 0xFFE5483F;
    /** A slab's text, off text, and the text of a hovered slab. */
    public static final int SLAB_TEXT = 0xFFD9D4DC, OFF_TEXT = 0xFF8D8892, HOVER_TEXT = 0xFFFFFFFF;
    /** The red close's fill. */
    public static final int CLOSE_FILL = 0xFF2A1212;
    public static final int GREEN = 0xFF5FD13A;

    public int acc, accHi, accTint, c0, c1, c2, rec, edge, glow, hi, lo;
    public int en, enDark, enText, enHi, enA1, enA2;
    public boolean green;

    private static final PixelTokens CURRENT = new PixelTokens();
    private static int seenGeneration = Integer.MIN_VALUE;
    private static String seenGlows;
    private static SolTexture onTexture;
    private static int onTextureKey;

    /** The tokens for the current preset (render thread). */
    public static PixelTokens get() {
        Tokens tk = ThemeState.tokens();
        String glows = SolConfig.get().onGlows;
        if (tk.generation != seenGeneration || !java.util.Objects.equals(glows, seenGlows)) {
            seenGeneration = tk.generation;
            seenGlows = glows;
            CURRENT.set(tk.accent, tk.skyTop, tk.glow, "green".equals(glows));
        }
        return CURRENT;
    }

    /** Works the palette out from the accent, the preset's top sky colour and its glow. */
    public PixelTokens set(int accent, int skyTop, int glowColour, boolean greenOn) {
        acc = 0xFF000000 | accent;
        accHi = Colors.mixRgb(acc, 0xFFFFFFFF, 0.35f);
        accTint = Colors.mixRgb(0xFF2E2E32, acc, 0.24f);
        c0 = Colors.mixRgb(0xFF232326, skyTop, 0.07f);
        c1 = Colors.mixRgb(0xFF2E2E32, skyTop, 0.08f);
        c2 = Colors.mixRgb(0xFF3A3A3F, skyTop, 0.09f);
        rec = Colors.mixRgb(0xFF19191C, skyTop, 0.06f);
        edge = Colors.mixRgb(0xFF050506, skyTop, 0.18f);
        glow = 0xFF000000 | glowColour;
        hi = Colors.withAlpha(Colors.mixRgb(glow, 0xFFFFFFFF, 0.45f), 0.2f);
        lo = 0x66000000;
        green = greenOn;
        en = greenOn ? GREEN : acc;
        enDark = greenOn ? 0xFF14300C : Colors.mixRgb(en, 0xFF0A0A0C, 0.78f);
        enText = greenOn ? 0xFFB4FA8C : Colors.mixRgb(en, 0xFFFFFFFF, 0.45f);
        enHi = Colors.withAlpha(Colors.mixRgb(en, 0xFFFFFFFF, 0.4f), 0.4f);
        enA1 = Colors.withAlpha(en, 0.34f);
        enA2 = Colors.withAlpha(en, 0.14f);
        return this;
    }

    /**
     * The on texture's pixels: a 32 x 16 tile of {@code dark} with 46 specks (1 to 3 pixels wide, 1 to 2 tall), each a
     * touch lighter (55% of them) or darker, seeded as the prototype's (Mulberry32 11).
     */
    public static int[] onPixels(int en, int dark) {
        int light = Colors.mixRgb(dark, en, 0.16f), shade = Colors.mixRgb(dark, 0xFF000000, 0.22f);
        int[] px = new int[32 * 16];
        java.util.Arrays.fill(px, 0xFF000000 | dark);
        Mulberry32 r = new Mulberry32(11);
        for (int i = 0; i < 46; i++) {
            int c = r.next() < 0.55 ? light : shade;
            int x = (int) Math.floor(r.next() * 32), y = (int) Math.floor(r.next() * 16);
            int w = 1 + (int) Math.floor(r.next() * 3), h = 1 + (int) Math.floor(r.next() * 2);
            for (int yy = y; yy < Math.min(16, y + h); yy++) {
                for (int xx = x; xx < Math.min(32, x + w); xx++) {
                    px[yy * 32 + xx] = c;
                }
            }
        }
        return px;
    }

    /** The on texture for the current "on" colour (nearest filtered, made again when the colour changes). */
    public SolTexture onTexture() {
        int key = en * 31 + enDark;
        if (onTexture == null || onTexture.released() || key != onTextureKey) {
            int[] px = onPixels(en, enDark);
            if (onTexture == null || onTexture.released()) {
                onTexture = SolTexture.of("on_texture", 32, 16, false, px);
            } else {
                onTexture.upload(px);
            }
            onTextureKey = key;
        }
        return onTexture;
    }
}
