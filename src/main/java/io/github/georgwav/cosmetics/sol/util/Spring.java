package io.github.georgwav.cosmetics.sol.util;

/**
 * The one spring behind every Sol motion: a little overshoot, then it settles. The maths is the prototypes'
 * ({@code v += (target - x)·k·dt; v *= e^(-d·dt); x += v·dt}), stepped in slices of at most 1/240 s so it moves
 * the same at 30 and at 240 FPS. With reduced motion every spring snaps.
 */
public final class Spring {
    /** Stiffness and damping, as the prototypes tune them. */
    public record Preset(float stiffness, float damping) {
    }

    /** The full menu's sun gliding along the arc. */
    public static final Preset SUN_ARC = new Preset(95f, 11f);
    /** The quick menu's sun orbiting the dial. */
    public static final Preset QUICK_SUN = new Preset(120f, 12f);
    /** The eclipse closing and opening. */
    public static final Preset ECLIPSE = new Preset(150f, 15f);
    /** The corona streamer reaching for a petal. */
    public static final Preset RAY = new Preset(130f, 12f);
    /** Panels, cards, switches and hovers. */
    public static final Preset UI = new Preset(220f, 18f);
    /** Entrances: softer, more overshoot. */
    public static final Preset POP = new Preset(180f, 13f);

    private static final float SLICE = 1f / 240f;

    /** Global reduced-motion switch (Settings → Motion: Calm). */
    public static volatile boolean reducedMotion;

    private final Preset preset;
    private float value;
    private float velocity;
    private float target;

    public Spring(Preset preset, float start) {
        this.preset = preset;
        this.value = start;
        this.target = start;
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    public float velocity() {
        return velocity;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    /** Jumps straight to a value, at rest. */
    public void snap(float to) {
        value = to;
        target = to;
        velocity = 0f;
    }

    public boolean settled() {
        return Math.abs(target - value) < 1e-3f && Math.abs(velocity) < 1e-3f;
    }

    /** Advances by {@code dt} seconds and returns the value. */
    public float step(float dt) {
        if (reducedMotion) {
            value = target;
            velocity = 0f;
            return value;
        }
        dt = Math.min(dt, 0.1f);
        while (dt > 0f) {
            float h = Math.min(dt, SLICE);
            velocity += (target - value) * preset.stiffness * h;
            velocity *= (float) Math.exp(-preset.damping * h);
            value += velocity * h;
            dt -= h;
        }
        if (settled()) {
            value = target;
            velocity = 0f;
        }
        return value;
    }

    /**
     * Advances an angle in degrees towards its target the short way round (the prototypes' {@code qNorm}).
     */
    public float stepAngle(float dt) {
        float diff = normDegrees(target - value);
        target = value + diff;
        return step(dt);
    }

    /** An angle folded into [-180, 180). */
    public static float normDegrees(float a) {
        float x = ((a % 360f) + 540f) % 360f - 180f;
        return x;
    }
}
