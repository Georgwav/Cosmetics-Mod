package io.github.georgwav.cosmetics.sol.sky;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The real sky: every star down to magnitude 6.5 from the HYG database (CC BY-SA 4.0), with its position,
 * brightness and colour, the bright stars' names, and the constellations' lines and names (d3-celestial, BSD).
 * Read once from {@code sky/stars.dat} and {@code sky/constellations.dat} (made by
 * {@code tools/sol-assets/make_sky.py}). Positions are equatorial unit vectors. Pure Java; immutable once read.
 */
public final class StarCatalog {
    /** Equatorial unit vectors, magnitudes, colours (RGB) and a twinkle seed (0..1), brightest first. */
    public final float[] x, y, z, mag, seed;
    public final int[] color;
    /** Star index to its name, or null. */
    public final String[] name;
    public final List<Constellation> constellations;

    /** A constellation: id, English name, where its name goes, and its lines as pairs of unit vectors. */
    public record Constellation(String id, String name, float lx, float ly, float lz, float[] lines) {
        /** How many line segments. */
        public int segments() {
            return lines.length / 6;
        }
    }

    private StarCatalog(float[] x, float[] y, float[] z, float[] mag, float[] seed, int[] color, String[] name,
                        List<Constellation> constellations) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.mag = mag;
        this.seed = seed;
        this.color = color;
        this.name = name;
        this.constellations = constellations;
    }

    public int size() {
        return mag.length;
    }

    /** Reads the two data files. */
    public static StarCatalog read(InputStream stars, InputStream constellations) throws IOException {
        DataInputStream in = new DataInputStream(stars);
        expect(in, "SOLS");
        int n = in.readInt();
        float[] x = new float[n], y = new float[n], z = new float[n], mag = new float[n], seed = new float[n];
        int[] color = new int[n];
        double[] v = new double[3];
        for (int i = 0; i < n; i++) {
            float ra = in.readFloat(), dec = in.readFloat();
            mag[i] = in.readShort() / 100f;
            float bv = in.readShort() / 1000f;
            SkySphere.eq(ra, dec, v);
            x[i] = (float) v[0];
            y[i] = (float) v[1];
            z[i] = (float) v[2];
            color[i] = bvColor(bv);
            seed[i] = hash01(i, 3, 9);
        }
        String[] name = new String[n];
        int named = in.readInt();
        for (int k = 0; k < named; k++) {
            int i = in.readInt();
            String s = in.readUTF();
            if (i >= 0 && i < n) {
                name[i] = s;
            }
        }
        DataInputStream c = new DataInputStream(constellations);
        expect(c, "SOLC");
        int count = c.readInt();
        List<Constellation> list = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            String id = c.readUTF(), cname = c.readUTF();
            float lra = c.readFloat(), ldec = c.readFloat();
            SkySphere.eq(lra / 15.0, ldec, v);
            float lx = (float) v[0], ly = (float) v[1], lz = (float) v[2];
            int segs = c.readInt();
            float[] lines = new float[segs * 6];
            for (int s = 0; s < segs; s++) {
                for (int e = 0; e < 2; e++) {
                    float ra = c.readFloat(), dec = c.readFloat();
                    SkySphere.eq(ra / 15.0, dec, v);
                    lines[s * 6 + e * 3] = (float) v[0];
                    lines[s * 6 + e * 3 + 1] = (float) v[1];
                    lines[s * 6 + e * 3 + 2] = (float) v[2];
                }
            }
            list.add(new Constellation(id, cname, lx, ly, lz, lines));
        }
        return new StarCatalog(x, y, z, mag, seed, color, name, List.copyOf(list));
    }

    private static void expect(DataInputStream in, String magic) throws IOException {
        byte[] b = new byte[4];
        in.readFully(b);
        if (!magic.equals(new String(b, java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("not a " + magic + " file");
        }
        int version = in.readInt();
        if (version != 1) {
            throw new IOException(magic + " version " + version);
        }
    }

    /** The lab's star colours by B-V index: blue-white through white and yellow to orange-red. */
    private static final float[] BV_AT = {-0.3f, 0f, 0.35f, 0.6f, 0.9f, 1.3f, 1.85f};
    private static final int[] BV_RGB = {0xA0BEFF, 0xD6E2FF, 0xF6F6FF, 0xFFF4DE, 0xFFE2BA, 0xFFC896, 0xFFA674};

    public static int bvColor(float bv) {
        if (bv <= BV_AT[0]) {
            return 0xFF000000 | BV_RGB[0];
        }
        for (int i = 1; i < BV_AT.length; i++) {
            if (bv <= BV_AT[i]) {
                float t = (bv - BV_AT[i - 1]) / (BV_AT[i] - BV_AT[i - 1]);
                int a = BV_RGB[i - 1], b = BV_RGB[i];
                int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
                int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
                int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
                return 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
        }
        return 0xFF000000 | BV_RGB[BV_RGB.length - 1];
    }

    /** The lab's {@code h3} hash, 0..1. */
    static float hash01(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1440662683;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (float) ((h & 0xFFFFFFFFL) / 4294967296.0);
    }
}
