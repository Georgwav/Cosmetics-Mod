package io.github.georgwav.cosmetics.sol.fx.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFxTest {
    @Test
    void theRainbowCentresOppositeTheSunWithUpSquareToIt() {
        float[] b = new float[4];
        for (int i = -89; i <= 89; i++) {
            double a = Math.toRadians(i);
            WorldFx.antiSun(a, b);
            // The sun is at (-sin a, cos a): the centre is on the side away from it...
            assertTrue(b[0] * -Math.sin(a) <= 1e-6);
            // ...straight opposite while the sun is low, and never deeper than LOWEST under the horizon.
            double sunUp = Math.asin(Math.cos(a));
            if (sunUp <= -WorldFx.LOWEST) {
                assertEquals(Math.sin(a), b[0], 1e-5);
                assertEquals(-Math.cos(a), b[1], 1e-5);
            } else {
                assertEquals(Math.sin(WorldFx.LOWEST), b[1], 1e-5);
            }
            assertEquals(1.0, Math.hypot(b[0], b[1]), 1e-5);
            // "Up" is a unit vector square to it, pointing up the sky.
            assertEquals(0.0, b[0] * b[2] + b[1] * b[3], 1e-5);
            assertEquals(1.0, Math.hypot(b[2], b[3]), 1e-5);
            assertTrue(b[3] > 0f);
        }
    }
}
