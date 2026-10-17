package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeedsTest {
    @Test
    void typedSeeds() {
        assertEquals(-4530634556500121041L, Seeds.typed(" -4530634556500121041 "));
        assertEquals(42L, Seeds.typed("42"));
        // Words become a seed as the game makes one: their string hash.
        assertEquals((long) "glacier".hashCode(), Seeds.typed("glacier"));
        assertNull(Seeds.typed("  "));
        assertNull(Seeds.typed(null));
    }

    @Test
    void seedFromTheGamesAnswer() {
        assertEquals(1234L, Seeds.fromReply("[1234]"));
        assertEquals(-987654321L, Seeds.fromReply("[-987654321]"));
        assertNull(Seeds.fromReply("[]"));
        assertNull(Seeds.fromReply(null));
    }
}
