package io.github.georgwav.cosmetics.sol.fx;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The effects' foundation (C.3): the kill window, the jump and landing edges, the ramp, the sprites and the pool. */
class FxFoundationTest {
    @Test
    void aKillCountsOnlyInsideTheThreeSecondWindow() {
        FxEvents.KillWindow w = new FxEvents.KillWindow(3000L);
        int[] out = new int[16];
        w.hit(7, 1000L, 20f);
        w.hit(8, 1000L, 20f);
        // Nothing dead yet: both kept.
        assertEquals(0, w.poll(2000L, id -> false, out));
        assertEquals(2, w.size());
        // 7 dies 2.9 s after the hit: a kill.
        assertEquals(1, w.poll(3900L, id -> id == 7, out));
        assertEquals(7, out[0]);
        // 8 is forgotten once the window passes, so its death later is no kill of yours.
        assertEquals(0, w.poll(4100L, id -> false, out));
        assertEquals(0, w.size());
        w.hit(9, 5000L, 20f);
        assertEquals(0, w.poll(8100L, id -> id == 9, out), "dead 3.1 s after the hit: not a kill");
        // A new hit starts the window again.
        w.hit(10, 9000L, 20f);
        w.hit(10, 11500L, 18f);
        assertEquals(1, w.poll(14000L, id -> id == 10, out));
    }

    @Test
    void theWindowKeepsTheMostRecentWhenFull() {
        FxEvents.KillWindow w = new FxEvents.KillWindow(3000L);
        for (int i = 0; i < FxEvents.KillWindow.SIZE + 4; i++) {
            w.hit(i, 1000L + i, 20f);
        }
        assertEquals(FxEvents.KillWindow.SIZE, w.size());
        int[] out = new int[16];
        assertEquals(1, w.poll(1500L, id -> id == FxEvents.KillWindow.SIZE + 3, out));
        assertEquals(0, w.poll(1500L, id -> id == 0, out), "the oldest made room");
    }

    @Test
    void jumpAndLandingEdges() {
        FxEvents.JumpLand j = new FxEvents.JumpLand();
        assertEquals(FxEvents.JumpLand.NONE, j.step(true, 64.0, 0.0, false));
        assertEquals(FxEvents.JumpLand.NONE, j.step(true, 64.0, 0.0, false));
        // Leaving the ground going up is a jump.
        assertEquals(FxEvents.JumpLand.JUMP, j.step(false, 64.42, 0.42, false));
        assertEquals(FxEvents.JumpLand.NONE, j.step(false, 65.2, 0.2, false));
        assertEquals(FxEvents.JumpLand.NONE, j.step(false, 64.6, -0.3, false));
        // Back on the ground: a landing, from the highest point, at the last tick's speed.
        assertEquals(FxEvents.JumpLand.LAND, j.step(true, 64.0, 0.0, false));
        assertEquals(1.2f, j.fallHeight, 1e-4f);
        assertEquals(0.3f, j.fallSpeed, 1e-4f);
        // Stepping off a slab (no jump, half a block) is no landing; a fall of nearly three blocks is.
        assertEquals(FxEvents.JumpLand.NONE, j.step(false, 63.9, -0.1, false));
        assertEquals(FxEvents.JumpLand.NONE, j.step(true, 63.5, 0.0, false));
        assertEquals(FxEvents.JumpLand.NONE, j.step(false, 63.3, -0.2, false));
        assertEquals(FxEvents.JumpLand.NONE, j.step(false, 61.5, -0.9, false));
        assertEquals(FxEvents.JumpLand.LAND, j.step(true, 60.5, 0.0, false));
        assertEquals(2.8f, j.fallHeight, 1e-4f);
        // Flying or swimming in between starts over: no landing counted from before.
        assertEquals(FxEvents.JumpLand.JUMP, j.step(false, 60.92, 0.42, false));
        assertEquals(FxEvents.JumpLand.NONE, j.step(false, 61.5, 0.1, true));
        assertEquals(FxEvents.JumpLand.NONE, j.step(true, 61.0, 0.0, false));
    }

