package io.github.georgwav.cosmetics.ui;

import io.github.georgwav.cosmetics.CosmeticsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
//? if >=1.21.9 {
import net.minecraft.network.chat.FontDescription;
//?}

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which characters the Geist fonts have, read from each TTF's character map, so text can fall back to the game's
 * own font for the rest (a name in another script, a symbol). The Geist font files have no fallback of their own:
 * one to the game's default font made every one of the 117 sizes build its own copy of the whole of Unifont on each
 * resource reload, which held the loading screen up for seconds.
 */
public final class GeistGlyphs {
    private static final Map<String, BitSet> COVERAGE = new ConcurrentHashMap<>();

    /** The game's own font, loaded only when some text needs it (the character map is read in plain unit tests). */
    private static final class Game {
        //? if >=1.21.9 {
        static final Style FONT = Style.EMPTY.withFont(FontDescription.DEFAULT);
        //?} else {
        /*static final Style FONT = Style.EMPTY.withFont(Style.DEFAULT_FONT);
        *///?}
    }

    private GeistGlyphs() {
    }

    /** {@code text} in {@code style}, with any run of characters {@code ttf} hasn't got in the game's own font. */
    public static Component styled(String text, Style style, String ttf) {
        BitSet has = coverage(ttf);
        int i = 0, n = text.length();
        while (i < n) {
            int cp = text.codePointAt(i);
            if (!covered(has, cp)) {
                break;
            }
            i += Character.charCount(cp);
        }
        if (i == n) {
            return Component.literal(text).withStyle(style);
        }
        MutableComponent out = Component.empty();
        int start = 0;
        boolean geist = true;
        for (i = 0; i < n; ) {
            int cp = text.codePointAt(i);
            boolean c = covered(has, cp);
            if (c != geist && i > start) {
                out.append(Component.literal(text.substring(start, i)).withStyle(geist ? style : Game.FONT));
                start = i;
            }
            geist = c;
            i += Character.charCount(cp);
        }
        out.append(Component.literal(text.substring(start)).withStyle(geist ? style : Game.FONT));
        return out;
    }

    private static boolean covered(BitSet has, int cp) {
        // Without the map (a pack swapped the file for one we can't read) everything is drawn in Geist, as before.
        return has == null || has.get(cp) || cp < 0x20;
    }

    /** The characters {@code ttf} (a file in assets/cosmeticsmod/font) has, or null if it can't be read. */
    static BitSet coverage(String ttf) {
        BitSet has = COVERAGE.get(ttf);
        if (has == null) {
            try (InputStream in = GeistGlyphs.class.getResourceAsStream("/assets/" + CosmeticsMod.MOD_ID + "/font/" + ttf)) {
                has = in == null ? new BitSet() : characters(ByteBuffer.wrap(in.readAllBytes()));
            } catch (IOException | RuntimeException e) {
                CosmeticsMod.LOGGER.warn("Couldn't read the characters of {}: {}", ttf, e.toString());
                has = new BitSet();
            }
            if (has.isEmpty()) {
                has.set(0x20, 0x7F); // at least what every font has
            }
            COVERAGE.put(ttf, has);
        }
        return has;
    }

    /** The characters a TrueType or OpenType font maps to a glyph (its cmap, format 4 or 12). */
    static BitSet characters(ByteBuffer f) {
        BitSet out = new BitSet();
        int tables = f.getShort(4) & 0xFFFF, cmap = -1;
        for (int t = 0; t < tables; t++) {
            int rec = 12 + t * 16;
            if (f.getInt(rec) == 0x636D6170) { // "cmap"
                cmap = f.getInt(rec + 8);
            }
        }
        if (cmap < 0) {
            return out;
        }
        int subtables = f.getShort(cmap + 2) & 0xFFFF, best = -1, bestRank = 0;
        for (int s = 0; s < subtables; s++) {
            int rec = cmap + 4 + s * 8;
            int platform = f.getShort(rec) & 0xFFFF, encoding = f.getShort(rec + 2) & 0xFFFF;
            int at = cmap + f.getInt(rec + 4), format = f.getShort(at) & 0xFFFF;
            // Unicode full repertoire (format 12) over the BMP (format 4).
            int rank = format == 12 && (platform == 0 || platform == 3 && encoding == 10) ? 2
                    : format == 4 && (platform == 0 || platform == 3 && encoding == 1) ? 1 : 0;
            if (rank > bestRank) {
                bestRank = rank;
                best = at;
            }
        }
        if (best < 0) {
            return out;
        }
        if (bestRank == 2) {
            int groups = f.getInt(best + 12);
            for (int g = 0; g < groups; g++) {
                int rec = best + 16 + g * 12;
                int first = f.getInt(rec), last = f.getInt(rec + 4), glyph = f.getInt(rec + 8);
                if (first >= 0 && last >= first && last < 0x110000) {
                    out.set(glyph == 0 ? first + 1 : first, last + 1);
                }
            }
            return out;
        }
        int segs = (f.getShort(best + 6) & 0xFFFF) / 2;
        int ends = best + 14, starts = ends + segs * 2 + 2, deltas = starts + segs * 2, offsets = deltas + segs * 2;
        for (int s = 0; s < segs; s++) {
            int end = f.getShort(ends + s * 2) & 0xFFFF, start = f.getShort(starts + s * 2) & 0xFFFF;
            int delta = f.getShort(deltas + s * 2), offsetAt = offsets + s * 2, offset = f.getShort(offsetAt) & 0xFFFF;
            for (int c = start; c <= end && c != 0xFFFF; c++) {
                int glyph;
                if (offset == 0) {
                    glyph = (c + delta) & 0xFFFF;
                } else {
                    int at = offsetAt + offset + 2 * (c - start);
                    glyph = at + 1 < f.limit() ? f.getShort(at) & 0xFFFF : 0;
                    if (glyph != 0) {
                        glyph = (glyph + delta) & 0xFFFF;
                    }
                }
                if (glyph != 0) {
                    out.set(c);
                }
            }
        }
        return out;
    }
}
