package io.github.georgwav.cosmetics.sol.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpringTest {
    @AfterEach
    void reset() {
        Spring.reducedMotion = false;
    }

    private static float run(Spring.Preset p, float fps, float seconds) {
        Spring s = new Spring(p, 0f);
        s.setTarget(1f);
        int steps = Math.round(seconds * fps);
        float dt = seconds / steps;
        for (int i = 0; i < steps; i++) {
            s.step(dt);
        }
        return s.value();
    }

    @Test
    void everyPresetOvershootsThenSettles() {
        for (Spring.Preset p : new Spring.Preset[]{Spring.SUN_ARC, Spring.QUICK_SUN, Spring.ECLIPSE, Spring.RAY, Spring.UI, Spring.POP}) {
            Spring s = new Spring(p, 0f);
            s.setTarget(1f);
            float max = 0f;
            for (int i = 0; i < 600; i++) {
                max = Math.max(max, s.step(1f / 120f));
            }
            assertTrue(max > 1f, "overshoots: " + p);
            assertEquals(1f, s.value(), 1e-3, "settles: " + p);
            assertTrue(s.settled());
        }
    }

    @Test
    void frameRateIndependent() {
        for (float seconds : new float[]{0.1f, 0.25f, 0.5f}) {
            float a = run(Spring.SUN_ARC, 30f, seconds);
            float b = run(Spring.SUN_ARC, 60f, seconds);
            float c = run(Spring.SUN_ARC, 144f, seconds);
            assertEquals(b, a, 0.02f + Math.abs(b) * 0.02f);
            assertEquals(b, c, 0.02f + Math.abs(b) * 0.02f);
        }
    }

    @Test
    void reducedMotionSnaps() {
        Spring.reducedMotion = true;
        Spring s = new Spring(Spring.UI, 0f);
        s.setTarget(5f);
        assertEquals(5f, s.step(1f / 60f));
    }

    @Test
    void anglesGoTheShortWay() {
        Spring s = new Spring(Spring.QUICK_SUN, 170f);
        s.setTarget(-170f);
        s.stepAngle(1f / 60f);
        assertTrue(s.target() > 180f, "target unwrapped to 190");
        assertEquals(-180f, Spring.normDegrees(180f), 1e-4);
    }
}
