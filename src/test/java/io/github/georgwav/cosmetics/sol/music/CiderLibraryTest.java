package io.github.georgwav.cosmetics.sol.music;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CiderLibraryTest {
    private static String media(String type, String id, String name, String artist) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"href\":\"/v1/catalog/us/" + type + "/" + id + "\",\"attributes\":{\"name\":\""
                + name + "\",\"artistName\":\"" + artist + "\",\"artwork\":{\"url\":\"https://x/{w}x{h}bb.jpg\"},\"playParams\":{\"id\":\""
                + id + "\",\"kind\":\"" + type.replaceAll("s$", "") + "\"}}}";
    }

    @Test
    void queueStartsAfterTheSongPlaying() {
        String json = "[" + media("songs", "1", "A", "x") + "," + media("songs", "2", "B", "y") + "," + media("songs", "3", "C", "z") + "]";
        List<CiderLibrary.Item> up = CiderLibrary.queue(json, "2", 10);
        assertEquals(List.of("C"), up.stream().map(CiderLibrary.Item::title).toList());
        // Not listed: all of it is up next.
        assertEquals(3, CiderLibrary.queue(json, "9", 10).size());
        assertEquals(2, CiderLibrary.queue(json, null, 2).size());
        assertTrue(CiderLibrary.queue("oops", "1", 5).isEmpty());
    }

    @Test
    void itemsCarryWhatThePageShowsAndPlays() {
        CiderLibrary.Item i = CiderLibrary.item(com.google.gson.JsonParser.parseString(media("albums", "77", "Album", "Band")));
        assertNotNull(i);
        assertEquals("albums", i.kind());
        assertEquals("Band", i.subtitle());
        assertEquals("https://x/96x96bb.jpg", i.artworkUrl());
        assertEquals("/v1/catalog/us/albums/77", i.href());
        assertNull(CiderLibrary.item(com.google.gson.JsonParser.parseString("{\"id\":\"1\",\"attributes\":{}}")));
    }

    @Test
    void appleRepliesAndSearchResults() {
        String data = "{\"data\":{\"data\":[" + media("library-playlists", "p.1", "Mix", "") + "]}}";
        assertEquals("Mix", CiderLibrary.data(data, 5).get(0).title());
        String search = "{\"data\":{\"results\":{\"albums\":{\"data\":[" + media("albums", "a", "Al", "x") + "]},\"songs\":{\"data\":["
                + media("songs", "s", "So", "y") + "]}}}}";
        assertEquals(List.of("So", "Al"), CiderLibrary.search(search, 5).stream().map(CiderLibrary.Item::title).toList());
        assertTrue(CiderLibrary.search("{}", 5).isEmpty());
    }
}
