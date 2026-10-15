package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Pictures from files (screenshots) at a fixed size, for galleries: read and scaled on Sol's threads (the middle of
 * the picture cropped to the size's shape), uploaded on the render thread into a few reused textures; the least
 * recently drawn gives its texture to the next. Render thread only.
 */
public final class FileThumbs {
    private final String name;
    private final int w, h, slots;
    private final Map<Path, Entry> entries = new HashMap<>();
    private final SolTexture[] textures;
    private final boolean[] taken;

    private static final class Entry {
        volatile int[] px;
        volatile boolean failed;
        int slot = -1;
        long used;
    }

    public FileThumbs(String name, int w, int h, int slots) {
        this.name = name;
        this.w = w;
        this.h = h;
        this.slots = slots;
        this.textures = new SolTexture[slots];
        this.taken = new boolean[slots];
    }

    /** The picture's texture once read, else null (it starts reading). */
    public SolTexture get(Path file) {
        Entry e = entries.get(file);
        if (e == null) {
            if (entries.size() >= slots * 2) {
                evict();
            }
            Entry fresh = new Entry();
            e = fresh;
            entries.put(file, e);
            SolExecutors.cpu(Feature.MENU, () -> {
                int[] px = read(file, w, h);
                if (px == null) {
                    fresh.failed = true;
                } else {
                    fresh.px = px;
                }
            });
        }
        e.used = System.nanoTime();
        if (e.px != null && e.slot < 0) {
            int slot = freeSlot();
            if (slot >= 0) {
                e.slot = slot;
                taken[slot] = true;
                if (textures[slot] == null || textures[slot].released()) {
                    textures[slot] = new SolTexture(name + "_" + slot, w, h, true);
                }
                textures[slot].upload(e.px);
            }
        }
        return e.slot >= 0 ? textures[e.slot] : null;
    }

    public boolean failed(Path file) {
        Entry e = entries.get(file);
        return e != null && e.failed;
    }

    /** Forgets a file (deleted): its texture goes back to the pool. */
    public void forget(Path file) {
        Entry e = entries.remove(file);
        if (e != null && e.slot >= 0) {
            taken[e.slot] = false;
        }
    }

    private int freeSlot() {
        for (int i = 0; i < slots; i++) {
            if (!taken[i]) {
                return i;
            }
        }
        // All in use: take the least recently drawn one's.
        Map.Entry<Path, Entry> oldest = null;
        for (Map.Entry<Path, Entry> en : entries.entrySet()) {
            if (en.getValue().slot >= 0 && (oldest == null || en.getValue().used < oldest.getValue().used)) {
                oldest = en;
            }
        }
        if (oldest == null) {
            return -1;
        }
        int s = oldest.getValue().slot;
        oldest.getValue().slot = -1;
        if (oldest.getValue().px == null) {
            entries.remove(oldest.getKey());
        }
        taken[s] = false;
        return s;
    }

    private void evict() {
        Map.Entry<Path, Entry> oldest = null;
        for (Map.Entry<Path, Entry> en : entries.entrySet()) {
            if (oldest == null || en.getValue().used < oldest.getValue().used) {
                oldest = en;
            }
        }
        if (oldest != null) {
            forget(oldest.getKey());
        }
    }

    public void release() {
        for (SolTexture t : textures) {
            if (t != null) {
                t.release();
            }
        }
        java.util.Arrays.fill(textures, null);
        java.util.Arrays.fill(taken, false);
        entries.clear();
    }

    /** A picture file scaled to w x h (its middle, cropped to that shape), averaged; null if it can't be read. */
    static int[] read(Path file, int w, int h) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 64_000_000L) {
                return null;
            }
            BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
            if (img == null) {
                return null;
            }
            int sw = img.getWidth(), sh = img.getHeight();
            float want = (float) w / h;
            int cw = sw, ch = sh;
            if ((float) sw / sh > want) {
                cw = Math.round(sh * want);
            } else {
                ch = Math.round(sw / want);
            }
            int ox = (sw - cw) / 2, oy = (sh - ch) / 2;
            int[] src = img.getRGB(ox, oy, cw, ch, null, 0, cw);
            int[] out = new int[w * h];
            for (int y = 0; y < h; y++) {
                int y0 = y * ch / h, y1 = Math.max(y0 + 1, (y + 1) * ch / h);
                for (int x = 0; x < w; x++) {
                    int x0 = x * cw / w, x1 = Math.max(x0 + 1, (x + 1) * cw / w);
                    long r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int yy = y0; yy < y1; yy += Math.max(1, (y1 - y0) / 4)) {
                        for (int xx = x0; xx < x1; xx += Math.max(1, (x1 - x0) / 4)) {
                            int c = src[yy * cw + xx];
                            r += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            b += c & 0xFF;
                            n++;
                        }
                    }
                    out[y * w + x] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
                }
            }
            return out;
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
}
