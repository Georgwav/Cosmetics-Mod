package io.github.georgwav.cosmetics.sol.music;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;

/**
 * What the computer is playing, for the visualizers: only while one is on screen (the render thread asks every
 * frame; three seconds without asking stops it). Linux records the default output's monitor with {@code pw-record}
 * or {@code parec}; Windows uses WASAPI loopback ({@link WasapiCapture}, through the JNA the game ships). One thread
 * reads, runs {@link Fft} every 735 samples (60 a second at 44.1 kHz) and publishes a {@link Spectrum}. Without a
 * way to listen, the visualizers keep their calm idle motion and the Music page says why.
 */
public final class AudioCapture {
    /** Where the samples come from: a blocking read of mono samples -1..1. */
    interface Input extends AutoCloseable {
        int rate();

        /** Fills {@code out} from the start; the count read, or -1 at the end. */
        int read(float[] out) throws IOException;

        @Override
        void close();
    }

    private static final boolean FAKE = Boolean.getBoolean("cosmeticsmod.fakeSpectrum");
    private static volatile Spectrum latest = Spectrum.SILENT;
    private static volatile long wantedUntil;
    private static volatile String state = "off";
    private static Thread thread;

    private AudioCapture() {
    }

    /** The newest spectrum (silent while off). */
    public static Spectrum latest() {
        return latest;
    }

    /** "off", "listening", or why it can't listen ("none"). */
    public static String state() {
        return state;
    }

    /** A visualizer is on screen (render thread, each frame): starts listening, or keeps it going three more seconds. */
    public static synchronized void want() {
        wantedUntil = System.nanoTime() + 3_000_000_000L;
        if (thread == null || !thread.isAlive()) {
            if ("none".equals(state)) {
                return;
            }
            thread = new Thread(AudioCapture::run, "Sol audio");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static void run() {
        Input in = FAKE ? new Synth() : open();
        if (in == null) {
            state = "none";
            CosmeticsMod.LOGGER.info("[Sol] Visualizers: no way to listen to the computer's sound here");
            return;
        }
        state = "listening";
        Fft fft = new Fft(in.rate());
        float[] ring = new float[Fft.N * 2];
        int end = 0, sinceHop = 0, hop = Math.max(256, in.rate() / 60);
        float[] chunk = new float[512];
        Spectrum s = Spectrum.SILENT;
        try (in) {
            while (System.nanoTime() < wantedUntil) {
                int n = in.read(chunk);
                if (n < 0) {
                    break;
                }
                for (int i = 0; i < n; i++) {
                    ring[end] = chunk[i];
                    end = (end + 1) % ring.length;
                }
                sinceHop += n;
                if (sinceHop >= hop) {
                    sinceHop = 0;
                    long now = System.nanoTime();
                    fft.analyse(ring, end, now / 1_000_000L);
                    s = fft.snapshot(now, s);
                    latest = s;
                }
            }
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.debug("[Sol] Audio capture stopped", e);
        }
        latest = Spectrum.SILENT;
        if (!"none".equals(state)) {
            state = "off";
        }
    }

    private static Input open() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return WasapiCapture.open();
        }
        if (!os.contains("linux")) {
            return null;
        }
        for (List<String> cmd : List.of(
                List.of("pw-record", "--format=f32", "--rate=44100", "--channels=1", "-P", "{ stream.capture.sink=true }", "-"),
                List.of("parec", "--format=float32le", "--rate=44100", "--channels=1", "--latency-msec=20", "-d", "@DEFAULT_MONITOR@"))) {
            Input in = Pipe.start(cmd);
            if (in != null) {
                return in;
            }
        }
        return null;
    }

    /** A recorder process writing 32-bit float mono at 44.1 kHz to its output. */
    static final class Pipe implements Input {
        private final Process process;
        private final InputStream out;
        private final byte[] bytes = new byte[512 * 4];

        private Pipe(Process process) {
            this.process = process;
            this.out = process.getInputStream();
        }

        static Pipe start(List<String> cmd) {
            try {
                Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                // A recorder that can't reach the sound server quits at once.
                Thread.sleep(300L);
                if (!p.isAlive()) {
                    return null;
                }
                return new Pipe(p);
            } catch (IOException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        @Override
        public int rate() {
            return 44100;
        }

        @Override
        public int read(float[] dst) throws IOException {
            int want = Math.min(dst.length, bytes.length / 4) * 4, got = 0;
            while (got < want) {
                int n = out.read(bytes, got, want - got);
                if (n < 0) {
                    return got >= 4 ? toFloats(got / 4, dst) : -1;
                }
                got += n;
            }
            return toFloats(want / 4, dst);
        }

        private int toFloats(int count, float[] dst) {
            ByteBuffer.wrap(bytes, 0, count * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(dst, 0, count);
            return count;
        }

        @Override
        public void close() {
            process.destroy();
        }
    }

    /**
     * A made-up song for CI, where nothing plays (-Dcosmeticsmod.fakeSpectrum): a kick at 120 BPM, a chord and a
     * hi-hat, in real time, through the same analyser.
     */
    static final class Synth implements Input {
        private long n;
        private final long start = System.nanoTime();
        private long seed = 42L;

        @Override
        public int rate() {
            return 44100;
        }

        @Override
        public int read(float[] dst) {
            // Keep to real time.
            long due = start + n * 1_000_000_000L / 44100L;
            long wait = due - System.nanoTime();
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
            for (int i = 0; i < dst.length; i++, n++) {
                double t = n / 44100.0, beat = t % 0.5;
                double kick = Math.sin(2 * Math.PI * (55 + 60 * Math.exp(-beat * 30)) * beat) * Math.exp(-beat * 9);
                double chord = 0.12 * (Math.sin(2 * Math.PI * 220 * t) + Math.sin(2 * Math.PI * 277.2 * t) + Math.sin(2 * Math.PI * 329.6 * t));
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                double hat = (t % 0.25 < 0.03 ? 0.15 : 0.0) * (((seed >>> 33) & 0xFFFF) / 32768.0 - 1.0);
                dst[i] = (float) (0.7 * kick + chord + hat);
            }
            return dst.length;
        }

        @Override
        public void close() {
        }
    }
}
