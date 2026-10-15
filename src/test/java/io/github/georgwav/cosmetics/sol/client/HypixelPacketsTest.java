package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HypixelPacketsTest {
    @Test
    void registerAsksForTheLocationEvent() {
        byte[] b = HypixelPackets.registerLocation();
        // version 1, one event, "hypixel:location" (16 bytes), version 1.
        assertEquals(1, b[0]);
        assertEquals(1, b[1]);
        assertEquals(16, b[2]);
        assertEquals("hypixel:location", new String(b, 3, 16, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, b[19]);
        assertEquals(20, b.length);
    }

    @Test
    void aGameRoundTrips() {
        var game = new HypixelPackets.Location("mini123A", Optional.of("BEDWARS"), Optional.empty(),
                Optional.of("BEDWARS_EIGHT_ONE"), Optional.of("Lighthouse"));
        var read = HypixelPackets.location(HypixelPackets.writeLocation(game));
        assertEquals(game, read);
        assertTrue(read.inGame());
        assertEquals(Optional.of("bedwars_eight_one"), read.playId());
    }

    @Test
    void aLobbyIsNoGame() {
        var lobby = new HypixelPackets.Location("lobby7", Optional.of("BEDWARS"), Optional.of("bedwarslobby3"),
                Optional.empty(), Optional.empty());
        var read = HypixelPackets.location(HypixelPackets.writeLocation(lobby));
        assertEquals(lobby, read);
        assertFalse(read.inGame());
        assertEquals(Optional.empty(), read.playId());
    }

    @Test
    void brokenOrNewerPacketsAreIgnored() {
        assertNull(HypixelPackets.location(new byte[0]));
        assertNull(HypixelPackets.location(new byte[]{1, 5, 'a'}), "a string longer than the packet");
        assertNull(HypixelPackets.location(new byte[]{2, 1, 'a', 0, 0, 0, 0}), "a version this doesn't read");
        assertNull(HypixelPackets.location(new byte[]{1, 1, 'a', 7}), "a broken optional");
    }

    @Test
    void longStringsUseMultiByteLengths() {
        String name = "x".repeat(300);
        var l = new HypixelPackets.Location(name, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(name, HypixelPackets.location(HypixelPackets.writeLocation(l)).serverName());
    }
}
