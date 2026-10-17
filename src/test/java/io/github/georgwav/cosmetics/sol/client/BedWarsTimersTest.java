package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BedWarsTimersTest {
    @Test
    void plainDropsColourCodes() {
        assertEquals("Diamond II in 4:59", BedWarsTimers.plain("§fDiamond II in §a4:59§1"));
        assertEquals("", BedWarsTimers.plain("§"));
    }

    @Test
    void bedWarsTitle() {
        assertTrue(BedWarsTimers.isBedWars("§e§lBED WARS"));
        assertTrue(BedWarsTimers.isBedWars("Bed Wars"));
        assertFalse(BedWarsTimers.isBedWars("§e§lSKYWARS"));
    }

    @Test
    void sidebarEvent() {
        // Hypixel's lines: a team-prefixed text after an invisible colour-code name.
        List<String> lines = List.of("§71/2/25 §8m12A§1", "", "§fDiamond II in §a4:59§2", "",
                "§cR §fRed: §a✔§3");
        assertEquals("Diamond II in 4:59", BedWarsTimers.event(lines));
        assertEquals("Bed gone in 10:00", BedWarsTimers.event(List.of("Bed gone in 10:00")));
        assertNull(BedWarsTimers.event(List.of("Red: ✔", "www.hypixel.net")));
    }

    @Test
    void countdownSeconds() {
        assertEquals(12, BedWarsTimers.seconds("§eSpawns in §c12 §eseconds"));
        assertEquals(1, BedWarsTimers.seconds("Spawns in 1 second"));
        assertEquals(-1, BedWarsTimers.seconds("Tier II"));
    }

    @Test
    void nearestGeneratorOfEachKind() {
        // Two diamond generators (stacks of Tier / kind / countdown) and one emerald one.
        List<BedWarsTimers.Label> labels = List.of(
                new BedWarsTimers.Label(10, 72.5, 0, "Tier II"), new BedWarsTimers.Label(10, 72.2, 0, "§b§lDiamond"),
                new BedWarsTimers.Label(10, 71.9, 0, "Spawns in 23 seconds"),
                new BedWarsTimers.Label(60, 72.2, 0, "Diamond"), new BedWarsTimers.Label(60, 71.9, 0, "Spawns in 5 seconds"),
                new BedWarsTimers.Label(0, 80.2, 30, "§2§lEmerald"), new BedWarsTimers.Label(0, 79.9, 30, "Spawns in 41 seconds"));
        assertArrayEquals(new int[]{23, 41}, BedWarsTimers.nearest(labels, 0, 0));
        assertArrayEquals(new int[]{5, 41}, BedWarsTimers.nearest(labels, 55, 0));
        assertArrayEquals(new int[]{-1, -1}, BedWarsTimers.nearest(List.of(new BedWarsTimers.Label(0, 0, 0, "Spawns in 3 seconds")), 0, 0),
                "a countdown with no kind above it is no generator");
    }
}
