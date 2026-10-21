package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.sol.fx.Fx;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hit Effects' particles (C.4): they come off the body toward you and never go under the ground; hearts and orbs float
 * up, the rest fall and rest on the ground.
 */
class HitEffectsTest {
    @Test
    void particlesComeOffFallAndRestOnTheGround() {
        float[] p = new float[3], q = new float[3];
        for (int style = 0; style < HitEffects.PARTICLE_STYLES.size(); style++) {
            Fx fx = new Fx();
            fx.style = style;
            fx.seed = 1234 + style;
            // The hit went toward +x; the ground is 1.2 blocks under the hit point.
            fx.tx = 1.0;
            fx.tz = 0.0;
            fx.c = -1.2f;
            for (int i = 0; i < 40; i++) {
                for (float t = 0f; t <= 3f; t += 0.05f) {
                    HitEffects.at(fx, i, t, p);
                    assertTrue(p[1] >= fx.c - 1e-4f, "style " + style + " particle " + i + " under the ground at " + t);
                }
                HitEffects.at(fx, i, 0.25f, p);
                assertTrue(p[0] < 0f, "style " + style + " particle " + i + " comes off the body toward you");
                if (style == 2 || style == 4) {
                    // Hearts and orbs drift up as they go.
                    HitEffects.at(fx, i, 0.4f, p);
                    HitEffects.at(fx, i, 1.2f, q);
                    assertTrue(q[1] > p[1], "style " + style + " particle " + i + " floats up");
                    continue;
                }
                // At rest once landed: the same place later.
                HitEffects.at(fx, i, 8f, p);
                HitEffects.at(fx, i, 9f, q);
                assertEquals(fx.c, p[1], 1e-4f, "style " + style + " particle " + i + " rests on the ground");
                assertEquals(p[0], q[0], 1e-3f);
                assertEquals(p[2], q[2], 1e-3f);
            }
        }
    }
}
