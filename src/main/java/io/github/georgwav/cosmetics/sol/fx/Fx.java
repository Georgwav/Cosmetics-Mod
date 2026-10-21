package io.github.georgwav.cosmetics.sol.fx;

/**
 * One running effect, from {@link FxPool}'s pool: reused, never allocated while playing. Its kind draws it from these
 * fields and its age alone, so an effect needs no per-frame state.
 */
public final class Fx {
    public FxKind kind;
    /** When it started and when it ends (the pool's clock, seconds); it may be told to end early. */
    public float start, end;
    /** Its length as started (seconds), and its own length at 100% (the timeline its kind is written on). */
    public float life, base;
    /** Where it plays, the way it faces (degrees), a seed for its random parts. */
    public double x, y, z;
    public float yaw;
    public int seed;
    /** Its ramp (core, main, shade, deep), a second ramp (two colours), and the glow colour. */
    public final int[] ramp = new int[Ramp.TONES];
    public final int[] ramp2 = new int[Ramp.TONES];
    /** A few more colours (the confetti's five). */
    public final int[] palette = new int[5];
    public int glowColour;
    /** The standard settings as it started: size, amount and speed (1 = 100%), glow (0 off, 1 soft, 2 strong). */
    public float size = 1f, amount = 1f, speed = 1f;
    public int glow = 1;
    public boolean snap = true, rainbow, two;
    /** Calm motion: it holds its key moment for its short life instead of playing through. */
    public boolean calm;
    /** The style picked (the kind's own numbering), and free numbers a kind may use. */
    public int style;
    public float a, b, c, d;
    public int i0, i1;
    public double tx, ty, tz;
    /** A copy of a body it shows, a text it writes, an entity it follows. */
    public PoseSnapshot copy;
    public String text;
    public int entity;
    boolean live;

    void reset() {
        kind = null;
        copy = null;
        text = null;
        size = amount = speed = 1f;
        glow = 1;
        snap = true;
        rainbow = two = calm = false;
        style = 0;
        a = b = c = d = 0f;
        i0 = i1 = 0;
        tx = ty = tz = 0;
        entity = 0;
        yaw = 0f;
        base = 0f;
    }

    /**
     * Its age on its own timeline: seconds as they'd be at 100% speed and duration, so a kind is written once and
     * plays slower, faster, shorter or longer as set.
     */
    public float time(float age) {
        if (calm && base > 0f) {
            return base * 0.4f;
        }
        return life <= 0f || base <= 0f ? age : age * base / life;
    }

    /** 0..1 over its life. */
    public float progress(float now) {
        return life <= 0f ? 1f : Math.min(1f, Math.max(0f, (now - start) / life));
    }

    /** How much is left of an early end (1 normally, falling to 0 over the last quarter second when told to end). */
    public float early(float now) {
        float planned = start + life;
        if (end >= planned) {
            return 1f;
        }
        return Ramp.steps(Math.max(0f, (end - now) / 0.25f), 3, snap);
    }

    /** The colour of tone {@code tone} for part {@code i} (two colours alternate parts; rainbow turns with time). */
    public int tone(int tone, int i, float now) {
        if (rainbow) {
            float hue = ((seed & 0xFF) / 256f + i * 0.07f + now * 0.25f) % 1f;
            // Twelve hues, so the rainbow stays in steps.
            hue = Math.round(hue * 12f) / 12f;
            int[] r = FxColour.rainbowRamp(hue);
            return r[tone];
        }
        return two && (i & 1) == 1 ? ramp2[tone] : ramp[tone];
    }
}
