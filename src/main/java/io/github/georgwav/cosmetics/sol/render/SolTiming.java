package io.github.georgwav.cosmetics.sol.render;

import io.github.georgwav.cosmetics.render.RenderStats;

import java.util.Arrays;

/**
 * For the benchmark: the CPU time Sol spends in each part of a frame, summed over a phase, and the frame times for
 * percentiles. Off unless the game runs the benchmark: then each part costs one boolean check.
 */
public final class SolTiming {
    /**
     * The parts timed: the sky pass (inside it again on their own: the sun; the night sky's stars, Milky Way,
     * constellations and twilight; the planets and the moon; the theme's night extras, its nebulae, aurora and
     * cloud; the sky's events), particles, light pass, flare, Sol UI, HUD modules, the effects (FxWorld).
     */
    public enum Part { SKY, SUN, PARTICLES, LIGHT, FLARE, SOL_UI, HUD, NIGHT, PLANETS, EXTRAS, EVENTS, FX }

    public static final boolean ON = RenderStats.TIMING;
    private static final long[] NANOS = new long[Part.values().length];
    /** Frame times of the phase, seconds; enough for a few minutes at a few hundred FPS. */
    private static final float[] FRAMES = new float[1 << 16];
    private static int frames;

    private SolTiming() {
    }

    /** The start of a timed part: pass the result to {@link #end}. */
    public static long start() {
        return ON ? System.nanoTime() : 0L;
    }

    public static void end(Part part, long start) {
        if (ON) {
            NANOS[part.ordinal()] += System.nanoTime() - start;
        }
    }

    /** Once per frame, with the frame's length. */
    public static void frame(float seconds) {
        if (ON && seconds > 0f) {
            FRAMES[frames++ & (FRAMES.length - 1)] = seconds;
        }
    }

    /** Starts a phase from zero. */
    public static void reset() {
        Arrays.fill(NANOS, 0L);
        frames = 0;
    }

    public static int frames() {
        return Math.min(frames, FRAMES.length);
    }

    /** The part's CPU milliseconds per frame over the phase. */
    public static double msPerFrame(Part part) {
        int n = frames();
        return n == 0 ? 0.0 : NANOS[part.ordinal()] / 1.0e6 / n;
    }

    /** The frame time below which {@code p} (0..1) of the phase's frames fall, milliseconds. */
    public static double frameMs(double p) {
        int n = frames();
        if (n == 0) {
            return 0.0;
        }
        float[] sorted = Arrays.copyOf(FRAMES, n);
        Arrays.sort(sorted);
        return sorted[Math.min(n - 1, (int) Math.floor(p * n))] * 1000.0;
    }
}
