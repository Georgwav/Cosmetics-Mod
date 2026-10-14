package io.github.georgwav.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElytraLookTest {
    private static final float STANDING = 0.2617994f; // the game's: 15 degrees out from the back
    private static final float CROUCHING = 0.6981317f; // 40 degrees

    @Test
    void theElytraIsTuckedInButNeverIntoTheBody() {
        assertEquals((float) Math.toRadians(5), ElytraLook.tuck(STANDING, false), 1e-5f);
        // Crouching leans the body forward: the elytra comes in towards it, not past it.
        assertEquals(CROUCHING - ElytraLook.TUCK, ElytraLook.tuck(CROUCHING, true), 1e-5f);
        assertEquals(ElytraLook.CROUCH_LEAN, ElytraLook.tuck(ElytraLook.CROUCH_LEAN + 0.05f, true), 1e-5f);
        // Nearly against the back: brought against it, no further; already there (or folded forward): as it is.
        assertEquals(0f, ElytraLook.tuck(0.05f, false));
        assertEquals(0f, ElytraLook.tuck(0f, false));
        assertEquals(-0.3f, ElytraLook.tuck(-0.3f, false));
    }
}
