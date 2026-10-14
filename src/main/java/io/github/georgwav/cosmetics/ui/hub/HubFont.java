package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
//? if >=1.21.11 {
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
//?} elif >=1.21.9 {
/*import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.ResourceLocation;
*///?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * The hub's typeface, Geist (font/geist-*.ttf, SIL Open Font License). Each size it comes in is its own font
 * (font/geist_&lt;px&gt;.json, font/geist_semibold_&lt;px&gt;.json) made at exactly that many pixels, and the hub draws
 * text in screen pixels, so every glyph lands one to one on the screen and stays sharp at any window size.
 */
final class HubFont {
    /** The sizes there is a font for, in screen pixels: every pixel up to 24, then every 2, then every 4. */
    private static final int[] SIZES = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 28,
            30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 52, 56, 60, 64, 68, 72, 76, 80, 84, 88, 92, 96};
    private static final Style[] MEDIUM = new Style[SIZES.length];
    private static final Style[] SEMIBOLD = new Style[SIZES.length];

    /** How far below the point text is drawn at the game puts the baseline, in pixels, whatever the size. */
    static final float BASELINE = 7f;
    /** Geist's capital height and descender, as parts of its size. */
    static final float CAP = 0.71f;
    static final float DESCENT = 0.16f;

    final Font font;
    /** The size in screen pixels. */
    final int px;
    private final Style style;

    private HubFont(Font font, int index, boolean semibold) {
        this.font = font;
        this.px = SIZES[index];
        Style[] styles = semibold ? SEMIBOLD : MEDIUM;
        if (styles[index] == null) {
            styles[index] = Style.EMPTY.withFont(font((semibold ? "geist_semibold_" : "geist_") + px));
        }
        this.style = styles[index];
    }

    /** The size nearest to {@code px} screen pixels. */
    static HubFont of(Font font, float px, boolean semibold) {
        int best = 0;
        for (int i = 1; i < SIZES.length; i++) {
            if (Math.abs(SIZES[i] - px) < Math.abs(SIZES[best] - px)) {
                best = i;
            }
        }
        return new HubFont(font, best, semibold);
    }

    //? if >=1.21.11 {
    private static FontDescription font(String name) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, name));
    }
    //?} elif >=1.21.9 {
    /*private static FontDescription font(String name) {
        return new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, name));
    }
    *///?} else {
    /*private static ResourceLocation font(String name) {
        return ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, name);
    }
    *///?}

    Component component(String text) {
        return Component.literal(text).withStyle(style);
    }

    /** Width in screen pixels. */
    int width(String text) {
        return font.width(component(text));
    }

    /** Capital height in screen pixels. */
    float cap() {
        return px * CAP;
    }

    /** Text starting at {@code x} with its baseline at {@code baseline} (screen pixels). */
    void draw(Canvas g, String text, float x, float baseline, int color) {
        g.text(font, component(text), Math.round(x), Math.round(baseline - BASELINE), color);
    }

    /** Text with its capitals centred on {@code cy}: what sits in the middle of a pill or a row. */
    void drawMid(Canvas g, String text, float x, float cy, int color) {
        draw(g, text, x, cy + cap() / 2f, color);
    }

    /** Shortened with an ellipsis to fit {@code room} screen pixels. */
    String fit(String text, float room) {
        if (width(text) <= room) {
            return text;
        }
        int end = text.length();
        while (end > 0 && width(text.substring(0, end).stripTrailing() + "…") > room) {
            end--;
        }
        return text.substring(0, end).stripTrailing() + "…";
    }

    /** The end of the text that fits (what you just typed stays in view). */
    String fitEnd(String text, float room) {
        int start = 0;
        while (start < text.length() && width(text.substring(start)) > room) {
            start++;
        }
        return text.substring(start);
    }
}
