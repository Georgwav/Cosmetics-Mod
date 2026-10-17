package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerFoldersTest {
    @Test
    void folderIsTheNameBeforeASlash() {
        assertEquals("PvP", ServerFolders.of("PvP/Hypixel"));
        assertEquals("Friends", ServerFolders.of(" Friends /Sam's SMP"));
        assertNull(ServerFolders.of("Hypixel"));
        assertNull(ServerFolders.of("/Hypixel"));
        assertNull(ServerFolders.of("PvP/"));
        assertNull(ServerFolders.of(null));
    }

    @Test
    void foldersOnceEachInOrder() {
        List<String> names = List.of("pvp/Arena", "Survival/Home", "Lobby", "PvP/Hypixel", "survival/Old");
        assertEquals(List.of("pvp", "Survival"), ServerFolders.all(names));
        assertArrayEquals(new int[]{0, 3}, ServerFolders.in(names, "PvP"));
        assertArrayEquals(new int[]{}, ServerFolders.in(names, "Creative"));
    }

    @Test
    void movesStayInTheFolder() {
        int[] shown = {0, 3, 4};
        assertArrayEquals(new int[]{3, 0}, ServerFolders.pair(shown, 1, 0));
        assertArrayEquals(new int[]{3, 4}, ServerFolders.pair(shown, 1, 2));
        // Past the folder's last server: no move.
        assertNull(ServerFolders.pair(shown, 2, 3));
        assertNull(ServerFolders.pair(shown, -1, 0));
        // All servers shown: the places are the file's.
        assertArrayEquals(new int[]{5, 6}, ServerFolders.pair(null, 5, 6));
    }
}
