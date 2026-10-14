package io.github.georgwav.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wavy capes: the lower part of a cape lags behind the top's swing, then catches up. */
class WavyCapesTest {
    private static final int LAST = WavyCapes.POINTS - 1;

    @Test
    void theBottomLagsWhenTheTopSwingsThenSettles() {
        WavyCapes.Chain chain = new WavyCapes.Chain();
        double t = 0;
        WavyCapes.step(chain, 0.1f, t);
        // Steady: hanging with the top, no lag.
        for (int i = 0; i < 20; i++) {
            t += 1 / 60.0;
            WavyCapes.step(chain, 0.1f, t);
        }
        assertEquals(0f, chain.lag[LAST], 1e-4f);
        // You start running: the top swings back 50 degrees; just after, the bottom hasn't followed yet.
        float top = 0.1f + (float) Math.toRadians(50);
        t += 1 / 60.0;
        float[] lag = WavyCapes.step(chain, top, t);
        assertTrue(lag[LAST] < -0.5f, "the bottom is still behind: " + lag[LAST]);
        assertTrue(lag[LAST] >= -WavyCapes.MAX_LAG);
        assertEquals(0f, lag[0], 1e-6f, "the top never lags");
        // A few seconds on, it has caught up.
        for (int i = 0; i < 300; i++) {
            t += 1 / 60.0;
            WavyCapes.step(chain, top, t);
        }
        assertEquals(0f, chain.lag[LAST], 0.02f);
    }

    @Test
    void aLongPauseDoesNotFling() {
        WavyCapes.Chain chain = new WavyCapes.Chain();
        WavyCapes.step(chain, 0f, 0.0);
        float[] lag = WavyCapes.step(chain, 1f, 30.0);
        for (float l : lag) {
            assertTrue(Math.abs(l) <= WavyCapes.MAX_LAG + 1e-6f);
            assertTrue(Float.isFinite(l));
        }
    }
}
