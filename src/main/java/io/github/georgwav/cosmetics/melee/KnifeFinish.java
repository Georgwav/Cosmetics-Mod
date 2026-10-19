package io.github.georgwav.cosmetics.melee;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Sol's own finishes for a pack's knife, made from that knife's texture and slot picture when the knives are mounted
 * (nothing of a pack is kept in the mod). Ruby: the blade's metal through a ruby ramp by its brightness, with the
 * cloudy light and dark patches of a Doppler finish; the dark metal and the violet grip wrap go near black. Pure Java.
 */
public final class KnifeFinish {
    /** The finishes there are, by the name the overrides file uses. */
    public static final String RUBY = "ruby";

    /** The ruby ramp: brightness (0 to 1), then red, green, blue. */
    private static final float[][] RUBY_RAMP = {
            {0.28f, 170, 15, 42}, {0.35f, 190, 19, 47}, {0.45f, 212, 32, 59}, {0.55f, 230, 48, 73}, {0.65f, 244, 82, 106},
            {0.75f, 250, 104, 124}, {0.85f, 254, 135, 151}, {0.95f, 255, 174, 182}, {1f, 255, 184, 192}};

    private KnifeFinish() {
    }

    /** Whether {@code finish} is one this class makes. */
    public static boolean known(String finish) {
        return RUBY.equals(finish == null ? null : finish.toLowerCase(Locale.ROOT));
    }

    /** A PNG with the finish applied, as a PNG. */
    public static byte[] png(byte[] png, String finish) throws IOException {
        BufferedImage in = ImageIO.read(new ByteArrayInputStream(png));
        if (in == null) {
            throw new IOException("not a picture");
        }
        int w = in.getWidth(), h = in.getHeight();
        int[] argb = in.getRGB(0, 0, w, h, null, 0, w);
        int[] out = apply(argb, w, h, finish);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        return bytes.toByteArray();
    }

    /** The finish over a picture's pixels (ARGB, {@code w} by {@code h}); see-through pixels stay as they are. */
    public static int[] apply(int[] argb, int w, int h, String finish) {
        if (!known(finish)) {
            throw new IllegalArgumentException("no finish " + finish);
        }
        int[] out = new int[argb.length];
        // The patches' size follows the picture's: a slot picture or a 128 px texture gets patches a few pixels across.
        float cell = Math.max(2f, Math.max(w, h) / 32f);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x, c = argb[i], a = c >>> 24;
                if (a == 0) {
                    out[i] = c;
                    continue;
                }
                out[i] = a << 24 | ruby(c, x, y, cell);
            }
        }
        return out;
    }

    /** One pixel's ruby colour (RGB). */
    static int ruby(int c, int x, int y, float cell) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float sat = max <= 0f ? 0f : (max - min) / max;
        float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float hue = hue(r, g, b, max, min);
        if (lum < 0.28f || sat >= 0.12f && hue >= 0.66f && hue <= 0.97f) {
            // Dark metal and the grip's wrap: near black, a little red.
            return rgb(6f + 75f * lum, 1.5f + 19f * lum, 2f + 25f * lum);
        }
        float[] k = RUBY_RAMP[RUBY_RAMP.length - 1];
        float cr = k[1], cg = k[2], cb = k[3];
        for (int s = 0; s < RUBY_RAMP.length - 1; s++) {
            float[] lo = RUBY_RAMP[s], hi = RUBY_RAMP[s + 1];
            if (lum <= hi[0]) {
                float t = Math.max(0f, (lum - lo[0]) / (hi[0] - lo[0]));
                cr = lo[1] + (hi[1] - lo[1]) * t;
                cg = lo[2] + (hi[2] - lo[2]) * t;
                cb = lo[3] + (hi[3] - lo[3]) * t;
                break;
            }
        }
        // Doppler patches: a darker one about one pixel in eight, a brighter one about one in six.
        float n = 0.65f * noise(x, y, cell, 7) + 0.35f * noise(x, y, cell / 2f, 11);
        float k2 = n < 0.3f ? 0.62f : n > 0.68f ? 1.14f : 1f;
        return rgb(cr * k2, cg * k2, cb * k2);
    }

    private static float hue(float r, float g, float b, float max, float min) {
        float d = max - min;
        if (d <= 0f) {
            return 0f;
        }
        float h = max == r ? (g - b) / d : max == g ? 2f + (b - r) / d : 4f + (r - g) / d;
        h /= 6f;
        return h < 0f ? h + 1f : h;
    }

    private static int rgb(float r, float g, float b) {
        return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /** Smooth value noise in [0, 1], the same for the same pixel and seed. */
    static float noise(float x, float y, float cell, int seed) {
        float gx = x / cell, gy = y / cell;
        int x0 = (int) Math.floor(gx), y0 = (int) Math.floor(gy);
        float fx = gx - x0, fy = gy - y0;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float a = hash(x0, y0, seed), b = hash(x0 + 1, y0, seed), c = hash(x0, y0 + 1, seed), d = hash(x0 + 1, y0 + 1, seed);
        return (a * (1f - fx) + b * fx) * (1f - fy) + (c * (1f - fx) + d * fx) * fy;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 0x85EBCA77;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }
}
