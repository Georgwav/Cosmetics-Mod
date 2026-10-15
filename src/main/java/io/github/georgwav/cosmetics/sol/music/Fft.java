package io.github.georgwav.cosmetics.sol.music;

/**
 * The visualizers' ears (pure; unit tested): a 1024-point FFT of the newest samples (Hann window) about 60 times a
 * second, folded into 24 bands spaced evenly in pitch from 40 Hz to 16 kHz, kept in range by a slow automatic gain;
 * the level (RMS); and beats: the bass jumping over 1.22 times its recent average, above 0.42, at least 260 ms after
 * the last one. Not thread safe: one analyser per capture thread.
 */
public final class Fft {
    public static final int N = 1024, BANDS = 24;
    public static final float LOW_HZ = 40f, HIGH_HZ = 16000f;

    private final int rate;
    private final float[] window = new float[N], re = new float[N], im = new float[N];
    private final int[] bandLo = new int[BANDS], bandHi = new int[BANDS];
    private final float[] bands = new float[BANDS];
    private float peak = 1e-3f, levelPeak = 1e-3f, bassAvg;
    private float level, bass, high;
    private long lastBeatMs = Long.MIN_VALUE / 2;
    private boolean beat;

    public Fft(int sampleRate) {
        this.rate = sampleRate;
        for (int i = 0; i < N; i++) {
            window[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (N - 1));
        }
        float binHz = (float) sampleRate / N;
        for (int b = 0; b < BANDS; b++) {
            double lo = LOW_HZ * Math.pow(HIGH_HZ / LOW_HZ, b / (double) BANDS);
            double hi = LOW_HZ * Math.pow(HIGH_HZ / LOW_HZ, (b + 1) / (double) BANDS);
            bandLo[b] = Math.max(1, (int) Math.floor(lo / binHz));
            bandHi[b] = Math.min(N / 2, Math.max(bandLo[b] + 1, (int) Math.ceil(hi / binHz)));
        }
    }

    /** The band holding {@code hz}, or -1 outside 40 Hz to 16 kHz. */
    public int bandOf(float hz) {
        int bin = Math.round(hz * N / rate);
        for (int b = 0; b < BANDS; b++) {
            if (bin >= bandLo[b] && bin < bandHi[b]) {
                return b;
            }
        }
        return -1;
    }

    /**
     * Analyses the newest {@link #N} samples of a ring buffer ({@code ring}, newest just before {@code end}), each
     * -1..1, at {@code nowMs}.
     */
    public void analyse(float[] ring, int end, long nowMs) {
        int len = ring.length;
        double sq = 0;
        for (int i = 0; i < N; i++) {
            float s = ring[Math.floorMod(end - N + i, len)];
            sq += s * s;
            re[i] = s * window[i];
            im[i] = 0f;
        }
        transform(re, im);
        float max = 0f;
        for (int b = 0; b < BANDS; b++) {
            float m = 0f;
            for (int k = bandLo[b]; k < bandHi[b]; k++) {
                m = Math.max(m, (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]));
            }
            // Magnitude to a gentle loudness curve: quiet bands still move.
            float v = (float) Math.sqrt(m / (N / 4f));
            bands[b] = v;
            max = Math.max(max, v);
        }
        // Slow automatic gain: falls about 1% a frame, jumps up at once.
        peak = Math.max(max, Math.max(0.02f, peak * 0.99f));
        for (int b = 0; b < BANDS; b++) {
            bands[b] = Math.min(1f, bands[b] / peak);
        }
        float rms = (float) Math.sqrt(sq / N);
        levelPeak = Math.max(rms, Math.max(0.01f, levelPeak * 0.995f));
        level = Math.min(1f, rms / levelPeak);
        bass = (bands[0] + bands[1] + bands[2]) / 3f;
        float h = 0f;
        for (int b = BANDS - 8; b < BANDS; b++) {
            h += bands[b];
        }
        high = h / 8f;
        beat = bass > 1.22f * bassAvg && bass > 0.42f && nowMs - lastBeatMs >= 260L;
        if (beat) {
            lastBeatMs = nowMs;
        }
        bassAvg = bassAvg == 0f ? bass : bassAvg * 0.95f + bass * 0.05f;
    }

    /** In-place radix-2 FFT (N a power of two). */
    static void transform(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            double ang = -2 * Math.PI / size;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int start = 0; start < n; start += size) {
                float cr = 1f, ci = 0f;
                for (int k = 0; k < size / 2; k++) {
                    int a = start + k, b = a + size / 2;
                    float tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                    float ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }

    /** A copy of the latest result. */
    public Spectrum snapshot(long nowNanos, Spectrum before) {
        return new Spectrum(bands.clone(), level, bass, high, beat ? nowNanos : before == null ? 0L : before.beatNanos(), nowNanos);
    }

    public float[] bands() {
        return bands;
    }

    public boolean beat() {
        return beat;
    }

    public float level() {
        return level;
    }
}
