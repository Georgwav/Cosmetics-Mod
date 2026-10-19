package io.github.georgwav.cosmetics.melee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeTimingTest {
    private static final float TICK = 0.05f;
    private static final float SWING = KnifeTiming.swing(0, 0);

    @Test
    void cooldownAndSwingFollowMinecraft() {
        assertEquals(0.625f, KnifeTiming.cooldown(1.6), 1e-6f);
        assertEquals(0.3f, SWING, 1e-6f);
        assertEquals(0.25f, KnifeTiming.swing(1, 0), 1e-6f, "Haste I");
        assertEquals(0.4f, KnifeTiming.swing(0, 1), 1e-6f, "Mining Fatigue I");
    }

    @Test
    void attacksLandTheirImpactWithinHalfASwingAndSettleAtTheCooldown() {
        // The packs' light attacks: impact and settle (seconds).
        float[][] attacks = {{0.18f, 1.25f}, {0.30f, 1.55f}, {0.05f, 0.98f}, {0.23f, 1.50f}, {0.60f, 1.87f}};
        for (double speed : new double[]{1.6, 2.0, 1.0, 4.0}) {
            float cd = KnifeTiming.cooldown(speed);
            for (float[] a : attacks) {
                KnifeTiming.Warp w = new KnifeTiming.Warp();
                KnifeTiming.attack(w, a[0], a[1], cd, SWING);
                float impactAt = w.real(a[0]);
                // Up to 3× faster: the impact within half a swing, unless even 3× can't reach that.
                assertTrue(impactAt <= Math.max(SWING / 2f, a[0] / 3f) + 1e-4f, speed + " " + a[0] + ": impact at " + impactAt);
                assertTrue(impactAt <= a[0] + 1e-4f, "never slower than the pack");
                // At rest by the cooldown (one tick either side), the pack's tail dropped.
                assertTrue(w.end <= cd + TICK, speed + ": done at " + w.end + " for a cooldown of " + cd);
                if (w.weight(w.end - 1e-4f) > 0.99f) {
                    // Not eased out: it really settled, at its own pace within 0.75 to 3×.
                    assertEquals(a[1], w.clip(w.end), 1e-3f);
                }
            }
        }
    }

    @Test
    void anAttackThatFitsSettlesExactlyAtTheCooldown() {
        KnifeTiming.Warp w = new KnifeTiming.Warp();
        float cd = KnifeTiming.cooldown(1.6);
        KnifeTiming.attack(w, 0.15f, 1.0f, cd, SWING);
        assertEquals(cd, w.end, TICK);
        assertEquals(1.0f, w.clip(w.end), 1e-3f);
        assertEquals(0.15f, w.real(0.15f), 1e-4f, "an impact already within half a swing stays where it is");
    }

    @Test
    void drawsComeToRestWhenTheChargeIsFull() {
        float cd = KnifeTiming.cooldown(1.6);
        KnifeTiming.Warp w = new KnifeTiming.Warp();
        KnifeTiming.draw(w, 0.92f, cd);
        assertEquals(cd, w.end, 1e-4f);
        assertEquals(0.92f, w.clip(cd), 1e-3f);
        // Creed's 1.85 s draw: 2.5× still runs long, so it eases into idle by the cooldown.
        KnifeTiming.draw(w, 1.85f, cd);
        assertEquals(cd, w.end, 1e-4f);
        assertEquals(0f, w.weight(cd), 1e-6f);
        // A draw shorter than the cooldown plays at the pack's speed (never slower).
        KnifeTiming.draw(w, 0.4f, cd);
        assertEquals(0.4f, w.end, 1e-4f);
    }

    @Test
    void soundsFollowTheWarpedTime() {
        KnifeTiming.Warp w = new KnifeTiming.Warp();
        KnifeTiming.attack(w, 0.3f, 1.2f, 0.625f, SWING);
        // The impact sound (at 0.3 s in the pack) plays at the warped impact, half a swing after the click.
        assertEquals(0.15f, w.real(0.3f), 1e-4f);
        assertEquals(0.3f, w.clip(w.real(0.3f)), 1e-4f);
        KnifeTiming.original(w, 1.2f);
        assertEquals(0.3f, w.real(0.3f), 1e-6f);
    }
}
