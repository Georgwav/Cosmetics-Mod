package io.github.georgwav.cosmetics.sol.sky;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The night sky's lettering: the Sol font (Geist Mono for the constellations, Geist for stars and worlds) cut into
 * glyphs at every pixel size from 6 to 28 ({@code sky/sky_text.dat} and {@code .png}, made by
 * {@code tools/sol-assets/make_sky_text.py}), so names are written into the sky one glyph at a time on whole pixels.
 * Pure Java; immutable once read.
 */
public final class SkyFont {
    public static final int MONO = 0, SANS = 1;

    /** A glyph: where it is in the picture, its offset from the pen on the baseline, and how far it moves the pen. */
    public record Glyph(int x, int y, int w, int h, int dx, int dy, int advance) {
    }

    /** One font at one pixel size. */
    public static final class Face {
        public final int family, size, ascent;
        private final Glyph[] ascii = new Glyph[128];
        private Glyph oUpper, oLower;

        Face(int family, int size, int ascent) {
            this.family = family;
            this.size = size;
            this.ascent = ascent;
        }

        void put(int code, Glyph g) {
            if (code < 128) {
                ascii[code] = g;
            } else if (code == 'Ö') {
                oUpper = g;
            } else if (code == 'ö') {
                oLower = g;
            }
        }

        /** The glyph for a character, or the space's for one the sky's letters don't have. */
        public Glyph glyph(char c) {
            Glyph g = c < 128 ? ascii[c] : c == 'Ö' ? oUpper : c == 'ö' ? oLower : null;
            if (g == null && c < 128 && Character.isLowerCase(c)) {
                g = ascii[Character.toUpperCase(c)];
            }
            return g != null ? g : ascii[' '];
        }

        /** The width of a line of text in pixels, with {@code spacing} pixels between letters. */
        public int width(String s, int spacing) {
            int w = 0;
            for (int i = 0; i < s.length(); i++) {
                w += glyph(s.charAt(i)).advance() + (i + 1 < s.length() ? spacing : 0);
            }
            return w;
        }
    }

    private final List<Face> faces;

    private SkyFont(List<Face> faces) {
        this.faces = faces;
    }

    /** The face of a family nearest to {@code px} pixels. */
    public Face face(int family, float px) {
        Face best = null;
        for (Face f : faces) {
            if (f.family == family && (best == null || Math.abs(f.size - px) < Math.abs(best.size - px))) {
                best = f;
            }
        }
        return best;
    }

    public static SkyFont read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (!"SOLT".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII)) || d.readInt() != 1) {
            throw new IOException("not a sky lettering file");
        }
        int n = d.readInt();
        List<Face> faces = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            Face f = new Face(d.readInt(), d.readInt(), d.readInt());
            int count = d.readInt();
            for (int g = 0; g < count; g++) {
                int code = d.readInt();
                f.put(code, new Glyph(d.readShort(), d.readShort(), d.readShort(), d.readShort(), d.readShort(), d.readShort(),
                        d.readShort()));
            }
            faces.add(f);
        }
        return new SkyFont(List.copyOf(faces));
    }
}
