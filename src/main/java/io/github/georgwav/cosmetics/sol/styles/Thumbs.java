package io.github.georgwav.cosmetics.sol.styles;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The Styles tiles' pictures (B.2.8): made only when a tile is on screen, decoded off the render thread to a 64-pixel
 * picture (a block as a little cube), the last 256 kept. Opening the menu decodes nothing that isn't shown. Render
 * thread for {@link #get}; the decoding runs on Sol's IO threads.
 */
public final class Thumbs {
    public static final int SIZE = 64;
    private static final int KEEP = 256;
    /** At most this many pictures go to the GPU in one frame. */
    private static final int UPLOADS_PER_FRAME = 4;

    /** A tile's picture: its texture, its size in picture pixels and its average hue (for sorting by colour). */
    public record Thumb(SolTexture texture, int w, int h, float hue, Pixels.Image image) {
    }

    private record Ready(String key, Pixels.Image image, float hue) {
    }

    private static final Map<String, Thumb> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static final Set<String> LOADING = new HashSet<>();
    private static final ConcurrentLinkedQueue<Ready> READY = new ConcurrentLinkedQueue<>();
    private static int uploadsThisFrame;
    private static long frame = -1;

    private Thumbs() {
    }

    /** How a picture is made: a 64-pixel tile, a block as a little cube, or the whole texture (every frame). */
    public enum Mode {
        TILE, CUBE, FULL
    }

    private static int made;

    /** The picture of a source's texture, or null while it's being made (it asks for it). */
    public static Thumb get(Mixer.Source source, Mode mode) {
        if (source == null) {
            return null;
        }
        String key = source.identity() + "#" + mode.name();
        drain();
        Thumb t = CACHE.get(key);
        if (t != null && !t.texture().released()) {
            return t;
        }
        if (LOADING.add(key)) {
            SolExecutors.IO.execute(() -> {
                Pixels.Image img = null;
                float hue = 2f;
                try {
                    // A made picture is kept on disk by its source's content (a pack file's CRC and size, a made
                    // file's recipe), so opening the tab again decodes nothing it has seen before.
                    java.nio.file.Path disk = mode == Mode.FULL ? null
                            : Styles.cacheFolder().resolve("thumbs").resolve(Mixer.sha1(key) + ".png");
                    if (disk != null && java.nio.file.Files.isRegularFile(disk)) {
                        img = Builder.decode(java.nio.file.Files.readAllBytes(disk));
                        hue = img == null ? 2f : Pixels.hue(img);
                    }
                    if (img == null) {
                        byte[] data = Styles.read(source);
                        Pixels.Image raw = data == null ? null : Builder.decode(data);
                        if (raw != null) {
                            Pixels.Image f = Pixels.firstFrame(raw);
                            img = switch (mode) {
                                case CUBE -> Pixels.isoCube(f);
                                case FULL -> raw;
                                case TILE -> Pixels.fit(f, SIZE);
                            };
                            hue = Pixels.hue(f);
                            if (disk != null) {
                                java.nio.file.Files.createDirectories(disk.getParent());
                                io.github.georgwav.cosmetics.sol.util.AtomicFiles.write(disk, Builder.encode(img));
                            }
                        }
                    }
                } catch (Exception | LinkageError e) {
                    img = null;
                }
                READY.add(new Ready(key, img, hue));
            });
        }
        return null;
    }

    /** The average hue of a source's picture once its tile is made (2 until then, and for grey ones). */
    public static float hue(Mixer.Source source) {
        if (source == null) {
            return 2f;
        }
        Thumb t = CACHE.get(source.identity() + "#" + Mode.TILE.name());
        return t == null ? 2f : t.hue();
    }

    /** Uploads a few finished pictures each frame, and forgets the oldest past 256. */
    private static void drain() {
        long now = System.nanoTime() / 16_000_000L;
        if (now != frame) {
            frame = now;
            uploadsThisFrame = 0;
        }
        Ready r;
        while (uploadsThisFrame < UPLOADS_PER_FRAME && (r = READY.poll()) != null) {
            LOADING.remove(r.key());
            if (r.image() == null) {
                continue;
            }
            Pixels.Image img = r.image();
            SolTexture tex = SolTexture.of("styles_thumb_" + made++, img.w(), img.h(), false, img.argb());
            CACHE.put(r.key(), new Thumb(tex, img.w(), img.h(), r.hue(), img));
            uploadsThisFrame++;
            if (CACHE.size() > KEEP) {
                var eldest = CACHE.entrySet().iterator().next();
                eldest.getValue().texture().release();
                CACHE.remove(eldest.getKey());
            }
        }
    }

    /** Frees every picture (the menu closed, or the packs changed). */
    public static void clear() {
        for (Thumb t : CACHE.values()) {
            t.texture().release();
        }
        CACHE.clear();
        LOADING.clear();
        READY.clear();
    }
}
