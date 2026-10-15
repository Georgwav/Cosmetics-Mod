package io.github.georgwav.cosmetics.sol.music;

/**
 * What's playing, as one source last told it (immutable; published as a snapshot). The position is where playback
 * was at {@code atNanos} (System.nanoTime); {@link #position()} carries it forward while playing.
 *
 * @param key        the song, for caches (the source's id, or title and artist)
 * @param catalogId  the Apple Music catalog id (Cider), for its lyrics; null elsewhere
 * @param artworkUrl the cover's address, sized ({@code {w}x{h}} filled in), or null
 */
public record NowPlaying(String source, String key, String title, String artist, String album, long durationMs,
                         long positionMs, long atNanos, boolean playing, String artworkUrl, String catalogId) {

    /** The playback position now, in ms (carried forward from the last report while playing). */
    public long position() {
        return position(System.nanoTime());
    }

    public long position(long nowNanos) {
        long p = playing ? positionMs + (nowNanos - atNanos) / 1_000_000L : positionMs;
        return durationMs > 0 ? Math.max(0L, Math.min(durationMs, p)) : Math.max(0L, p);
    }

    /** 0..1 through the song. */
    public float progress() {
        return durationMs <= 0 ? 0f : (float) position() / durationMs;
    }

    public NowPlaying withPosition(long positionMs, boolean playing) {
        return new NowPlaying(source, key, title, artist, album, durationMs, positionMs, System.nanoTime(), playing, artworkUrl, catalogId);
    }

    /** Whether it's the same song as {@code other}. */
    public boolean sameSong(NowPlaying other) {
        return other != null && key.equals(other.key);
    }
}
