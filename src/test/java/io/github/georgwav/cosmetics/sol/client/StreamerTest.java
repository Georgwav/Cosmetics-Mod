package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamerTest {
    @Test
    void yourNameBecomesYou() {
        assertEquals("<You> hi", Streamer.replace("<Georg> hi", "Georg", "You"));
        // Any case, every time it appears.
        assertEquals("You was slain by Zombie; gg You", Streamer.replace("georg was slain by Zombie; gg GEORG", "Georg", "You"));
        // Whole names only: not inside another word or a longer name.
        assertEquals("Crash! You crashed", Streamer.replace("Crash! Ash crashed", "Ash", "You"));
        assertEquals("<Ash_2> hi You", Streamer.replace("<Ash_2> hi ash", "Ash", "You"));
        assertEquals("You,You", Streamer.replace("Ash,ASH", "Ash", "You"));
        // Text without your name comes back as the same string.
        String other = "Steve joined the game";
        assertSame(other, Streamer.replace(other, "Georg", "You"));
    }
}
