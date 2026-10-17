package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamerTest {
    @Test
    void yourNameBecomesYou() {
        assertEquals("<You> hi", Streamer.replace("<Georg> hi", "Georg", "You"));
        // Any case, every time it appears.
        assertEquals("You was slain by Zombie; gg You", Streamer.replace("georg was slain by Zombie; gg GEORG", "Georg", "You"));
        // Text without your name comes back as the same string.
        String other = "Steve joined the game";
        assertSame(other, Streamer.replace(other, "Georg", "You"));
    }
}
