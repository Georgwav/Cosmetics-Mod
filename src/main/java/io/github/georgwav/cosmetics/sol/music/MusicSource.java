package io.github.georgwav.cosmetics.sol.music;

/** Where music comes from (Cider, the system's player, Spotify): started and stopped by the Music module. */
public interface MusicSource {
    String id();

    /** Starts listening (on its own threads); reports through {@link MusicService#report}. */
    void start();

    void stop();

    /** Whether it's connected to its player now. */
    boolean connected();

    default void playPause() {
    }

    default void next() {
    }

    default void previous() {
    }

    /** Loves the song playing (Cider: Apple Music's rating, and into the library). */
    default void like() {
    }

    /** Lyrics only this source can give (Apple's, through Cider), or null. */
    default String appleLyricsTtml(NowPlaying song) {
        return null;
    }
}
