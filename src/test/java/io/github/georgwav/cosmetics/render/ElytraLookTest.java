package io.github.georgwav.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraLookTest {
    // The game's poses: out from the back (xRot) and spread sideways (zRot).
    private static final float STANDING_X = 0.2617994f; // 15 degrees
    private static final float STANDING_Z = -0.2617994f;
    private static final float CROUCHING_X = 0.6981317f; // 40 degrees
    private static final float CROUCHING_Z = (float) (-Math.PI / 4);
    private static final float GLIDING_X = 0.34906584f; // 20 degrees
    private static final float GLIDING_Z = (float) (-Math.PI / 2);
    /** How far crouching leans the body forward (the game's). */
    private static final float CROUCH_LEAN = 0.5f;

    @Test
    void theElytraIsTuckedInButNeverIntoTheBody() {
        assertEquals((float) Math.toRadians(5), ElytraLook.tuck(STANDING_X, STANDING_Z), 1e-5f);
        // Crouching leans the body forward: the elytra comes in towards it, not past it.
        float crouching = ElytraLook.tuck(CROUCHING_X, CROUCHING_Z);
        assertEquals(CROUCHING_X - ElytraLook.TUCK, crouching, 1e-5f);
        assertTrue(crouching > CROUCH_LEAN);
        // Nearly against the back: brought against it, no further; already there (or folded forward): as it is.
        assertEquals(0f, ElytraLook.tuck(0.05f, STANDING_Z));
        assertEquals(0f, ElytraLook.tuck(0f, STANDING_Z));
        assertEquals(-0.3f, ElytraLook.tuck(-0.3f, STANDING_Z));
    }

    @Test
    void theTuckEasesOutAsTheWingsSpreadToGlide() {
        assertEquals(GLIDING_X, ElytraLook.tuck(GLIDING_X, GLIDING_Z));
        // Half way from crouched to fully spread: half the tuck.
        float half = (CROUCHING_Z + GLIDING_Z) / 2;
        assertEquals(GLIDING_X - ElytraLook.TUCK / 2, ElytraLook.tuck(GLIDING_X, half), 1e-5f);
        // The game eases the wings between poses; the tucked angle follows without jumping.
        float last = ElytraLook.tuck(STANDING_X, STANDING_Z);
        for (int step = 1; step <= 100; step++) {
            float t = step / 100f;
            float x = STANDING_X + (GLIDING_X - STANDING_X) * t;
            float z = STANDING_Z + (GLIDING_Z - STANDING_Z) * t;
            float now = ElytraLook.tuck(x, z);
            assertTrue(Math.abs(now - last) < 0.01f, "jumped at step " + step);
            last = now;
        }
    }
}
