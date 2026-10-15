package io.github.georgwav.cosmetics.sol.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FftTest {
    private static final int RATE = 44100;

    private static float[] sine(float hz, int n, float amp) {
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            s[i] = amp * (float) Math.sin(2 * Math.PI * hz * i / RATE);
        }
        return s;
    }

    @Test
    void transformFindsTheBin() {
        float[] re = new float[64], im = new float[64];
        for (int i = 0; i < 64; i++) {
            re[i] = (float) Math.cos(2 * Math.PI * 5 * i / 64);
        }
        Fft.transform(re, im);
        int best = 0;
        for (int k = 1; k < 32; k++) {
            if (Math.hypot(re[k], im[k]) > Math.hypot(re[best], im[best])) {
                best = k;
            }
        }
        assertEquals(5, best);
        assertEquals(32f, Math.hypot(re[5], im[5]), 0.01f);
    }

    @Test
    void aSinePeaksInItsBand() {
        for (float hz : new float[]{100f, 1000f, 5000f}) {
            Fft fft = new Fft(RATE);
            fft.analyse(sine(hz, Fft.N, 0.5f), 0, 0L);
            float[] b = fft.bands();
            int peak = 0;
            for (int i = 1; i < Fft.BANDS; i++) {
                if (b[i] > b[peak]) {
                    peak = i;
                }
            }
            assertEquals(fft.bandOf(hz), peak, "a " + hz + " Hz sine");
            assertEquals(1f, b[peak], 1e-4f, "the gain brings the loudest band to 1");
        }
    }

    @Test
    void bandsCoverTheRangeInOrder() {
        Fft fft = new Fft(RATE);
        assertEquals(0, fft.bandOf(45f));
        assertEquals(Fft.BANDS - 1, fft.bandOf(15500f));
        assertEquals(-1, fft.bandOf(20f));
        int last = -1;
        for (float hz = 45f; hz < 15500f; hz *= 1.1f) {
            int b = fft.bandOf(hz);
            assertTrue(b >= last, "bands rise with pitch");
            last = b;
        }
    }

    @Test
    void kicksAreBeatsAndTheGapHolds() {
        Fft fft = new Fft(RATE);
        float[] ring = new float[Fft.N];
        int beats = 0;
        // 4 s at 60 analyses a second: a 60 Hz kick every 500 ms over a quiet 2 kHz tone.
        for (int frame = 0; frame < 240; frame++) {
            long ms = frame * 1000L / 60L;
            boolean kick = ms % 500L < 70L;
            for (int i = 0; i < Fft.N; i++) {
                double t = (frame * 735 + i) / (double) RATE;
                ring[i] = (float) ((kick ? 0.9 * Math.sin(2 * Math.PI * 60 * t) : 0.0) + 0.05 * Math.sin(2 * Math.PI * 2000 * t));
            }
            fft.analyse(ring, 0, ms);
            if (fft.beat()) {
                beats++;
                assertTrue(ms % 500L < 70L, "a beat only on a kick, at " + ms + " ms");
            }
        }
        // Eight kicks; the first sets the average, so at least six are heard, never twice per kick.
        assertTrue(beats >= 6 && beats <= 8, "beats: " + beats);
    }

    @Test
    void silenceIsQuietAndCalm() {
        Fft fft = new Fft(RATE);
        fft.analyse(new float[Fft.N], 0, 0L);
        assertFalse(fft.beat());
        for (float v : fft.bands()) {
            assertEquals(0f, v, 1e-6f);
        }
        assertEquals(0f, fft.level(), 1e-6f);
    }

    @Test
    void theSynthHasABeat() {
        AudioCapture.Synth synth = new AudioCapture.Synth();
        Fft fft = new Fft(synth.rate());
        float[] ring = new float[Fft.N * 2], chunk = new float[735];
        int end = 0, beats = 0;
        for (int frame = 0; frame < 120; frame++) {
            // The synth keeps to real time: 2 s in all.
            synth.read(chunk);
            for (float v : chunk) {
                ring[end] = v;
                end = (end + 1) % ring.length;
            }
            fft.analyse(ring, end, frame * 1000L / 60L);
            beats += fft.beat() ? 1 : 0;
        }
        assertTrue(beats >= 2, "beats in 2 s of the made-up song: " + beats);
    }
}
