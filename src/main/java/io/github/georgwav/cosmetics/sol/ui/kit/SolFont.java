package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Sol's type: Geist (medium and semibold) and Geist Mono (font/geist-*.ttf, SIL Open Font License). Each size is
 * its own font made at exactly that many screen pixels (font/geist_&lt;px&gt;.json and friends), and the kit draws in
 * screen pixels, so glyphs land one to one on the screen and stay sharp at any GUI scale. A size in design pixels
 * becomes the nearest size there is. Widths and text components are cached per font, so measuring and drawing the
 * same strings again allocates nothing.
 */
public final class SolFont {
    public enum Family {
        MEDIUM("geist_"), SEMIBOLD("geist_semibold_"), MONO("geist_mono_");

        final String prefix;

        Family(String prefix) {
            this.prefix = prefix;
        }
    }

    private static final int[] SIZES = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 28,
            30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 52, 56, 60, 64, 68, 72, 76, 80, 84, 88, 92, 96};
    private static final int MONO_MAX = 48;
    private static final SolFont[][] FONTS = new SolFont[Family.values().length][SIZES.length];
    private static final int CACHE = 600;

    /** How far below the point the game draws text at its baseline is, in pixels, whatever the size. */
    static final float BASELINE = 7f;
    /** Geist's capital height, as a part of its size. */
    static final float CAP = 0.71f;

    public final Family family;
    /** The size in screen pixels. */
    public final int px;
    private final Style style;
    private final Map<String, Component> components = new HashMap<>();
    private final Map<String, Integer> widths = new HashMap<>();
    private final Component[] chars = new Component[128];

    private SolFont(Family family, int px) {
        this.family = family;
        this.px = px;
        this.style = Style.EMPTY.withFont(font(family.prefix + px));
    }

    /** The font of {@code family} nearest to {@code screenPx} screen pixels. */
    public static SolFont of(Family family, float screenPx) {
        int best = 0;
        for (int i = 1; i < SIZES.length; i++) {
            if (family == Family.MONO && SIZES[i] > MONO_MAX) {
                break;
            }
            if (Math.abs(SIZES[i] - screenPx) < Math.abs(SIZES[best] - screenPx)) {
                best = i;
            }
        }
        SolFont f = FONTS[family.ordinal()][best];
        if (f == null) {
            f = new SolFont(family, SIZES[best]);
            FONTS[family.ordinal()][best] = f;
        }
        return f;
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

    private static Font game() {
        return Minecraft.getInstance().font;
    }

    Component component(String text) {
        if (text == null) {
            text = "";
        }
        Component c = components.get(text);
        if (c == null) {
            if (components.size() > CACHE) {
                components.clear();
            }
            c = Component.literal(text).withStyle(style);
            components.put(text, c);
        }
        return c;
    }

    private Component charComponent(char ch) {
        if (ch < 128) {
            Component c = chars[ch];
            if (c == null) {
                c = Component.literal(String.valueOf(ch)).withStyle(style);
                chars[ch] = c;
            }
            return c;
        }
        return component(String.valueOf(ch));
    }

    /** Width in screen pixels. */
    public int width(String text) {
        Integer w = widths.get(text);
        if (w == null) {
            if (widths.size() > CACHE) {
                widths.clear();
            }
            w = game().width(component(text));
            widths.put(text, w);
        }
        return w;
    }

    /** Width with {@code spacing} extra screen pixels after every character but the last. */
    public float width(String text, float spacing) {
        return width(text) + Math.max(0, text.length() - 1) * spacing;
    }

    /** Capital height in screen pixels. */
    public float cap() {
        return px * CAP;
    }

    /** Text starting at {@code x} with its baseline at {@code baseline}, both in screen pixels. */
    void draw(Canvas g, String text, float x, float baseline, int argb) {
        g.text(game(), component(text), Math.round(x), Math.round(baseline - BASELINE), argb);
    }

    /** Letter-spaced text (the prototypes' spaced mono captions), character by character. */
    void drawSpaced(Canvas g, String text, float x, float baseline, float spacing, int argb) {
        Font font = game();
        float pen = x;
        int y = Math.round(baseline - BASELINE);
        for (int i = 0; i < text.length(); i++) {
            Component c = charComponent(text.charAt(i));
            g.text(font, c, Math.round(pen), y, argb);
            pen += font.width(c) + spacing;
        }
    }

    /** Shortened with an ellipsis to fit {@code room} screen pixels. */
    public String fit(String text, float room) {
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
    public String fitEnd(String text, float room) {
        int start = 0;
        while (start < text.length() && width(text.substring(start)) > room) {
            start++;
        }
        return text.substring(start);
    }
}
