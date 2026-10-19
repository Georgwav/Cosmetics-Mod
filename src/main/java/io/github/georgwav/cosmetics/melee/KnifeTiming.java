package io.github.georgwav.cosmetics.melee;

/**
 * Fits a knife's animations to Minecraft's sword ("Fit to Minecraft", the default). The packs were made for their own
 * games, so their animations take 0.4 to 2.7 s to settle, against a sword's 0.625 s.
 * <ul>
 *   <li>A draw plays [0, settle] over [0, the attack cooldown] from when the sword appears, so the knife comes to rest
 *       just as the attack charge is full; never slower than the pack, at most 2.5× faster. Still long at that, it
 *       eases into idle by the cooldown.</li>
 *   <li>An attack's strike, [0, impact], is sped up so the impact lands within half a swing (0.15 s) of the click: up
 *       to 3×, never slower. Its recovery, [impact, settle], then fits the rest of the cooldown, at 0.75 to 3×; longer
 *       than that, it eases into idle by the cooldown. What's after settle (the pack's idle tail) isn't played.</li>
 *   <li>"Original timing" plays the pack's own lengths.</li>
 * </ul>
 * Sounds and slash effects follow the warped time, since they're keyed on the animation's own time. Pure Java.
 */
public final class KnifeTiming {
    private KnifeTiming() {
    }

    /**
     * A warp from real time (seconds since the animation started) to the animation's time: two linear pieces, an end,
     * and maybe an ease into idle. Reused; nothing allocated.
     */
    public static final class Warp {
        /** Real time at the piece boundary, and the clip time there. */
        float tauMid, clipMid;
        /** Speed of each piece (clip seconds per real second). */
        float k1 = 1f, k2 = 1f;
        /** Real time the animation is done (it settled, or eased out). */
        public float end;
        /** The ease into idle, real times (fadeEnd 0 for none). */
        float fadeStart, fadeEnd;

        /** The animation's time at {@code tau} real seconds after it started. */
        public float clip(float tau) {
            if (tau <= tauMid) {
                return tau * k1;
            }
            return clipMid + (tau - tauMid) * k2;
        }

        /** Real time at which the animation reaches {@code clipTime} (for sounds), or a large number if it never does. */
        public float real(float clipTime) {
            if (clipTime <= clipMid) {
                return k1 > 0f ? clipTime / k1 : Float.MAX_VALUE;
            }
            return k2 > 0f ? tauMid + (clipTime - clipMid) / k2 : Float.MAX_VALUE;
        }

        /** How much of the animation shows at {@code tau} (1 until it eases into idle, then down to 0). */
        public float weight(float tau) {
            if (fadeEnd <= 0f || tau <= fadeStart) {
                return 1f;
            }
            if (tau >= fadeEnd) {
                return 0f;
            }
            float x = (tau - fadeStart) / (fadeEnd - fadeStart);
            return 1f - x * x * (3f - 2f * x);
        }

        void set(float tauMid, float clipMid, float k1, float k2, float end, float fadeStart, float fadeEnd) {
            this.tauMid = tauMid;
            this.clipMid = clipMid;
            this.k1 = k1;
            this.k2 = k2;
            this.end = end;
            this.fadeStart = fadeStart;
            this.fadeEnd = fadeEnd;
        }
    }

    /** The pack's own timing: plays its whole length. */
    public static void original(Warp w, float length) {
        w.set(length, length, 1f, 1f, length, 0f, 0f);
    }

    /** A draw: rest when the attack charge is full ({@code cooldown} seconds), at 1 to 2.5×. */
    public static void draw(Warp w, float settle, float cooldown) {
        settle = Math.max(settle, 1e-3f);
        float k = clamp(settle / Math.max(cooldown, 1e-3f), 1f, 2.5f);
        float done = settle / k;
        if (done <= cooldown + 1e-4f) {
            w.set(done, settle, k, k, done, 0f, 0f);
        } else {
            // Still too long at 2.5×: what's left eases into idle by the cooldown.
            w.set(cooldown, cooldown * k, k, k, cooldown, cooldown * 0.6f, cooldown);
        }
    }

    /**
     * An attack: the impact within half a swing ({@code swing} seconds) of the click at up to 3× (never slower), then
     * settled at the cooldown at 0.75 to 3×, or eased into idle by it.
     */
    public static void attack(Warp w, float impact, float settle, float cooldown, float swing) {
        impact = Math.max(0f, impact);
        settle = Math.max(settle, impact);
        float k1 = clamp(impact / Math.max(swing * 0.5f, 1e-3f), 1f, 3f);
        float tauImpact = impact / k1;
        float span = cooldown - tauImpact;
        float rest = settle - impact;
        if (rest <= 1e-4f || span <= 1e-4f) {
            // Nothing left to recover, or no time for it: done at the impact (or eased out by the cooldown).
            float end = Math.max(tauImpact, Math.min(cooldown, tauImpact + rest));
            w.set(tauImpact, impact, k1, k1, end, rest > 1e-4f ? tauImpact : 0f, rest > 1e-4f ? Math.max(cooldown, tauImpact + 0.05f) : 0f);
            return;
        }
        float k2 = clamp(rest / span, 0.75f, 3f);
        float done = tauImpact + rest / k2;
        if (done <= cooldown + 1e-4f) {
            w.set(tauImpact, impact, k1, k2, done, 0f, 0f);
        } else {
            w.set(tauImpact, impact, k1, k2, cooldown, cooldown - span * 0.35f, cooldown);
        }
    }

    /** Minecraft's attack cooldown in seconds for an attack speed (attacks a second): 20 / speed ticks. */
    public static float cooldown(double attackSpeed) {
        return (float) (1.0 / Math.max(0.05, attackSpeed));
    }

    /** Minecraft's arm swing in seconds: 6 ticks, shorter with Haste, longer with Mining Fatigue (levels from 1). */
    public static float swing(int haste, int fatigue) {
        int ticks = 6;
        if (haste > 0) {
            ticks = 6 - haste;
        } else if (fatigue > 0) {
            ticks = 6 + fatigue * 2;
        }
        return Math.max(1, ticks) / 20f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
