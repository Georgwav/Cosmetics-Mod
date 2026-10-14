package io.github.georgwav.cosmetics.sol.util;

/**
 * The prototypes' seeded random numbers ({@code mulberry}), bit for bit, so stars, particles, rune sparks and
 * galaxy dots land where they do in the design.
 */
public final class Mulberry32 {
    private int state;

    public Mulberry32(int seed) {
        this.state = seed;
    }

    /** The next number in [0, 1). */
    public double next() {
        state += 0x6D2B79F5;
        int a = state;
        int t = (a ^ (a >>> 15)) * (1 | a);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }

    public float nextFloat() {
        return (float) next();
    }

    /** A whole number in [0, bound). */
    public int nextInt(int bound) {
        return (int) Math.floor(next() * bound);
    }
}
