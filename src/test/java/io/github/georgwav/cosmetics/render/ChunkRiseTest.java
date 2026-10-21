package io.github.georgwav.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkRiseTest {
    @Test
    void risesFromEightBelowAndSlowsIntoPlace() {
        assertEquals(-8f, ChunkRise.drop(0f), 1e-6f);
        assertEquals(0f, ChunkRise.drop(1f), 1e-6f);
        assertEquals(0f, ChunkRise.drop(2f), 1e-6f);
        assertEquals(-8f, ChunkRise.drop(-1f), 1e-6f);
        float last = -9f, lastStep = Float.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            float d = ChunkRise.drop(i / 20f);
            assertTrue(d >= last);
            if (i > 0) {
                // Each step smaller than the one before: it eases in.
                assertTrue(d - last <= lastStep + 1e-6f);
                lastStep = d - last;
            }
            last = d;
        }
    }
}
