package io.github.georgwav.cosmetics.sol.music;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The playing song's cover: fetched once per album (IO thread), decoded and scaled on the CPU thread (a 128 px
 * picture for the menu, a 32 px one for the HUD, both with rounded corners, and the 48 x 48 sample for
 * {@link AlbumTheme}), then uploaded by the render thread on its next frame.
 */
public final class Covers {
    public static final int LARGE = 128, SMALL = 32, SAMPLE = 48;

    /** A decoded cover. {@code key} is its address. */
    /** {@code sun}: the 16 x 16 album sun the sky draws in place of the plasma (a visualizer). */
    public record Cover(String key, int[] large, int[] small, int[] sun, AlbumTheme.Palette palette) {
    }

    private static volatile String wanted;
    private static volatile Cover ready;
    private static Cover uploaded;
    private static SolTexture large, small;

    private Covers() {
    }

    /** The cover for this address (null: none); a new address starts loading it. Any thread. */
    static void want(String url) {
        if (url == null || url.isBlank()) {
            wanted = null;
            ready = null;
            return;
        }
        if (url.equals(wanted)) {
            return;
        }
        wanted = url;
        SolExecutors.IO.execute(() -> {
            byte[] bytes = fetch(url);
            if (bytes == null || !url.equals(wanted)) {
                return;
            }
            SolExecutors.cpu(Feature.MUSIC, () -> {
                Cover c = decode(url, bytes);
                if (c != null && url.equals(wanted)) {
                    ready = c;
                }
            });
        });
    }

    static void clear() {
        wanted = null;
        ready = null;
    }

    private static byte[] fetch(String url) {
        try {
            if (url.startsWith("file:")) {
                Path p = Path.of(URI.create(url));
                return Files.size(p) < 8_000_000L ? Files.readAllBytes(p) : null;
            }
            return url.startsWith("http://") || url.startsWith("https://") ? Http.bytes(url) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static Cover decode(String key, byte[] bytes) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
                return null;
            }
            int w = img.getWidth(), h = img.getHeight();
            int[] src = img.getRGB(0, 0, w, h, null, 0, w);
            return fromPixels(key, src, w, h);
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.debug("[Sol] Couldn't read a cover", e);
            return null;
        }
    }

    /** A cover from straight ARGB pixels (pure; the square middle of a non-square picture). */
    static Cover fromPixels(String key, int[] src, int w, int h) {
        int side = Math.min(w, h), ox = (w - side) / 2, oy = (h - side) / 2;
        int[] sample = scale(src, w, ox, oy, side, SAMPLE);
        int[] big = round(scale(src, w, ox, oy, side, LARGE), LARGE, LARGE * 10f / 96f);
        int[] little = round(scale(src, w, ox, oy, side, SMALL), SMALL, SMALL * 4f / 26f);
        return new Cover(key, big, little, sun(scale(src, w, ox, oy, side, 16)), AlbumTheme.palette(sample));
    }

    /** The square (ox, oy, side) of a picture, averaged down (or up, nearest) to n x n, opaque. */
    static int[] scale(int[] src, int stride, int ox, int oy, int side, int n) {
        int[] out = new int[n * n];
        for (int y = 0; y < n; y++) {
            int y0 = oy + y * side / n, y1 = Math.max(y0 + 1, oy + (y + 1) * side / n);
            for (int x = 0; x < n; x++) {
                int x0 = ox + x * side / n, x1 = Math.max(x0 + 1, ox + (x + 1) * side / n);
                long r = 0, g = 0, b = 0;
                int count = 0;
                for (int sy = y0; sy < y1; sy++) {
                    int row = sy * stride;
                    for (int sx = x0; sx < x1; sx++) {
                        int c = src[row + sx];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                        count++;
                    }
                }
                out[y * n + x] = 0xFF000000 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
            }
        }
        return out;
    }

    /** The album sun: the cover at 16 x 16, lifted toward white (screened by a quarter) so it still reads as a sun. */
    static int[] sun(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            r = 255 - (255 - r) * 3 / 4;
            g = 255 - (255 - g) * 3 / 4;
            b = 255 - (255 - b) * 3 / 4;
            px[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        return px;
    }

    /** Rounds the corners (radius r pixels, smooth edge) by alpha. */
    static int[] round(int[] px, int n, float r) {
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float cx = Math.max(0f, Math.max(r - (x + 0.5f), (x + 0.5f) - (n - r)));
                float cy = Math.max(0f, Math.max(r - (y + 0.5f), (y + 0.5f) - (n - r)));
                if (cx <= 0f || cy <= 0f) {
                    continue;
                }
                float d = (float) Math.sqrt(cx * cx + cy * cy) - r;
                float a = Math.max(0f, Math.min(1f, 0.5f - d));
                int i = y * n + x;
                px[i] = ((int) (a * 255f + 0.5f) << 24) | (px[i] & 0xFFFFFF);
            }
        }
        return px;
    }

    /** The cover shown now (render thread): uploads a newly decoded one first. Null when there's none. */
    public static Cover current() {
        Cover c = ready;
        if (c != uploaded) {
            uploaded = c;
            if (c != null) {
                if (large == null || large.released()) {
                    large = new SolTexture("music_cover", LARGE, LARGE, true);
                    small = new SolTexture("music_cover_small", SMALL, SMALL, true);
                }
                large.upload(c.large());
                small.upload(c.small());
            }
        }
        return c;
    }

    /** The cover's textures (after {@link #current()} returned non-null). */
    public static SolTexture large() {
        return large;
    }

    public static SolTexture small() {
        return small;
    }
}
