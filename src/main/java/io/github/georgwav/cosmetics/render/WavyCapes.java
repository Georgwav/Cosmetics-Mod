package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.model.Cloth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wavy capes (a display setting, like the Wavey Capes mod): a cape's points down its length follow the top's swing
 * on springs, so when you start, stop, jump or land the bottom lags behind and the cape ripples through. Each player's
 * cape has its own chain of {@link Cloth#SAMPLES} points, stepped with the game's clock; the result is how far
 * each point lags behind the top, which bends the cape's cloth shape on top of the usual cape sway.
 *
 * <p>Plain maths, no Minecraft types, so it is unit-tested. Render thread only.
 */
public final class WavyCapes {
    static final int POINTS = Cloth.SAMPLES;
    /** Spring towards the point above, per second squared, and its damping per second. */
    static final float STIFFNESS = 230f;
    static final float DAMPING = 15f;
    /** Most a point lags behind the top either way, radians. */
    static final float MAX_LAG = (float) Math.toRadians(70);
    /** Longest step simulated at once, seconds (more is cut, as after a pause). */
    private static final double MAX_STEP = 0.1;
    /** Sub-step size, seconds, for a stable spring chain. */
    private static final double SUB_STEP = 1.0 / 240.0;

    /** One cape's points: each one's angle and how fast it turns. */
    static final class Chain {
        final float[] angle = new float[POINTS];
        final float[] speed = new float[POINTS];
        final float[] lag = new float[POINTS];
        double last = Double.NaN;
    }

    private static final Map<UUID, Chain> CHAINS = new HashMap<>();
    private static final UUID NOBODY = new UUID(0L, 0L);
    private static double lastSweep;

    private WavyCapes() {
    }

    /**
     * Steps {@code player}'s cape to {@code seconds} with the top swung to {@code top} radians, and returns how far
     * each point down the cape lags behind it (radians; positive further back). The array is reused per player.
     */
    public static float[] lag(UUID player, float top, double seconds) {
        Chain chain = CHAINS.computeIfAbsent(player != null ? player : NOBODY, k -> new Chain());
        step(chain, top, seconds);
        if (seconds - lastSweep > 30.0) {
            lastSweep = seconds;
            CHAINS.values().removeIf(c -> seconds - c.last > 60.0);
        }
        return chain.lag;
    }

    /** Forgets every cape (a world change). */
    public static void clear() {
        CHAINS.clear();
    }

    static float[] step(Chain chain, float top, double seconds) {
        if (Double.isNaN(chain.last) || seconds < chain.last) {
            java.util.Arrays.fill(chain.angle, top);
            java.util.Arrays.fill(chain.speed, 0f);
            java.util.Arrays.fill(chain.lag, 0f);
            chain.last = seconds;
            return chain.lag;
        }
        double dt = Math.min(MAX_STEP, seconds - chain.last);
        chain.last = seconds;
        int steps = (int) Math.ceil(dt / SUB_STEP);
        float h = steps > 0 ? (float) (dt / steps) : 0f;
        float[] a = chain.angle;
        float[] v = chain.speed;
        for (int s = 0; s < steps; s++) {
            a[0] = top;
            for (int i = 1; i < POINTS; i++) {
                float accel = STIFFNESS * (a[i - 1] - a[i]) - DAMPING * v[i];
                v[i] += accel * h;
                a[i] += v[i] * h;
            }
        }
        a[0] = top;
        for (int i = 0; i < POINTS; i++) {
            chain.lag[i] = Math.max(-MAX_LAG, Math.min(MAX_LAG, a[i] - top));
        }
        return chain.lag;
    }
}
