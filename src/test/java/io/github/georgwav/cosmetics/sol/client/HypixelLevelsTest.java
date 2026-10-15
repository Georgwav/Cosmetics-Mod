package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HypixelLevelsTest {
    @Test
    void networkLevels() {
        assertEquals(1, HypixelLevels.level(0));
        assertEquals(1, HypixelLevels.level(9_999));
        assertEquals(2, HypixelLevels.level(10_000));
        assertEquals(3, HypixelLevels.level(22_500));
        assertEquals(1, HypixelLevels.level(-5), "never below level 1");
        // Level 100 starts at 1250 * 102.5^2 - 15312.5 experience.
        assertEquals(100, HypixelLevels.level(13_117_500));
        assertEquals(99, HypixelLevels.level(13_117_499));
    }

    @Test
    void onlyRealAccountsAreAsked() {
        assertEquals(-1, HypixelLevels.of(java.util.UUID.fromString("00000000-0000-2000-8000-000000000000"), "key"),
                "an NPC's id");
        assertEquals(-1, HypixelLevels.of(java.util.UUID.randomUUID(), ""), "no key");
    }
}