    @Test
    void theRampHasFourTonesFromOneColour() {
        int[] r = Ramp.of(0xFF3080FF);
        // Core 60% toward white, shade 35% and deep 60% toward black.
        assertEquals(0xFFACCCFF, r[Ramp.CORE]);
        assertEquals(0xFF3080FF, r[Ramp.MAIN]);
        assertEquals(0xFF1F53A6, r[Ramp.SHADE]);
        assertEquals(0xFF133366, r[Ramp.DEEP]);
        // Stepped fades.
        assertEquals(0.25f, Ramp.steps(0.1f, 4, true));
        assertEquals(1f, Ramp.steps(0.8f, 4, true));
        assertEquals(0.8f, Ramp.steps(0.8f, 4, false));
        assertEquals(0f, Ramp.steps(0f, 4, true));
        // Positions on the texel grid.
        assertEquals(0.125f, Ramp.snap(0.13f, true));
        // The pop: 1.25 then 1.1 then 1, in three steps.
        assertEquals(1.25f, Ramp.pop(0.01f, 0.15f, true));
        assertEquals(1.1f, Ramp.pop(0.07f, 0.15f, true));
        assertEquals(1f, Ramp.pop(0.2f, 0.15f, true));
    }

    @Test
    void spritesPackWithEveryToneTheyUse() {
        FxTex atlas = FxSprites.pack();
        assertEquals(128, atlas.width);
        for (FxSprites.Sprite s : FxSprites.all()) {
            assertNotNull(s.uv[FxSprites.ALL], s.name + " has pixels");
            for (int t = 0; t < 4; t++) {
                boolean used = false;
                for (int y = 0; y < s.h; y++) {
                    for (int x = 0; x < s.w; x++) {
                        used |= s.tone(x, y) == t;
                    }
                }
                assertEquals(used, s.uses(t), s.name + " tone " + t);
            }
        }
        // The flame has three frames, the confetti two; the plus is the logo's sparkle, five texels across.
        assertEquals(3, FxSprites.FLAME.length);
        assertEquals(2, FxSprites.CONFETTI.length);
        assertEquals(5, FxSprites.PLUS.w);
        // The dither disc is solid in the middle and sparse at the edge.
        assertEquals(Ramp.MAIN, FxSprites.DITHER.tone(8, 8));
        assertTrue(FxSprites.DITHER.tone(0, 0) < 0);
    }

    @Test
    void thePoolCapsAKindAndEndsTheOldestEarly() {
        FxPool pool = new FxPool(32, 24);
        FxKind kind = new FxKind("test", 4) {
            @Override
            public void draw(Fx fx, float age, float now, FxOut out) {
            }
        };
        Set<Fx> made = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            pool.setNow(i * 0.1f);
            made.add(pool.spawn(kind, 0, 0, 0, 2f, 2));
        }
        assertEquals(4, pool.count(kind));
        pool.setNow(0.5f);
        Fx fifth = pool.spawn(kind, 0, 0, 0, 2f, 2);
        assertFalse(made.contains(fifth));
        // The first one now ends within a quarter second; the rest play on.
        int ending = 0;
        for (Fx f : made) {
            if (f.end <= 0.5f + 0.25f + 1e-4f) {
                ending++;
            }
        }
        assertEquals(1, ending);
        // At low quality the cap halves.
        FxPool low = new FxPool(32, 24);
        for (int i = 0; i < 3; i++) {
            low.setNow(i * 0.1f);
            low.spawn(kind, 0, 0, 0, 2f, 0);
        }
        assertEquals(3, low.count(kind));
        // Finished effects are retired on the next draw.
        pool.setNow(10f);
        pool.draw(new NullOut(), () -> { }, () -> { }, () -> { });
        assertEquals(0, pool.count(kind));
    }

    /** An output that draws nothing. */
    private static final class NullOut implements FxOut {
        @Override
        public void use(FxTex tex) {
        }

        @Override
        public void origin(double x, double y, double z) {
        }

        @Override
        public void eye(float[] out) {
        }

        @Override
        public void vertex(float x, float y, float z, float u, float v, int argb) {
        }

        @Override
        public boolean glowPass() {
            return false;
        }

        @Override
        public void right(float[] out) {
            out[0] = 1f;
        }

        @Override
        public void up(float[] out) {
            out[1] = 1f;
        }

        @Override
        public double camX() {
            return 0;
        }

        @Override
        public double camY() {
            return 0;
        }

        @Override
        public double camZ() {
            return 0;
        }

        @Override
        public float ground(double x, double z, double fromY) {
            return 0f;
        }
    }
}
