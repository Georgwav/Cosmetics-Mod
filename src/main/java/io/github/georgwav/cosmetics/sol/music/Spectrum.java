package io.github.georgwav.cosmetics.sol.music;

/**
 * What the music sounds like now (immutable; published by the capture thread): 24 bands 0..1 from low to high,
 * the level, the bass and the highs, and when the last beat was (System.nanoTime, 0 for none yet).
 */
public record Spectrum(float[] bands, float level, float bass, float high, long beatNanos, long atNanos) {
    public static final Spectrum SILENT = new Spectrum(new float[Fft.BANDS], 0f, 0f, 0f, 0L, 0L);
}
